package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlockEntity;
import xyz.toesinthesand.ashfall.arms.charger.ChargerKind;
import xyz.toesinthesand.ashfall.arms.grid.CoreConversion;
import xyz.toesinthesand.ashfall.arms.grid.DistrictLoop;
import xyz.toesinthesand.ashfall.arms.grid.DistrictRecord;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridActions;
import xyz.toesinthesand.ashfall.arms.grid.GridBlocks;
import xyz.toesinthesand.ashfall.arms.grid.GridCommand;
import xyz.toesinthesand.ashfall.arms.grid.GridController;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.grid.GridPower;
import xyz.toesinthesand.ashfall.arms.grid.GridReclaim;
import xyz.toesinthesand.ashfall.arms.grid.GridRelight;
import xyz.toesinthesand.ashfall.arms.grid.GridSettings;
import xyz.toesinthesand.ashfall.arms.grid.GridSiege;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.arms.grid.SiegeSettings;
import xyz.toesinthesand.ashfall.arms.grid.SubstationCoreBlock;
import xyz.toesinthesand.ashfall.arms.grid.SubstationCoreBlockEntity;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.salvage.atlas.RelightLog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The district loop on a real server. Each test registers its own small test district over its own structure
 * (gametests run in parallel, so no test touches the real districts or the shared datapack numbers), with tiny
 * numbers: 2 couplers, 2 cells, a 2-second hold, a 10-hp core, one siege wave of one zombie. The test drives the
 * loop with explicit day-clock values instead of waiting for nightfall.
 */
public class GridGameTest {
	private static final BlockPos CORE = new BlockPos(3, 1, 3);
	private static final long NIGHT = 12_000L;

	private static GridSettings settings() {
		GridSettings d = GridSettings.DEFAULT;
		return new GridSettings(2, 2, 240L, 2, 4, 1, 32, new GridSettings.Pressure(15, 0, 0, 24, 40),
				new GridSettings.Core(10, 2.5, 3.0, 5, 0), 150, 23_000, 0, 6, d.reclaimed(), Map.of());
	}

