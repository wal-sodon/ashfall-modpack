package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import xyz.toesinthesand.ashfall.arms.grid.DistrictLoop;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridActions;
import xyz.toesinthesand.ashfall.arms.grid.GridBlocks;
import xyz.toesinthesand.ashfall.arms.grid.GridBreach;
import xyz.toesinthesand.ashfall.arms.grid.GridCommand;
import xyz.toesinthesand.ashfall.arms.grid.GridController;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridReclaim;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.grid.GridSiege;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.grid.SiegeSettings;
import xyz.toesinthesand.ashfall.arms.grid.SubstationCoreBlockEntity;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.horde.HordeHooks;
import xyz.toesinthesand.ashfall.director.horde.HordeNights;
import xyz.toesinthesand.ashfall.director.pathing.SiegeRoutes;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plan M8's grid half on a real server: Breach events (a horde hits a reclaimed core; a lost Breach breaks the district,
 * re-repair costs half the couplers; a held one keeps it), the Grid tier hook the Director's horde nights read, and a
 * siege zombie led in through a substation's door to its core. Each test owns a manual test district (see
 * {@code GridGameTest}) and drives the loop with explicit clocks.
 */
public class BreachGameTest {
	private static final BlockPos CORE = new BlockPos(3, 1, 3);
	private static final int PADDING = 24;

	/** 2 couplers (re-repair 1), 2 cells, a 10-hp core with a 2.5-block reach doing 5 hp per zombie-second. */
	private static GridSettings settings() {
		GridSettings d = GridSettings.DEFAULT;
		return new GridSettings(2, 2, 240L, 2, 4, 1, 32, new GridSettings.Pressure(15, 0, 0, 24, 40),
				new GridSettings.Core(10, 2.5, 3.0, 5, 0), 150, 23_000, 0, 6, d.reclaimed(), Map.of());
	}

