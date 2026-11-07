package xyz.toesinthesand.ashfall.arms.test;

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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.CrowbarItem;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.spawn.BuildingKeys;
import xyz.toesinthesand.ashfall.director.spawn.BuildingPopulations;
import xyz.toesinthesand.ashfall.director.spawn.BuildingRecord;
import xyz.toesinthesand.ashfall.director.spawn.DirectorState;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.lock.KeyData;
import xyz.toesinthesand.ashfall.salvage.lock.Locks;
import xyz.toesinthesand.ashfall.salvage.lock.StrongboxBlock;
import xyz.toesinthesand.ashfall.salvage.lock.StrongboxBlockEntity;
import xyz.toesinthesand.ashfall.salvage.lock.Strongboxes;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Building keys and safes: a locked Strongbox refuses hands, pickaxes and hoppers; only its own building's key opens it
 * (quietly, and the key is used up); a Crowbar forces it after the hold with a 24-block noise; and a bank's first
 * population puts in a box and gives one zombie the key, which it drops on death. Padding for the crowbar's noise.
 */
public class StrongboxGameTest {
	private static final int PADDING = 30;
	private static final BlockPos BOX = new BlockPos(2, 1, 2);

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static long randomId() {
		return 7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
	}

	/** A locked box for {@code building} at {@link #BOX}, tier 3 loot. */
	private static BlockPos lockedBox(GameTestHelper helper, long building) {
		floor(helper, 5);
		BlockPos abs = helper.absolutePos(BOX);
		Strongboxes.place(helper.getLevel(), abs, Direction.NORTH, building, 3, 42L);
		return abs;
	}