	private static SiegeSettings siege() {
		return new SiegeSettings(List.of(new SiegeSettings.DayTier(1, 1)), 1, 2, 3, 90, 0,
				List.of(new SiegeSettings.Tier(1, 1, 1, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1))));
	}

	/** Registers a manual test district over this test's 8x8x8 structure. */
	private static String district(GameTestHelper helper, String id, Set<Long> relays) {
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 7, 7)));
		GridDistricts.registerTest(new GridDistricts.TestDistrict(id, box, relays, settings(), siege(), 1, true));
		// The gametest world is saved between runs, so a district may remember a core from an earlier run.
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

	/** Places the core and lets it register itself with its district. */
	private static SubstationCoreBlockEntity core(GameTestHelper helper) {
		helper.setBlock(CORE, GridBlocks.SUBSTATION_CORE);
		SubstationCoreBlockEntity core = helper.getBlockEntity(CORE, SubstationCoreBlockEntity.class);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(CORE);
		core.update(level, abs, level.getBlockState(abs));
		return core;
	}

	private static DistrictRecord record(GameTestHelper helper, String id) {
		return GridState.get(helper.getLevel().getServer()).record(id);
	}

	private static long clock(GameTestHelper helper) {
		return helper.getLevel().getOverworldClockTime();
	}

	/** A survival mock player (vanilla's helper is creative, which never uses up items). */
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

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	private static void cleanup(String id) {
		GridDistricts.unregisterTest(id);
	}

	@GameTest
	public void couplerSocketingRepairsTheCore(GameTestHelper helper) {
		String id = district(helper, "test_grid_socket", Set.of());
		floor(helper);
		core(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "grid-socket");
		try {
			ItemStack couplers = new ItemStack(GridBlocks.GRID_COUPLER, 3);
			BlockPos abs = helper.absolutePos(CORE);
			helper.assertValueEqual(GridActions.socket(level, abs, player, couplers), DistrictLoop.Result.ACCEPTED, "first coupler");
			helper.assertValueEqual(record(helper, id).state, DistrictState.DARK, "one of two couplers");
			helper.assertValueEqual(GridActions.socket(level, abs, player, couplers), DistrictLoop.Result.COMPLETED, "second coupler");
			helper.assertValueEqual(record(helper, id).state, DistrictState.REPAIRED, "state after both couplers");
			helper.assertValueEqual(couplers.getCount(), 1, "socketed couplers are used up");
			helper.assertValueEqual(GridActions.socket(level, abs, player, couplers), DistrictLoop.Result.FULL, "third coupler");
			helper.assertValueEqual(couplers.getCount(), 1, "a refused coupler is kept");
			helper.assertValueEqual(record(helper, id).corePos, abs.asLong(), "core registered for its district");
			core(helper);
			helper.assertBlockProperty(CORE, SubstationCoreBlock.POWER, 1);
			helper.succeed();
		} finally {
			remove(player);
			cleanup(id);
		}
	}

	@GameTest(maxTicks = 100)
	public void energizeHoldPausesWithoutPlayersThenLinks(GameTestHelper helper) {
		BlockPos relay = new BlockPos(6, 1, 6);
		long relayChunk = ChunkPos.pack(helper.absolutePos(relay));
		String id = district(helper, "test_grid_hold", Set.of(relayChunk));
		floor(helper);
		core(helper);
		helper.setBlock(relay, ArmsBlocks.POWER_RELAY);
		ServerLevel level = helper.getLevel();
		GridCommand.force(level, id, DistrictState.REPAIRED);
		ServerPlayer player = survivalPlayer(helper, "grid-hold");
		try {
			BlockPos abs = helper.absolutePos(CORE);
			// the mock joins at the world spawn, far outside the 4-block hold radius
			player.snapTo(abs.getX() + 40.5, abs.getY(), abs.getZ() + 40.5, 0f, 0f);
			ItemStack cells = ArmsItems.charged(ArmsItems.ENERGY_CELL);
			cells.setCount(1);
			ItemStack flat = new ItemStack(ArmsItems.ENERGY_CELL);
			helper.assertValueEqual(GridActions.loadCell(level, abs, player, flat), DistrictLoop.Result.WRONG_STATE, "a flat cell is refused");
			GridActions.loadCell(level, abs, player, cells);
			ItemStack second = ArmsItems.charged(ArmsItems.ENERGY_CELL);
			helper.assertValueEqual(GridActions.loadCell(level, abs, player, second), DistrictLoop.Result.COMPLETED, "second cell starts the hold");
			LoopCheck.hold(helper, id, 0, "freshly started");

			GridController.tickDistrict(level, id, clock(helper), 20);
			GridController.tickDistrict(level, id, clock(helper), 20);
			LoopCheck.hold(helper, id, 0, "nobody near: the hold must pause");

			player.snapTo(abs.getX() + 1.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
			GridController.tickDistrict(level, id, clock(helper), 20);
			LoopCheck.hold(helper, id, 20, "a player near: the hold runs");
			GridController.tickDistrict(level, id, clock(helper), 20);
			LoopCheck.hold(helper, id, 40, "hold complete");
			helper.assertValueEqual(record(helper, id).state, DistrictState.REPAIRED, "not energized while a key relay is unlinked");

			helper.assertValueEqual(GridActions.link(level, helper.absolutePos(relay), player), DistrictLoop.Result.COMPLETED, "the key relay links");
			DistrictRecord r = record(helper, id);
			helper.assertValueEqual(r.state, DistrictState.ENERGIZED, "hold done and every relay linked");
			helper.assertValueEqual(r.siegeAt, DistrictLoop.nextNightfall(clock(helper)), "siege at the coming nightfall");
			helper.succeed();
		} finally {
			remove(player);
			cleanup(id);
		}
	}

	@GameTest(maxTicks = 100)
	public void siegeFailReturnsToRepaired(GameTestHelper helper) {
		String id = district(helper, "test_grid_siege_fail", Set.of());
		floor(helper);
		core(helper);
		ServerLevel level = helper.getLevel();
		GridCommand.force(level, id, DistrictState.SIEGE);
		helper.assertValueEqual(record(helper, id).coreHp, 10, "core health at the start");
		Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 3));
		GridSiege.tagSiegeZombie(attacker, id);
		long t = record(helper, id).siegeAt + 100;
		GridController.tickDistrict(level, id, t, 20);
		helper.assertTrue(record(helper, id).coreHp < 10, "an attacking siege zombie damages the core");
		GridController.tickDistrict(level, id, t + 20, 20);
		GridController.tickDistrict(level, id, t + 40, 20);
		DistrictRecord r = record(helper, id);
		helper.assertValueEqual(r.state, DistrictState.REPAIRED, "a destroyed core goes back to repaired");
		helper.assertValueEqual(r.couplers, 2, "couplers kept");
		helper.assertValueEqual(r.cells, 0, "cells spent");
		helper.assertValueEqual(r.holdTicks, 0L, "hold to redo");
		GridSiege.siegeZombies(level, id, helper.absolutePos(CORE)).forEach(Entity::discard);
		cleanup(id);
		helper.succeed();
	}

	/**
	 * The control room's iron door must not make the siege harmless: the surge holds security doors open for the
	 * siege (wooden doors are left alone) and shuts them when it ends. Found in the Phase 3 headless playtest, where
	 * the River Arts core never took a hit because every wave stopped at the door.
	 */
	@GameTest(maxTicks = 100)
	public void siegeForcesSecurityDoorsOpenUntilItEnds(GameTestHelper helper) {
		String id = "test_grid_siege_doors";
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 7, 7)));
		GridSettings base = settings();
		GridSettings withDoors = new GridSettings(base.couplersRequired(), base.cellsRequired(), base.cellMinEnergy(),
				base.energizeSeconds(), base.holdRadius(), base.humIntervalSeconds(), base.humRadius(), base.pressure(),
				new GridSettings.Core(10, 2.5, 3.0, 5, 4), base.sirenWarningSeconds(), base.dawnTime(), base.minSiegePresenceSeconds(),
				base.presenceRadius(), base.reclaimed(), base.districts());
		GridDistricts.registerTest(new GridDistricts.TestDistrict(id, box, Set.of(), withDoors, siege(), 1, true));
		GridState.get(helper.getLevel().getServer()).update(helper.getLevel(), id, r -> {
			r.reset();
			r.corePos = DistrictRecord.NO_CORE;
		});
		floor(helper);
		core(helper);
		BlockPos iron = new BlockPos(6, 1, 3), wood = new BlockPos(0, 1, 3);
		helper.setBlock(iron, Blocks.IRON_DOOR.defaultBlockState());
		helper.setBlock(iron.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(wood, Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(wood.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		ServerLevel level = helper.getLevel();
		GridCommand.force(level, id, DistrictState.SIEGE);
		helper.assertTrue(helper.getBlockState(iron).getValue(DoorBlock.OPEN), "the siege surge opens the security door");
		helper.assertTrue(helper.getBlockState(iron.above()).getValue(DoorBlock.OPEN), "both halves open");
		helper.assertFalse(helper.getBlockState(wood).getValue(DoorBlock.OPEN), "wooden doors are left alone");
		// A player shutting it (or a button pulse ending) doesn't hold: the next siege tick reopens it.
		BlockState shut = helper.getBlockState(iron);
		((DoorBlock) Blocks.IRON_DOOR).setOpen(null, level, shut, helper.absolutePos(iron), false);
		GridController.tickDistrict(level, id, record(helper, id).siegeAt + 100, 20);
		helper.assertTrue(helper.getBlockState(iron).getValue(DoorBlock.OPEN), "held open while the siege lasts");
		GridCommand.force(level, id, DistrictState.REPAIRED);
		helper.assertFalse(helper.getBlockState(iron).getValue(DoorBlock.OPEN), "shut again when the siege ends");
		GridSiege.siegeZombies(level, id, helper.absolutePos(CORE)).forEach(Entity::discard);
		cleanup(id);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void siegeSuccessReclaims(GameTestHelper helper) {
		String id = district(helper, "test_grid_siege_win", Set.of());
		floor(helper);
		core(helper);
		ServerLevel level = helper.getLevel();
		GridCommand.force(level, id, DistrictState.SIEGE);
		long t = record(helper, id).siegeAt + 100;
		BlockPos abs = helper.absolutePos(CORE);
		GridController.tickDistrict(level, id, t, 20);
		helper.assertValueEqual(record(helper, id).wavesSpawned, 1, "the first wave spawns at once");
		List<Zombie> wave = GridSiege.siegeZombies(level, id, abs);
		helper.assertValueEqual(wave.size(), 1, "one siege zombie with Director tags");
		helper.assertTrue(wave.getFirst().isPersistenceRequired(), "siege zombies don't despawn");
		helper.assertValueEqual(record(helper, id).state, DistrictState.SIEGE, "a living siege zombie keeps the siege going");
		wave.forEach(Entity::discard);
		GridController.tickDistrict(level, id, t + 20, 20);
		DistrictRecord r = record(helper, id);
		helper.assertValueEqual(r.state, DistrictState.RECLAIMED, "every wave cleared");
		helper.assertTrue(r.reclaimedAtDay >= 1, "reclaimed day recorded");
		helper.assertTrue(GridState.get(level.getServer()).isReclaimed(id), "isReclaimed");
		helper.assertTrue(HavenState.get(level.getServer()).customZones().containsKey(GridReclaim.compoundZoneId(id)),
				"the substation compound is a safe zone");
		HavenState havens = HavenState.get(level.getServer());
		SafeZone compound = havens.customZones().get(GridReclaim.compoundZoneId(id));
		helper.assertTrue(compound.open(), "the compound is open to building like a Bunker Core claim");
		helper.assertTrue(Havens.inOpenZone(level, abs), "the core stands in an open zone");
		// A world reclaimed before compounds were open: start-up reopens it.
		havens.putCustomZone(GridReclaim.compoundZoneId(id), compound.withOpen(false));
		helper.assertTrue(GridReclaim.openCompounds(level.getServer()) >= 1, "start-up reopened no compound");
		helper.assertTrue(havens.customZones().get(GridReclaim.compoundZoneId(id)).open(), "an old closed compound stayed closed");
		helper.assertValueEqual(GridReclaim.openCompounds(level.getServer()), 0, "reopening twice changed something");
		helper.assertTrue(r.rewarded, "reward cache placed");
		boolean chest = false;
		for (BlockPos p : BlockPos.betweenClosed(abs.offset(-3, -1, -3), abs.offset(3, 1, 3))) {
			if (level.getBlockEntity(p) instanceof ChestBlockEntity c && c.getLootTable() != null
					&& c.getLootTable().identifier().equals(Identifier.fromNamespaceAndPath("ashfall_cache", "grid_reward_" + id))) {
				chest = true;
			}
		}
		helper.assertTrue(chest, "a per-player reward cache with the district's ashfall_cache table");
		GridCommand.force(level, id, DistrictState.DARK);
		helper.assertFalse(HavenState.get(level.getServer()).customZones().containsKey(GridReclaim.compoundZoneId(id)),
				"set back to dark, the compound is no safe zone any more");
		cleanup(id);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void fieldChargerChargesFreeInAReclaimedDistrict(GameTestHelper helper) {
		String id = district(helper, "test_grid_power", Set.of());
		ServerLevel level = helper.getLevel();
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ArmsBlocks.FIELD_CHARGER);
		ChargerBlockEntity charger = helper.getBlockEntity(pos, ChargerBlockEntity.class);
		ItemStack cell = new ItemStack(ChargerTestItems.TEST_CELL);
		SimpleEnergyItem.setStoredEnergyUnchecked(cell, 0);
		charger.setItem(0, cell);
		helper.assertFalse(GridPower.powered(level, helper.absolutePos(pos)), "dark district: no grid power");
		GridCommand.force(level, id, DistrictState.RECLAIMED);
		helper.assertTrue(GridPower.powered(level, helper.absolutePos(pos)), "reclaimed district powers chargers");
		helper.runAfterDelay(60, () -> {
			long energy = SimpleEnergyItem.getStoredEnergyUnchecked(charger.getItem(0));
			helper.assertTrue(energy > 0, "a Field Charger with no cell charges on the grid: got " + energy);
			helper.assertTrue(charger.getItem(ChargerKind.CELL_SLOT).isEmpty(), "no cell was needed");
			helper.assertTrue(charger.gridPowered(), "charger reports grid power");
			GridCommand.force(level, id, DistrictState.DARK);
			cleanup(id);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void relightReplaysLoggedLamps(GameTestHelper helper) {
		String id = district(helper, "test_grid_relight", Set.of());
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos lamp = new BlockPos(2, 1, 2), lantern = new BlockPos(5, 1, 5), patch = new BlockPos(2, 3, 5), built = new BlockPos(5, 1, 2);
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		helper.setBlock(patch, Blocks.SMOOTH_STONE);
		helper.setBlock(built, Blocks.OAK_PLANKS);
		RelightLog log = RelightLog.get(level.getServer());
		log.record(id, helper.absolutePos(lamp), Identifier.withDefaultNamespace("redstone_lamp"));
		log.record(id, helper.absolutePos(lantern), Identifier.withDefaultNamespace("lantern"));
		log.record(id, helper.absolutePos(patch), Identifier.withDefaultNamespace("glowstone"));
		log.record(id, helper.absolutePos(built), Identifier.withDefaultNamespace("lantern"));
		GridCommand.force(level, id, DistrictState.RECLAIMED);
		helper.assertBlockPresent(GridBlocks.GRID_LAMP, lamp);
		helper.assertBlockPresent(Blocks.LANTERN, lantern);
		helper.assertBlockPresent(Blocks.GLOWSTONE, patch);
		helper.assertBlockPresent(Blocks.OAK_PLANKS, built);
		helper.assertTrue(helper.getBlockState(lamp).getLightEmission() == 15, "the grid lamp is lit");

		// At night the restored lights near a player shine for the Director.
		ServerPlayer player = survivalPlayer(helper, "grid-relight");
		try {
			BlockPos abs = helper.absolutePos(lamp);
			player.snapTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 3.5, 0f, 0f);
			int emitted = GridRelight.emitLights(level, GridSettings.DEFAULT.reclaimed());
			helper.assertTrue(emitted >= 3, "restored lights became stimuli: " + emitted);
			boolean lit = Stimuli.query(level, Vec3.atCenterOf(abs), 1.0).stream().anyMatch(st -> st.type() == StimulusType.LIGHT);
			helper.assertTrue(lit, "a LIGHT stimulus stands at the grid lamp");
		} finally {
			remove(player);
		}
		GridCommand.force(level, id, DistrictState.DARK);
		cleanup(id);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void reclaimedDistrictSpawnsAFifthInTheDark(GameTestHelper helper) {
		String id = district(helper, "test_grid_spawn", Set.of());
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, 0, 2), new BlockPos(6, 4, 6))) {
			helper.setBlock(p, Blocks.STONE);
		}
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, 1, 3), new BlockPos(5, 3, 5))) {
			helper.setBlock(p, Blocks.AIR);
		}
		BlockPos spot = helper.absolutePos(new BlockPos(4, 1, 4));
		float[] before = {-1f};
		// Waits for the light engine to darken the sealed room, then measures once.
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getMaxLocalRawBrightness(spot) <= 7, "the sealed room is dark");
			if (before[0] < 0f) {
				before[0] = SpawnDirector.zoneSpotWeight(level, spot, 1);
				helper.assertTrue(before[0] > 0, "a dark spot has spawn weight");
				helper.assertValueEqual(SpawnDirector.darknessModifier(level, spot), 1f, "no modifier while dark");
				GridCommand.force(level, id, DistrictState.RECLAIMED);
			}
			float after = SpawnDirector.zoneSpotWeight(level, spot, 1);
			helper.assertTrue(Math.abs(SpawnDirector.darknessModifier(level, spot) - 0.2f) < 1e-6, "reclaimed: darkness modifier 0.2");
			// The weight is a probability clamped to 1: with an unclamped "before" it is exactly a fifth, and in a zone darker
			// than x1 (whose "before" clamps; the darkest zone is x1.3) at most 0.26; either way the district still spawns, only less.
			helper.assertTrue(after > 0 && after <= before[0] * 0.26f + 1e-4, "reclaimed: darkness weight cut to a fifth, got "
					+ before[0] + " -> " + after);
			if (before[0] < 1f) {
				helper.assertTrue(Math.abs(after - before[0] * 0.2f) < 1e-4, "exactly x0.2: " + before[0] + " -> " + after);
			}
			BlockPos outside = helper.absolutePos(new BlockPos(20, 1, 20));
			helper.assertTrue(GridDistricts.districtAt(level, outside).map(d -> !d.equals(id)).orElse(true),
					"the modifier stays inside the district");
			GridCommand.force(level, id, DistrictState.DARK);
			cleanup(id);
		});
	}

	@GameTest
	public void placeholderCoreBecomesTheRealCore(GameTestHelper helper) {
		String id = district(helper, "test_grid_convert", Set.of());
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.POLISHED_BLACKSTONE_SLAB);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.HEAVY_CORE);
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.HEAVY_CORE); // not on a plinth: someone's mace block, left alone
		BlockPos abs = helper.absolutePos(new BlockPos(3, 2, 3));
		LevelChunk chunk = level.getChunkAt(abs);
		int placed = CoreConversion.convert(level, chunk);
		helper.assertTrue(placed >= 1, "the placeholder was converted");
		helper.assertBlockPresent(GridBlocks.SUBSTATION_CORE, new BlockPos(3, 2, 3));
		helper.assertBlockPresent(Blocks.HEAVY_CORE, new BlockPos(5, 1, 5));
		SubstationCoreBlockEntity core = helper.getBlockEntity(new BlockPos(3, 2, 3), SubstationCoreBlockEntity.class);
		helper.assertValueEqual(core.district().orElse(""), id, "the new core knows its district");
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(GridBlocks.SUBSTATION_CORE).toString().equals("ashfall:substation_core"), "id");
		cleanup(id);
		helper.succeed();
	}

	@GameTest
	public void couplerCraftsWithoutACrystal(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
		ItemStack c = new ItemStack(ArmsItems.WIRE_SPOOL), s = new ItemStack(ArmsItems.SCRAP_ELECTRONICS);
		ItemStack w = new ItemStack(ArmsItems.WEAPON_PARTS), b = new ItemStack(ArmsItems.BATTERY);
		ItemStack m = new ItemStack(xyz.toesinthesand.ashfall.salvage.item.SalvageItems.SCRAP_METAL);
		// one Weapon Part, not two (rc15): Weapon Parts are found only, and 2 per coupler starved the Grid
		CraftingInput input = CraftingInput.of(3, 2, List.of(c, s, c, w, b, m));
		ItemStack result = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertTrue(result.is(GridBlocks.GRID_COUPLER) && result.getCount() == 1, "crafted " + result);
		helper.succeed();
	}

	/** Small assertion helpers on the hold. */
	private static final class LoopCheck {
		static void hold(GameTestHelper helper, String id, long expected, String what) {
			helper.assertValueEqual(record(helper, id).holdTicks, expected, what);
		}
	}
}