	private static SiegeSettings siege() {
		return new SiegeSettings(List.of(new SiegeSettings.DayTier(1, 1)), 1, 2, 3, 90, 0,
				List.of(new SiegeSettings.Tier(1, 1, 1, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1))));
	}

	/** Waves far out of the test area (120 blocks), so no wave zombie reaches the test's core. */
	private static SiegeSettings farWaves() {
		return new SiegeSettings(List.of(new SiegeSettings.DayTier(1, 1)), 1, 120, 121, 3600, 0,
				List.of(new SiegeSettings.Tier(1, 1, 1, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1))));
	}

	/**
	 * Like {@link #farWaves()} but two waves. A far spawn can fail (unloaded chunk, no ground 120 blocks out), and with
	 * one wave a failed spawn means "every wave spawned, none alive": the siege is won on its first tick. Two waves
	 * keep it running until dawn whichever way the first spawn goes.
	 */
	private static SiegeSettings farWavesTwice() {
		return new SiegeSettings(List.of(new SiegeSettings.DayTier(1, 1)), 1, 120, 121, 3600, 0,
				List.of(new SiegeSettings.Tier(1, 2, 1, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1))));
	}

	private static String district(GameTestHelper helper, String id, SiegeSettings siege) {
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 7, 7)));
		GridDistricts.registerTest(new GridDistricts.TestDistrict(id, box, Set.of(), settings(), siege, 1, true));
		GridState.get(helper.getLevel().getServer()).update(helper.getLevel(), id, r -> {
			r.reset();
			r.corePos = DistrictRecord.NO_CORE;
		});
		return id;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void core(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, GridBlocks.SUBSTATION_CORE);
		SubstationCoreBlockEntity core = helper.getBlockEntity(at, SubstationCoreBlockEntity.class);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		core.update(level, abs, level.getBlockState(abs));
	}

	private static DistrictRecord record(GameTestHelper helper, String id) {
		return GridState.get(helper.getLevel().getServer()).record(id);
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

	@GameTest(maxTicks = 100, padding = PADDING)
	public void lostBreachBreaksTheDistrictAndReRepairCostsHalf(GameTestHelper helper) {
		String id = district(helper, "test_breach_lost", siege());
		floor(helper);
		core(helper, CORE);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "breach-repair");
		try {
			GridCommand.force(level, id, DistrictState.RECLAIMED);
			long reclaimedDay = record(helper, id).reclaimedAtDay;
			helper.assertTrue(HavenState.get(level.getServer()).customZones().containsKey(GridReclaim.compoundZoneId(id)),
					"reclaimed: the compound is a safe zone");
			helper.assertFalse(new GridBreach().targets(level.getServer()).contains(id), "the schedule never picks a test district");

			helper.assertTrue(new GridBreach().begin(level, id), "the Breach starts on a reclaimed core");
			DistrictRecord r = record(helper, id);
			helper.assertValueEqual(r.state, DistrictState.SIEGE, "under attack");
			helper.assertTrue(r.breach, "flagged as a Breach");
			helper.assertFalse(HavenState.get(level.getServer()).customZones().containsKey(GridReclaim.compoundZoneId(id)),
					"a district under attack is no safe zone");

			Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 3));
			GridSiege.tagSiegeZombie(attacker, id);
			long t = r.siegeAt + 100;
			for (int i = 0; i < 4 && record(helper, id).state == DistrictState.SIEGE; i++) {
				GridController.tickDistrict(level, id, t + i * 20L, 20);
			}
			r = record(helper, id);
			helper.assertValueEqual(r.state, DistrictState.BROKEN, "a core lost to a Breach breaks the district");
			helper.assertValueEqual(r.couplers, 0, "the broken core holds no couplers");
			helper.assertFalse(GridState.get(level.getServer()).isReclaimed(id), "broken is not reclaimed (dark again)");

			ItemStack couplers = new ItemStack(GridBlocks.GRID_COUPLER, 3);
			BlockPos abs = helper.absolutePos(CORE);
			helper.assertValueEqual(DistrictLoop.reRepairCouplers(settings().rules()), 1, "half of 2");
			helper.assertValueEqual(GridActions.socket(level, abs, player, couplers), DistrictLoop.Result.COMPLETED,
					"one coupler re-repairs it");
			helper.assertValueEqual(couplers.getCount(), 2, "only half the couplers were used");
			r = record(helper, id);
			helper.assertValueEqual(r.state, DistrictState.RECLAIMED, "back on the grid");
			helper.assertValueEqual(r.reclaimedAtDay, reclaimedDay, "keeps its first reclaim day");
			helper.assertTrue(HavenState.get(level.getServer()).customZones().containsKey(GridReclaim.compoundZoneId(id)),
					"the safe compound is back");
			GridSiege.siegeZombies(level, id, abs).forEach(Entity::discard);
			attacker.discard();
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(player);
			GridDistricts.unregisterTest(id);
		}
	}

	@GameTest(maxTicks = 100, padding = PADDING)
	public void breachStillStandingAtDawnIsHeld(GameTestHelper helper) {
		String id = district(helper, "test_breach_held", farWavesTwice());
		floor(helper);
		core(helper, CORE);
		ServerLevel level = helper.getLevel();
		try {
			GridCommand.force(level, id, DistrictState.RECLAIMED);
			helper.assertTrue(new GridBreach().begin(level, id), "Breach started");
			long start = record(helper, id).siegeAt;
			GridController.tickDistrict(level, id, start + 100, 20);
			helper.assertValueEqual(record(helper, id).state, DistrictState.SIEGE, "the Breach runs through the night");
			GridController.tickDistrict(level, id, start + settings().rules().nightTicks(), 20);
			DistrictRecord r = record(helper, id);
			helper.assertValueEqual(r.state, DistrictState.RECLAIMED, "held at dawn, nobody needed at the core");
			helper.assertFalse(r.breach, "the Breach is over");
			helper.assertTrue(HavenState.get(level.getServer()).customZones().containsKey(GridReclaim.compoundZoneId(id)),
					"the compound is safe again");
			boolean cache = BlockPos.betweenClosedStream(helper.absolutePos(CORE).offset(-3, -2, -3), helper.absolutePos(CORE).offset(3, 0, 3))
					.anyMatch(p -> level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.CHEST));
			helper.assertTrue(cache, "a held Breach leaves a supply cache at the core");
			GridSiege.siegeZombies(level, id, helper.absolutePos(CORE)).forEach(Entity::discard);
			helper.succeed();
		} finally {
			GridDistricts.unregisterTest(id);
		}
	}

	/**
	 * The Director's scheduler starts Breaches through {@link HordeHooks#breach()}, never through arms directly, so this
	 * checks the registered hook is the real grid: it is {@link GridBreach}, the Director's own start path refuses a
	 * test district (the schedule must never hit one), and a Breach started through the hook is a real grid SIEGE.
	 */
	@GameTest(maxTicks = 100, padding = PADDING)
	public void directorBreachHookDrivesTheRealGrid(GameTestHelper helper) {
		String id = district(helper, "test_breach_hook", farWaves());
		floor(helper);
		core(helper, CORE);
		ServerLevel level = helper.getLevel();
		try {
			GridCommand.force(level, id, DistrictState.RECLAIMED);
			helper.assertTrue(HordeHooks.breach() instanceof GridBreach, "arms registered the grid as the Breach hook");
			helper.assertTrue(HordeNights.startBreach(level, id) == null, "the Director's own path skips test districts");
			helper.assertValueEqual(record(helper, id).state, DistrictState.RECLAIMED, "so nothing started");
			helper.assertTrue(HordeHooks.breach().begin(level, id), "the hook starts the Breach");
			DistrictRecord r = record(helper, id);
			helper.assertValueEqual(r.state, DistrictState.SIEGE, "a real grid siege");
			helper.assertTrue(r.breach, "flagged as a Breach");
			helper.assertValueEqual(HordeHooks.breach().name(id).getString(), GridDistricts.name(id).getString(),
					"the siren names the grid's district");
			GridController.tickDistrict(level, id, r.siegeAt + settings().rules().nightTicks(), 20);
			helper.assertValueEqual(record(helper, id).state, DistrictState.RECLAIMED, "held at dawn");
			GridSiege.siegeZombies(level, id, helper.absolutePos(CORE)).forEach(Entity::discard);
			helper.succeed();
		} finally {
			GridDistricts.unregisterTest(id);
		}
	}

	/** Horde tier = max(day tier, Grid tier): arms hands the Director its Grid tier through the hook. */
	@GameTest(padding = PADDING)
	public void hordeNightsReadTheGridTier(GameTestHelper helper) {
		GridState grid = GridState.get(helper.getLevel().getServer());
		int expected = SiegeSettings.get().gridTier(1, grid.reclaimedCount());
		helper.assertValueEqual(HordeHooks.gridTier(), expected, "the Director sees the grid's tier");
		helper.assertValueEqual(GridBreach.gridTier(), expected, "same number from arms");
		helper.succeed();
	}

	/**
	 * A siege zombie outside a roofed substation, with the door on the far side and an inner wall, walks in through
	 * the door and batters the core (Phase 3: they stood on roofs and in cellars instead).
	 */
	@GameTest(maxTicks = 600, padding = PADDING, skyAccess = true)
	public void siegeZombieWalksInThroughTheDoorToTheCore(GameTestHelper helper) {
		// waves far out of the test area, so only the zombie placed below can reach the core
		String id = district(helper, "test_breach_route", farWaves());
		floor(helper);
		building(helper);
		BlockPos coreAt = new BlockPos(4, 1, 5);
		core(helper, coreAt);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(coreAt);
		GridCommand.force(level, id, DistrictState.SIEGE);
		GridState.get(level.getServer()).update(level, id, r -> r.wavesSpawned = 1); // the one wave is "out": no spawns here
		SiegeRoutes.invalidate();
		Zombie zombie = DirectorSpawns.spawn(level, helper.absolutePos(new BlockPos(0, 1, 6)), ZombieVariants.byName("walker"),
				DirectorZombie.Source.SIEGE, DirectorZombie.NO_BUILDING);
		helper.assertTrue(zombie != null, "siege zombie spawned");
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the test's daylight
		GridSiege.tagSiegeZombie(zombie, id);
		long t = record(helper, id).siegeAt + 100;
		int[] ticks = {0};
		helper.succeedWhen(() -> {
			if (ticks[0]++ % 20 == 0 && record(helper, id).state == DistrictState.SIEGE) {
				GridController.tickDistrict(level, id, t, 20); // assigns the route, counts attackers
			}
			helper.assertTrue(record(helper, id).coreHp < 10 || record(helper, id).state != DistrictState.SIEGE,
					"zombie at " + zombie.position() + " has not reached the core at " + abs);
			GridSiege.siegeZombies(level, id, abs).forEach(Entity::discard);
			GridCommand.force(level, id, DistrictState.DARK);
			GridDistricts.unregisterTest(id);
		});
	}

	/** Walls round x 2..7, z 0..7 (3 high), a roof at y 4, a doorway at (2, 1..2, 1) and an inner wall z 3, x 3..5. */
	private static void building(GameTestHelper helper) {
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
}