	private static UseOnContext useOn(ServerPlayer player, BlockPos pos) {
		return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	private static boolean soundAt(ServerLevel level, BlockPos pos, double radius) {
		Vec3 c = Vec3.atCenterOf(pos);
		return Stimuli.query(level, c, 1.0).stream().anyMatch(s -> s.type() == StimulusType.SOUND
				&& (radius < 0 || s.radius() == radius) && s.distanceSqr(c.x, c.y, c.z) < 1.0);
	}

	@GameTest
	public void lockedBoxCantBeOpenedByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = lockedBox(helper, randomId());
		ServerPlayer player = survivalPlayer(helper, "strongbox-hand");
		try {
			player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
			BlockState state = level.getBlockState(abs);
			helper.assertTrue(state.getValue(StrongboxBlock.LOCKED), "a placed box is locked");
			state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "a locked box opens no menu");
			StrongboxBlockEntity box = (StrongboxBlockEntity) level.getBlockEntity(abs);
			helper.assertFalse(box.canOpen(player), "a locked box refuses the player");
			helper.assertTrue(state.getDestroyProgress(player, level, abs) == 0.0f, "a locked box can't be mined");
			helper.assertTrue(box.getSlotsForFace(Direction.DOWN).length == 0, "a locked box gives hoppers no slots");
			helper.assertTrue(box.getLootTable() != null && box.getLootTable().identifier().getPath().equals("strongbox/tier_3"),
					"the box carries its tier's table, got " + box.getLootTable());
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void matchingKeyOpensQuietlyAndIsUsedUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long building = randomId();
		BlockPos abs = lockedBox(helper, building);
		ServerPlayer player = survivalPlayer(helper, "strongbox-key");
		try {
			player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
			ItemStack wrong = Strongboxes.key(new KeyData(building + 1, "Elsewhere"));
			player.setItemInHand(InteractionHand.MAIN_HAND, wrong);
			wrong.useOn(useOn(player, abs));
			helper.assertTrue(Strongboxes.isLocked(level.getBlockState(abs)), "a wrong key leaves the box locked");
			helper.assertTrue(wrong.getCount() == 1, "a wrong key is not used up");

			ItemStack right = Strongboxes.key(new KeyData(building, "Bank at 0, 0"));
			player.setItemInHand(InteractionHand.MAIN_HAND, right);
			right.useOn(useOn(player, abs));
			helper.assertFalse(Strongboxes.isLocked(level.getBlockState(abs)), "the matching key unlocks the box");
			helper.assertTrue(right.isEmpty(), "the matching key is used up");
			helper.assertFalse(soundAt(level, abs, -1), "a key makes no noise zombies hear");
			helper.assertTrue(((StrongboxBlockEntity) level.getBlockEntity(abs)).canOpen(player), "an unlocked box opens");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void crowbarForcesTheBoxLoudly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = lockedBox(helper, randomId());
		ServerPlayer player = survivalPlayer(helper, "strongbox-crowbar");
		try {
			player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
			ItemStack crowbar = new ItemStack(ArmsItems.CROWBAR);
			player.setItemInHand(InteractionHand.MAIN_HAND, crowbar);
			crowbar.useOn(useOn(player, abs));
			helper.assertTrue(player.isUsingItem(), "using a crowbar on a locked box starts the hold");
			helper.assertTrue(crowbar.getUseDuration(player) == CrowbarItem.FORCE_TICKS && CrowbarItem.FORCE_TICKS == 80,
					"the hold is 4 s");
			helper.assertTrue(Strongboxes.isLocked(level.getBlockState(abs)), "starting the hold doesn't open it yet");
			// The end of the hold (finishUsingItem needs the player's look ray, which a mock player has no client for).
			helper.assertTrue(CrowbarItem.force(level, player, crowbar, abs), "the full hold forces the box");
			helper.assertFalse(Strongboxes.isLocked(level.getBlockState(abs)), "a forced box is unlocked");
			helper.assertTrue(CrowbarItem.FORCE_NOISE == 48.0f, "forcing is loud (48)");
			helper.assertTrue(soundAt(level, abs, CrowbarItem.FORCE_NOISE), "forcing leaves a radius-48 SOUND");
			helper.assertTrue(crowbar.getMaxDamage() / CrowbarItem.FORCE_DURABILITY == 8, "a crowbar forces about 8 boxes");
			helper.assertTrue(crowbar.getDamageValue() == CrowbarItem.FORCE_DURABILITY, "forcing costs "
					+ CrowbarItem.FORCE_DURABILITY + " durability, got " + crowbar.getDamageValue());
			helper.assertFalse(CrowbarItem.force(level, player, crowbar, abs), "an open box can't be forced again");
			helper.assertTrue(level.getBlockState(abs).getDestroyProgress(player, level, abs) > 0.0f, "an open box can be mined");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void keyCarrierDropsTheRightKey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 6);
		BlockPos min = helper.absolutePos(new BlockPos(1, 0, 1));
		BlockPos max = helper.absolutePos(new BlockPos(4, 0, 4));
		int x0 = Math.min(min.getX(), max.getX()), x1 = Math.max(min.getX(), max.getX());
		int z0 = Math.min(min.getZ(), max.getZ()), z1 = Math.max(min.getZ(), max.getZ());
		List<int[]> rows = new ArrayList<>();
		for (int z = z0; z <= z1; z++) {
			rows.add(new int[]{z, x0, x1});
		}
		Footprint footprint = Footprint.of(rows);
		int floorY = min.getY();
		long id = randomId();
		Unit unit = new Unit(0, floorY, floorY + 2, "Floor", footprint);
		// A bank (always eligible), Downtown so the population size doesn't depend on the day.
		Building building = new Building(id, Map.of("amenity", "bank", LostCitiesBuildingSource.TAG_ZONE, "downtown"),
				new int[]{floorY}, floorY, floorY + 3, footprint, List.of(unit));
		BuildingProfile.Population population = new BuildingProfile.Population(20f, Map.of(ZombieVariant.ZOMBIE, 1));
		BuildingPopulations.setOverridesForTesting(l -> l == id ? building : AshfallSalvage.buildings().get(l), (b, u) -> population);
		Strongboxes.setChanceForTesting(1.0f);
		List<Entity> members = new ArrayList<>();
		try {
			helper.assertTrue(BuildingPopulations.populate(level, building) > 0, "the bank gets a population");
			BlockPos found = null;
			for (BlockPos pos : BlockPos.betweenClosed(x0, floorY + 1, z0, x1, floorY + 2, z1)) {
				if (level.getBlockState(pos).is(Locks.STRONGBOX)) {
					found = pos.immutable();
				}
			}
			helper.assertTrue(found != null, "the bank's first population puts in a Strongbox");
			helper.assertTrue(Strongboxes.isLocked(level.getBlockState(found)), "the new box is locked");
			helper.assertValueEqual(((StrongboxBlockEntity) level.getBlockEntity(found)).building(), id, "box's building id");

			BuildingRecord record = DirectorState.get(level.getServer()).building(id);
			Zombie carrier = null;
			int carriers = 0;
			for (UUID uuid : record.members()) {
				Entity e = level.getEntity(uuid);
				if (e != null) {
					members.add(e);
					KeyData key = e.getAttached(BuildingKeys.CARRIER);
					if (key != null) {
						carriers++;
						carrier = (Zombie) e;
						helper.assertValueEqual(key.building(), id, "carried key's building id");
					}
				}
			}
			helper.assertValueEqual(carriers, 1, "key carriers in the population");
			Vec3 at = carrier.position();
			carrier.kill(level);
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3),
					item -> item.getItem().is(Locks.BUILDING_KEY));
			helper.assertTrue(drops.size() == 1, "the carrier drops one key, found " + drops.size());
			KeyData dropped = drops.getFirst().getItem().get(Locks.KEY_DATA);
			helper.assertTrue(dropped != null && dropped.building() == id && dropped.name().startsWith("Bank at "),
					"the dropped key opens this bank, got " + dropped);
			drops.forEach(Entity::discard);
			// A later population of the same building puts in no second box.
			BuildingPopulations.clear(level, id);
			level.removeBlockEntity(found); // first, or removing the block would spill the rolled loot
			level.setBlock(found, Blocks.AIR.defaultBlockState(), 3);
			DirectorState.get(level.getServer()).building(id).forceReady();
			BuildingPopulations.populate(level, building);
			for (BlockPos pos : BlockPos.betweenClosed(x0, floorY + 1, z0, x1, floorY + 2, z1)) {
				helper.assertFalse(level.getBlockState(pos).is(Locks.STRONGBOX), "a repopulated building gets no second box");
			}
			for (UUID uuid : DirectorState.get(level.getServer()).building(id).members()) {
				Entity e = level.getEntity(uuid);
				if (e != null) {
					members.add(e);
				}
			}
		} finally {
			BuildingPopulations.setOverridesForTesting(null, null);
			Strongboxes.setChanceForTesting(null);
			members.forEach(Entity::discard);
		}
		helper.succeed();
	}

	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
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
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		return player;
	}
}
