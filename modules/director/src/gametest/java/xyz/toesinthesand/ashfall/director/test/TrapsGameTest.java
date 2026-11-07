package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.traps.Compass8;
import xyz.toesinthesand.ashfall.director.traps.RattleBoxBlock;
import xyz.toesinthesand.ashfall.director.traps.RattleBoxBlockEntity;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;
import xyz.toesinthesand.ashfall.director.traps.TriplineAlarmBlock;
import xyz.toesinthesand.ashfall.director.traps.TriplineAlarmBlockEntity;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Traps and lures on a real server: the Rattle Box draws a zombie, the Tripline Alarm trips on zombies only, and the
 * Spike Strip hurts zombies, spares players, wears out and doesn't stop zombies pathing across it. The Rattle Box
 * test gets wide padding because its noise reaches 32 blocks (gametests share one level).
 */
public class TrapsGameTest {
	private static final int PADDING = 24;
	private static final int LURE_PADDING = 40;

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(maxTicks = 400, padding = LURE_PADDING)
	public void zombieWalksToWoundRattleBox(GameTestHelper helper) {
		floor(helper, 8);
		BlockPos boxPos = new BlockPos(6, 1, 6);
		helper.setBlock(boxPos, DirectorBlocks.RATTLE_BOX);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(boxPos);
		RattleBoxBlockEntity box = (RattleBoxBlockEntity) level.getBlockEntity(abs);
		helper.assertTrue(box != null, "the Rattle Box has a block entity");
		helper.assertTrue(box.wind(level), "a fresh Rattle Box winds");
		helper.assertFalse(box.wind(level), "a running Rattle Box can't be wound again");
		helper.assertTrue(level.getBlockState(abs).getValue(RattleBoxBlock.WOUND), "winding sets wound=true");
		Vec3 center = Vec3.atCenterOf(abs);
		List<Stimulus> heard = Stimuli.query(level, center, 1.0);
		helper.assertTrue(heard.stream().anyMatch(s -> s.type() == StimulusType.SOUND && s.radius() == RattleBoxBlockEntity.RADIUS
				&& s.distanceSqr(center.x, center.y, center.z) < 1.0), "a wound box leaves a radius-32 SOUND, got " + heard);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5));
		// the box itself is in the way, so "reached" is standing next to it
		helper.succeedWhen(() -> helper.assertTrue(zombie.distanceToSqr(center.x, zombie.getY(), center.z) < 2.0 * 2.0,
				"zombie at " + zombie.position() + " has not reached the Rattle Box at " + center));
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void rattleBoxRefreshesOneStimulus(GameTestHelper helper) {
		BlockPos boxPos = new BlockPos(2, 1, 2);
		helper.setBlock(1, 0, 1, Blocks.STONE);
		helper.setBlock(boxPos, DirectorBlocks.RATTLE_BOX);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(boxPos);
		((RattleBoxBlockEntity) level.getBlockEntity(abs)).wind(level);
		Vec3 center = Vec3.atCenterOf(abs);
		// past two re-emits (every 40 ticks): still exactly one stimulus for the box
		helper.runAfterDelay(RattleBoxBlockEntity.EMIT_INTERVAL * 2 + 5, () -> {
			long count = Stimuli.query(level, center, 1.0).stream()
					.filter(s -> s.radius() == RattleBoxBlockEntity.RADIUS && s.distanceSqr(center.x, center.y, center.z) < 1.0).count();
			helper.assertTrue(count == 1, "a running Rattle Box should stand for one stimulus, found " + count);
			helper.succeed();
		});
	}

	@GameTest(padding = PADDING)
	public void triplineAlarmTripsOnZombieNotPlayer(GameTestHelper helper) {
		floor(helper, 8);
		BlockPos wall = new BlockPos(3, 1, 0);
		BlockPos alarmPos = new BlockPos(3, 1, 1);
		helper.setBlock(wall, Blocks.STONE);
		helper.setBlock(alarmPos, DirectorBlocks.TRIPLINE_ALARM.defaultBlockState().setValue(TriplineAlarmBlock.FACING, Direction.SOUTH));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(alarmPos);
		TriplineAlarmBlockEntity alarm = (TriplineAlarmBlockEntity) level.getBlockEntity(abs);
		helper.assertTrue(alarm != null, "the alarm has a block entity");
		AABB watch = TriplineAlarmBlockEntity.watchBox(abs, Direction.SOUTH);
		helper.assertTrue(watch.minZ == abs.getZ() + 1 && watch.maxZ == abs.getZ() + 6 && watch.getXsize() == 5 && watch.getYsize() == 3,
				"the watched box is 5x3x5 in front of the alarm, got " + watch);
		Vec3 inFront = helper.absoluteVec(new Vec3(3.5, 1, 4.5));

		ServerPlayer player = mockPlayer(helper, "tripline-mock");
		try {
			player.teleportTo(inFront.x, inFront.y, inFront.z);
			helper.assertFalse(alarm.scan(level), "a player must not trip the alarm");
			helper.assertFalse(level.getBlockState(abs).getValue(TriplineAlarmBlock.POWERED), "no signal for a player");
			helper.assertTrue(alarm.listeners(level).contains(player), "a player within 96 blocks hears the alarm");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}

		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 1, 4.5));
		helper.assertTrue(alarm.scan(level), "a zombie in front trips the alarm");
		helper.assertTrue(level.getBlockState(abs).getValue(TriplineAlarmBlock.POWERED), "a tripped alarm is powered");
		helper.assertTrue(level.hasNeighborSignal(abs.relative(Direction.NORTH)),
				"the wall behind a tripped alarm is powered");
		helper.assertTrue(alarm.cooldown() > 0, "tripping starts the cooldown");
		zombie.discard();
		helper.assertFalse(alarm.scan(level), "the alarm clears once the zombie is gone");
		helper.assertFalse(level.getBlockState(abs).getValue(TriplineAlarmBlock.POWERED), "no signal once clear");
		helper.succeed();
	}

	@GameTest
	public void compassNames(GameTestHelper helper) {
		helper.assertTrue(Compass8.name(0, -10).equals("north"), "-Z is north");
		helper.assertTrue(Compass8.name(10, 0).equals("east"), "+X is east");
		helper.assertTrue(Compass8.name(10, -10).equals("north_east"), "+X -Z is north-east");
		helper.assertTrue(Compass8.name(-3, 9).equals("south"), "mostly +Z is south");
		helper.assertTrue(Compass8.name(-7, 7).equals("south_west"), "-X +Z is south-west");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void spikeStripHurtsZombieOncePerSecond(GameTestHelper helper) {
		floor(helper, 4);
		BlockPos stripPos = new BlockPos(1, 1, 1);
		helper.setBlock(stripPos, DirectorBlocks.SPIKE_STRIP);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(stripPos);
		BlockState state = level.getBlockState(abs);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5));
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		float before = zombie.getHealth();
		helper.assertTrue(SpikeStripBlock.stepOn(state, level, abs, zombie), "the strip hurts a zombie");
		helper.assertTrue(zombie.getHealth() < before, "zombie health dropped from " + before + " to " + zombie.getHealth());
		helper.assertTrue(zombie.getLastDamageSource() != null && zombie.getLastDamageSource().is(SpikeStripBlock.DAMAGE),
				"the damage type is ashfall:spikes");
		helper.assertTrue(stuck(zombie).x < 1.0, "the strip slows a zombie too");
		helper.assertFalse(SpikeStripBlock.stepOn(state, level, abs, zombie), "no second hit within a second");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void spikeStripSlowsPlayerWithoutHurting(GameTestHelper helper) {
		floor(helper, 4);
		BlockPos stripPos = new BlockPos(1, 1, 1);
		helper.setBlock(stripPos, DirectorBlocks.SPIKE_STRIP);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(stripPos);
		ServerPlayer player = mockPlayer(helper, "spikes-mock");
		try {
			player.getAbilities().invulnerable = false;
			player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
			float before = player.getHealth();
			helper.assertFalse(SpikeStripBlock.stepOn(level.getBlockState(abs), level, abs, player), "the strip must not hurt a player");
			helper.assertTrue(player.getHealth() == before, "player health changed from " + before + " to " + player.getHealth());
			Vec3 slow = stuck(player);
			helper.assertTrue(slow.x == SpikeStripBlock.SLOW.x && slow.z == SpikeStripBlock.SLOW.z, "the strip slows a player, got " + slow);
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void spikeStripWearsOutAndRepairs(GameTestHelper helper) {
		floor(helper, 4);
		BlockPos stripPos = new BlockPos(1, 1, 1);
		helper.setBlock(stripPos, DirectorBlocks.SPIKE_STRIP);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(stripPos);
		for (int i = 0; i < SpikeStripBlock.MAX_WEAR; i++) {
			SpikeStripBlock.wear(level, abs);
		}
		helper.assertTrue(level.getBlockState(abs).getValue(SpikeStripBlock.WEAR) == SpikeStripBlock.MAX_WEAR, "worn to the last stage");

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET, 3));
		helper.useBlock(stripPos, player);
		helper.assertTrue(level.getBlockState(abs).getValue(SpikeStripBlock.WEAR) == SpikeStripBlock.MAX_WEAR - 1,
				"an Iron Nugget takes one wear off, wear is " + level.getBlockState(abs).getValue(SpikeStripBlock.WEAR));
		helper.assertTrue(player.getMainHandItem().getCount() == 2, "the repair uses one nugget");

		SpikeStripBlock.wear(level, abs);
		SpikeStripBlock.wear(level, abs);
		helper.assertBlockNotPresent(DirectorBlocks.SPIKE_STRIP, stripPos);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(1.5));
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(SalvageItems.SCRAP_METAL)), "a worn-out strip drops Scrap Metal");
		helper.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(DirectorBlocks.SPIKE_STRIP_ITEM)), "a worn-out strip doesn't drop itself");
		drops.forEach(Entity::discard);
		helper.succeed();
	}

	/**
	 * A one-wide corridor fully covered by strips with a wound Rattle Box at the far end: the zombie has to path
	 * across the strips to follow the noise. A Rattle Box rather than a one-off sound, because a slowed zombie takes
	 * longer than one SOUND lifetime (5 s) to get there.
	 */
	@GameTest(maxTicks = 400, padding = LURE_PADDING)
	public void zombiePathsAcrossSpikeStrip(GameTestHelper helper) {
		// everything stays inside the default 8x8 test area; blocks set outside it don't stay
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, 0, Blocks.STONE);
				helper.setBlock(x, y, 2, Blocks.STONE);
			}
		}
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(0, y, 1, Blocks.STONE);
			helper.setBlock(7, y, 1, Blocks.STONE);
		}
		for (int x = 3; x <= 4; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), DirectorBlocks.SPIKE_STRIP);
		}
		BlockPos boxPos = new BlockPos(6, 1, 1);
		helper.setBlock(boxPos, DirectorBlocks.RATTLE_BOX);
		ServerLevel level = helper.getLevel();
		((RattleBoxBlockEntity) level.getBlockEntity(helper.absolutePos(boxPos))).wind(level);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5));
		float before = zombie.getHealth();
		// centre past the last strip (x = 4); the path ends at the node before the box, which navigation counts as
		// reached a little short of its centre
		double pastStrips = helper.absoluteVec(new Vec3(5.0, 1, 1.5)).x;
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getX() >= pastStrips, "zombie at " + zombie.position() + " has not crossed the strips (x >= " + pastStrips + ")");
			helper.assertTrue(zombie.getHealth() < before, "crossing the strips should have hurt the zombie");
		});
	}

	/** The protected {@code stuckSpeedMultiplier} a block set this tick (reset when the entity next moves). */
	private static Vec3 stuck(Entity entity) {
		try {
			Field field = Entity.class.getDeclaredField("stuckSpeedMultiplier");
			field.setAccessible(true);
			return (Vec3) field.get(entity);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A survival mock in the level's player list (see {@code HydrationGameTest}); remove it when done. */
	private static ServerPlayer mockPlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}
}
