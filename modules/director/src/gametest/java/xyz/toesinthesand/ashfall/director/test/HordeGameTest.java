package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.horde.Horde;
import xyz.toesinthesand.ashfall.director.horde.HordeSettings;
import xyz.toesinthesand.ashfall.director.horde.Hordes;
import xyz.toesinthesand.ashfall.director.pathing.EntranceFinder;
import xyz.toesinthesand.ashfall.director.pathing.SiegeRoutes;
import xyz.toesinthesand.ashfall.director.senses.BloodTrails;
import xyz.toesinthesand.ashfall.director.senses.SenseGoal;
import xyz.toesinthesand.ashfall.director.senses.SenseGoals;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;

import java.util.List;
import java.util.UUID;

/**
 * Plan M8 on a real server: hordes form and merge, a horde hears further than one zombie, a wounded player leaves a
 * blood trail zombies smell, and a siege route leads a zombie through a building's door to a target inside.
 * <p>
 * Horde updates are driven by the test in a single tick, so the server's own horde pass (every 40 ticks) never runs
 * in between; generous padding keeps other tests' zombies out of grouping range.
 */
public class HordeGameTest {
	private static final int PADDING = 32;

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A Director walker that stands still (no AI), so only the horde logic is tested. */
	private static Zombie walker(GameTestHelper helper, int x, int z) {
		BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
		Zombie zombie = DirectorSpawns.spawn(helper.getLevel(), pos, ZombieVariants.byName("walker"), DirectorZombie.Source.AMBIENT,
				DirectorZombie.NO_BUILDING);
		helper.assertTrue(zombie != null, "a Director walker spawned");
		zombie.setNoAi(true);
		return zombie;
	}

	@GameTest(padding = PADDING)
	public void hordesFormThenMerge(GameTestHelper helper) {
		floor(helper);
		List<Zombie> a = List.of(walker(helper, 0, 0), walker(helper, 1, 0), walker(helper, 0, 1));
		List<Zombie> b = List.of(walker(helper, 7, 7), walker(helper, 6, 7), walker(helper, 7, 6));
		ServerLevel level = helper.getLevel();
		HordeSettings d = HordeSettings.DEFAULT;
		try {
			// first with merging out of reach: two hordes of three
			HordeSettings.setForTesting(new HordeSettings(d.updateTicks(), 8.0, 3, 1.0, 40, d.stimulusScale(), d.leash(),
					d.arriveRadius(), d.blood()));
			Hordes.update(level);
			Horde ha = Hordes.hordeOf(a.getFirst());
			Horde hb = Hordes.hordeOf(b.getFirst());
			helper.assertTrue(ha != null && hb != null, "both groups formed hordes");
			helper.assertTrue(ha != hb, "groups 9+ blocks apart are two hordes");
			helper.assertValueEqual(ha.size(), 3, "horde A size");
			helper.assertValueEqual(hb.size(), 3, "horde B size");
			for (Zombie z : a) {
				helper.assertTrue(Hordes.hordeOf(z) == ha, "every zombie of group A follows the same leader");
			}
			// then with the real merge radius (16): the leaders are close enough to merge
			HordeSettings.setForTesting(new HordeSettings(d.updateTicks(), 8.0, 3, 16.0, 40, d.stimulusScale(), d.leash(),
					d.arriveRadius(), d.blood()));
			Hordes.update(level);
			Horde merged = Hordes.hordeOf(a.getFirst());
			helper.assertTrue(merged != null && merged == Hordes.hordeOf(b.getFirst()), "the two hordes merged");
			helper.assertValueEqual(merged.size(), 6, "merged size");
			helper.assertTrue(merged.members().contains(merged.leader()), "the merged horde has one leader among its members");
		} finally {
			HordeSettings.setForTesting(null);
			a.forEach(Entity::discard);
			b.forEach(Entity::discard);
		}
		helper.succeed();
	}

	/**
	 * Horde-night waves roll each member for a special infected (M9 wired into M8), so a special spawned as a HORDE
	 * Director zombie must group like any other member: it gets the horde goal and joins the walkers' horde.
	 */
	@GameTest(padding = PADDING)
	public void specialInfectedJoinHordes(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		List<Zombie> walkers = List.of(walker(helper, 1, 1), walker(helper, 2, 1));
		SpecialInfectedEntity crawler = SpecialInfected.spawn(level, helper.absolutePos(new BlockPos(1, 1, 2)), SpecialKind.CRAWLER,
				DirectorZombie.Source.HORDE);
		helper.assertTrue(crawler != null, "a horde crawler spawned through the Director");
		crawler.setNoAi(true);
		try {
			helper.assertTrue(Hordes.eligible(crawler), "a HORDE special is horde material");
			Hordes.update(level);
			Horde horde = Hordes.hordeOf(crawler);
			helper.assertTrue(horde != null, "the crawler is in a horde");
			helper.assertTrue(horde == Hordes.hordeOf(walkers.getFirst()), "the same horde as the walkers");
			helper.assertValueEqual(horde.size(), 3, "two walkers and the crawler");
		} finally {
			walkers.forEach(Entity::discard);
			crawler.discard();
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void aHordeHearsFurtherThanOneZombie(GameTestHelper helper) {
		floor(helper);
		List<Zombie> group = List.of(walker(helper, 1, 1), walker(helper, 2, 1), walker(helper, 1, 2));
		ServerLevel level = helper.getLevel();
		try {
			Hordes.update(level);
			Horde horde = Hordes.hordeOf(group.getFirst());
			helper.assertTrue(horde != null, "the three formed a horde");
			Zombie leader = (Zombie) Hordes.leaderMob(horde);
			helper.assertTrue(leader != null, "leader loaded");
			// radius 10, 16 blocks from the leader: beyond one zombie (10 x 1.0), within a horde's reach (10 x 2)
			Vec3 noise = leader.getEyePosition().add(0, 0, 16);
			Stimulus sound = Stimuli.emit(level, noise, StimulusType.SOUND, 10, null);
			helper.assertTrue(sound != null, "sound emitted");
			SenseGoal single = SenseGoals.attach(leader, 1.0);
			helper.assertTrue(single.pickStimulus() != sound, "a single zombie does not hear it");
			Hordes.update(level);
			Vec3 dest = horde.destination();
			helper.assertTrue(dest != null && dest.distanceToSqr(noise) < 0.01, "the horde heads for the noise, got " + dest);
		} finally {
			group.forEach(Entity::discard);
		}
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void aWoundedPlayerLeavesABloodTrail(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = mockPlayer(level);
		try {
			Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
			player.snapTo(at.x, at.y, at.z, 0f, 0f);
			HordeSettings.Blood blood = HordeSettings.DEFAULT.blood();
			helper.assertTrue(BloodTrails.tick(level, player, blood) == null, "a healthy player does not bleed");
			player.setHealth(4f);
			Stimulus drop = BloodTrails.tick(level, player, blood);
			helper.assertTrue(drop != null && drop.type() == StimulusType.BLOOD, "a wounded player leaves a BLOOD drop");
			helper.assertTrue(BloodTrails.tick(level, player, blood) == null, "no new drop while standing still");
			Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(6.5, 1, 1.5));
			SenseGoal goal = SenseGoals.attach(zombie, 1.0);
			helper.assertTrue(goal.pickStimulus() == drop, "a zombie 5 blocks away smells the blood");
			zombie.discard();
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * A small "substation": a roofed building with one door on the far side and an inner wall, the target behind it.
	 * The finder must find the door, and a zombie outside must walk in through it to the target (Phase 3: siege
	 * zombies stood on roofs and in cellars instead).
	 */
	@GameTest(maxTicks = 500, padding = PADDING, skyAccess = true)
	public void siegeRouteLeadsInThroughTheDoor(GameTestHelper helper) {
		floor(helper);
		building(helper);
		BlockPos target = new BlockPos(4, 1, 5);
		helper.setBlock(target, Blocks.IRON_BLOCK);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(target);
		EntranceFinder.Result found = EntranceFinder.find(level, abs);
		helper.assertFalse(found.outdoors(), "the target is indoors");
		helper.assertValueEqual(found.exit(), helper.absolutePos(new BlockPos(1, 1, 1)), "the exit is just outside the door");
		helper.assertTrue(found.path().contains(helper.absolutePos(new BlockPos(2, 1, 1))), "the way out goes through the doorway");
		helper.assertTrue(found.path().contains(helper.absolutePos(new BlockPos(6, 1, 3))), "and round the inner wall");

		SiegeRoutes.invalidate();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(0.5, 1, 6.5));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the test's daylight
		SiegeRoutes.assign(zombie, abs);
		Vec3 centre = Vec3.atCenterOf(abs);
		helper.succeedWhen(() -> {
			double dx = zombie.getX() - centre.x, dz = zombie.getZ() - centre.z;
			helper.assertTrue(dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(zombie.getY() - abs.getY()) <= 1.0,
					"zombie at " + zombie.position() + " has not reached the target at " + abs);
		});
	}

	/** Walls round x 2..7, z 0..7 (3 high), a roof at y 4, a doorway at (2, 1..2, 1) and an inner wall z 3, x 3..5. */
	static void building(GameTestHelper helper) {
		for (int x = 2; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				boolean edge = x == 2 || x == 7 || z == 0 || z == 7;
				for (int y = 1; y <= 3; y++) {
					if (edge) {
						helper.setBlock(x, y, z, Blocks.STONE_BRICKS);
					}
				}
				helper.setBlock(x, 4, z, Blocks.STONE_BRICKS);
			}
		}
		helper.setBlock(2, 1, 1, Blocks.AIR);
		helper.setBlock(2, 2, 1, Blocks.AIR);
		for (int x = 3; x <= 5; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, 3, Blocks.STONE_BRICKS);
			}
		}
	}

	/** A survival mock player in the player list (removed by the caller), as in {@code HavenGameTest}. */
	private static ServerPlayer mockPlayer(ServerLevel level) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "horde-mock"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.getAbilities().invulnerable = false;
		return player;
	}
}
