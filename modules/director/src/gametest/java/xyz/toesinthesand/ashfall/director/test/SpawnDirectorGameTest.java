package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.horde.HordeNights;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.spawn.BuildingPopulations;
import xyz.toesinthesand.ashfall.director.spawn.BuildingRecord;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorState;
import xyz.toesinthesand.ashfall.director.spawn.DirectorTracker;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.director.spawn.SpawnRules;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawning on a real server: zombies-only filtering, the game-rule setup, light and claim exclusions,
 * variant stats, caps and the building population lifecycle.
 * <p>
 * Global test hooks (fake claim check, world cap, building overrides) are set and restored inside one
 * synchronous block wherever possible, since other tests tick alongside.
 */
public class SpawnDirectorGameTest {
	/** Places a stone floor under the given relative cells so spots are standable. */
	private static void floor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A closed 3x3 stone room (inner cell at relative 2,1,2, two blocks tall) with no light inside. */
	private static BlockPos darkRoom(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				for (int y = 0; y <= 3; y++) {
					boolean inner = x == 2 && z == 2 && (y == 1 || y == 2);
					helper.setBlock(x, y, z, inner ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		return helper.absolutePos(new BlockPos(2, 1, 2));
	}

	@GameTest
	public void overworldBiomesHaveNoMonsterSpawns(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		var settings = helper.getLevel().environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos);
		helper.assertTrue(settings.getMobsInCategory(MobCategory.MONSTER).isEmpty(),
				"overworld biome still lists natural monsters: " + settings.getMobsInCategory(MobCategory.MONSTER).unwrap());
		helper.succeed();
	}

	/**
	 * Biomes the test world's generator never produces still lose their monsters (an Arnis world's
	 * chunks carry biomes its flat generator does not list), while Nether biomes keep theirs.
	 */
	@GameTest
	public void ungeneratedOverworldBiomesLoseMonstersNetherKeepsThem(GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		MobSpawnSettings desert = biomes.getOrThrow(Biomes.DESERT).value().getAttributes()
				.applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
		helper.assertTrue(desert.getMobsInCategory(MobCategory.MONSTER).isEmpty(),
				"desert still lists natural monsters: " + desert.getMobsInCategory(MobCategory.MONSTER).unwrap());
		MobSpawnSettings deltas = biomes.getOrThrow(Biomes.BASALT_DELTAS).value().getAttributes()
				.applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY);
		helper.assertTrue(!deltas.getMobsInCategory(MobCategory.MONSTER).isEmpty(), "the Nether must stay vanilla");
		helper.succeed();
	}

	/**
	 * The ambient Director reads the same day as horde nights and sieges: the overworld day clock, not elapsed game
	 * time. Sleeping moves only the clock, so before phase 7 a world that slept had ambient caps and specials a week
	 * behind its horde nights.
	 */
	@GameTest
	public void theDirectorDayIsTheHordeDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long clock = level.getOverworldClockTime();
		int hordeDay = xyz.toesinthesand.ashfall.director.horde.HordeSchedule.day(clock);
		helper.assertValueEqual(DirectorDay.today(level), hordeDay, "director day vs horde day");
		helper.assertValueEqual(DirectorDay.today(level.getServer()), hordeDay, "server day vs horde day");
		GameRules rules = level.getGameRules();
		int expected = SpawnRules.scaleBySpawnMultiplier(
				SpawnRules.ambientCap(hordeDay, rules.get(SpawnDirector.AMBIENT_CAP_START), rules.get(SpawnDirector.AMBIENT_CAP_MAX)),
				xyz.toesinthesand.ashfall.director.story.StoryHooks.spawnMultiplier(level.getServer()));
		helper.assertValueEqual(SpawnDirector.ambientCap(level), expected, "ambient cap on the day clock");
		helper.succeed();
	}

	/** Sight rays never cross unloaded chunks (a ray there would load them from the spawn loop). */
	@GameTest
	public void sightRaysSkipUnloadedChunks(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		Vec3 here = Vec3.atCenterOf(pos);
		helper.assertTrue(SpawnDirector.segmentLoaded(helper.getLevel(), here, here.add(2, 0, 2)), "a short local ray is loaded");
		helper.assertTrue(!SpawnDirector.segmentLoaded(helper.getLevel(), here, here.add(5_000, 0, 5_000)),
				"a ray into unloaded chunks must be refused");
		helper.assertTrue(!helper.getLevel().hasChunkAt(pos.offset(5_000, 0, 5_000)), "the check itself loaded a chunk");
		helper.succeed();
	}

	@GameTest
	public void safetyNetRefusesNaturalNonZombieMonsters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 0, 4, 0, 4);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));

		Skeleton natural = EntityTypes.SKELETON.create(level, EntitySpawnReason.NATURAL);
		natural.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		boolean added = level.addFreshEntity(natural);
		helper.assertTrue(!added && level.getEntity(natural.getUUID()) == null, "a natural skeleton got into the overworld");

		Skeleton commanded = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		commanded.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		helper.assertTrue(level.addFreshEntity(commanded), "commands must still be able to summon skeletons");

		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.NATURAL);
		zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		helper.assertTrue(level.addFreshEntity(zombie), "natural zombies are allowed");

		commanded.discard();
		zombie.discard();
		helper.succeed();
	}

	@GameTest
	public void gameRulesSetOnceOnFirstStart(GameTestHelper helper) {
		// The gametest server resets game rules on every start but reuses its run directory (and thus our
		// saved flag), so the real first start only happens once per clean checkout. Recreate it here.
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		DirectorState state = DirectorState.get(server);
		GameRules rules = level.getGameRules();
		state.resetGameRulesAppliedForTesting();
		rules.set(GameRules.SPAWN_PATROLS, true, server);
		SpawnDirector.applyGameRulesOnce(server);
		helper.assertTrue(state.gameRulesApplied(), "the one-time flag is not saved");
		helper.assertTrue(!rules.get(GameRules.SPAWN_PATROLS), "patrols still on");
		helper.assertTrue(!rules.get(GameRules.SPAWN_PHANTOMS), "phantoms still on");
		helper.assertTrue(!rules.get(GameRules.SPAWN_WANDERING_TRADERS), "wandering traders still on");
		// Once applied, an admin turning patrols back on is respected on the next start.
		rules.set(GameRules.SPAWN_PATROLS, true, server);
		SpawnDirector.applyGameRulesOnce(server);
		helper.assertTrue(rules.get(GameRules.SPAWN_PATROLS), "a later admin change was overwritten");
		rules.set(GameRules.SPAWN_PATROLS, false, server);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void noSpawnsAtLightFifteen(GameTestHelper helper) {
		floor(helper, 0, 4, 0, 4);
		helper.setBlock(2, 1, 2, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertValueEqual(level.getMaxLocalRawBrightness(pos), 15, "light at the spot");
			helper.assertValueEqual(SpawnDirector.check(level, pos), SpawnDirector.Verdict.LIGHT, "verdict at light 15");
			for (int i = 0; i < 200; i++) {
				helper.assertTrue(SpawnDirector.tryAmbientSpawn(level, pos, 30, level.getRandom()) == null, "spawned at light 15");
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void noSpawnsInsideAClaim(GameTestHelper helper) {
		BlockPos pos = darkRoom(helper);
		helper.runAfterDelay(10, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(SpawnDirector.spotWeight(level, pos) > 0, "dark room is not dark: light "
					+ level.getMaxLocalRawBrightness(pos));
			helper.assertTrue(SpawnDirector.check(level, pos) != SpawnDirector.Verdict.CLAIM, "claimed without a claim");
			Claims.setCheckerForTesting((l, p) -> true);
			try {
				helper.assertValueEqual(SpawnDirector.check(level, pos), SpawnDirector.Verdict.CLAIM, "verdict in a claim");
				for (int i = 0; i < 100; i++) {
					helper.assertTrue(SpawnDirector.tryAmbientSpawn(level, pos, 30, level.getRandom()) == null, "spawned in a claim");
				}
			} finally {
				Claims.setCheckerForTesting(null);
			}
			helper.succeed();
		});
	}

	/**
	 * Vanilla's finalizeSpawn gives a few zombies an iron sword or shovel; the variant must replace it, not add to it
	 * (the armored test above caught 3 pieces where the file allows 2, about one run in twenty). Deterministic here:
	 * the weapon is put in by hand, as vanilla would, before the variant is applied.
	 */
	@GameTest
	public void variantReplacesVanillaWeapon(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var runner = ZombieVariants.byName("runner");
		helper.assertTrue(runner != null, "runner variant not loaded from the datapack");
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(zombie != null, "no zombie");
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
		DirectorSpawns.applyVariant(zombie, runner.getValue(), level.getRandom());
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
				"the vanilla spawn weapon survived the variant: " + zombie.getItemBySlot(EquipmentSlot.MAINHAND));
		zombie.discard();
		helper.succeed();
	}

	@GameTest
	public void variantAttributesAndTagsApplied(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 0, 6, 0, 6);
		var runner = ZombieVariants.byName("runner");
		helper.assertTrue(runner != null, "runner variant not loaded from the datapack");
		Zombie zombie = DirectorSpawns.spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), runner,
				DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
		helper.assertTrue(zombie != null, "runner did not spawn");
		helper.assertValueEqual(zombie.getAttributeValue(Attributes.MAX_HEALTH), 14.0, "runner max health");
		helper.assertValueEqual(zombie.getHealth(), 14.0f, "runner health");
		helper.assertValueEqual(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.30, "runner speed");
		helper.assertValueEqual(zombie.getAttributeValue(Attributes.FOLLOW_RANGE), 48.0, "runner follow range");
		helper.assertTrue(!zombie.isBaby(), "variants are adults unless baby_chance says otherwise");
		helper.assertTrue(zombie.entityTags().contains("ashfall_variant=runner"), "missing variant tag: " + zombie.entityTags());
		helper.assertValueEqual(DirectorSpawns.tagOf(zombie), new DirectorZombie(DirectorZombie.Source.AMBIENT, "runner", 0), "attachment");
		helper.assertTrue(DirectorTracker.loaded().contains(zombie), "Director zombie not tracked");

		var armored = ZombieVariants.byName("armored");
		for (int i = 0; i < 10; i++) {
			Zombie a = DirectorSpawns.spawn(level, helper.absolutePos(new BlockPos(4, 1, 4)), armored,
					DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
			int pieces = 0;
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
				if (!a.getItemBySlot(slot).isEmpty()) {
					pieces++;
				}
			}
			helper.assertTrue(pieces <= 2, "armored zombie has " + pieces + " pieces");
			a.discard();
		}

		var patient = ZombieVariants.byName("patient");
		Zombie p = DirectorSpawns.spawn(level, helper.absolutePos(new BlockPos(5, 1, 2)), patient,
				DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
		helper.assertTrue(p != null && p.getType() == EntityTypes.ZOMBIE_VILLAGER, "patient should be a zombie villager");
		p.discard();
		zombie.discard();
		helper.succeed();
	}

	/**
	 * Building populations don't take a horde wave's room (10.3.1): a city base full of indoor zombies still gets its
	 * horde. Roaming zombies (ambient here) still count against the world cap.
	 */
	@GameTest
	public void buildingZombiesLeaveRoomForHordes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 0, 4, 0, 4);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		Vec3 at = Vec3.atCenterOf(pos);
		HordeSchedule.Spawn spawn = HordeSchedule.Spawn.DEFAULT;
		int roaming = DirectorTracker.total() - DirectorTracker.count(DirectorZombie.Source.BUILDING);
		List<Zombie> spawned = new ArrayList<>();
		try {
			for (int i = 0; i < 3; i++) {
				spawned.add(DirectorSpawns.spawn(level, pos, ZombieVariants.byName("walker"), DirectorZombie.Source.BUILDING, 0));
			}
			SpawnDirector.setWorldCapForTesting(roaming + 2);
			helper.assertValueEqual(HordeNights.room(level, at, spawn), 2, "horde room with 3 building zombies under a world cap of roaming+2");
			for (int i = 0; i < 2; i++) {
				spawned.add(DirectorSpawns.spawn(level, pos, ZombieVariants.byName("walker"), DirectorZombie.Source.AMBIENT, 0));
			}
			helper.assertValueEqual(HordeNights.room(level, at, spawn), 0, "horde room once roaming zombies reach the world cap");
		} finally {
			SpawnDirector.setWorldCapForTesting(null);
			spawned.forEach(z -> {
				if (z != null) {
					z.discard();
				}
			});
		}
		helper.succeed();
	}

	@GameTest
	public void capsRespected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 0, 4, 0, 4);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		int total = DirectorTracker.total();
		SpawnDirector.setWorldCapForTesting(total + 2);
		List<Zombie> spawned = new ArrayList<>();
		try {
			int room = SpawnDirector.allowance(level, pos.getX(), pos.getY(), pos.getZ());
			helper.assertTrue(room <= 2 && room > 0, "allowance under a world cap of total+2 was " + room);
			for (int i = 0; i < 2; i++) {
				spawned.add(DirectorSpawns.spawn(level, pos, ZombieVariants.byName("walker"), DirectorZombie.Source.AMBIENT, 0));
			}
			helper.assertValueEqual(SpawnDirector.allowance(level, pos.getX(), pos.getY(), pos.getZ()), 0, "allowance at the world cap");
		} finally {
			SpawnDirector.setWorldCapForTesting(null);
			spawned.forEach(z -> {
				if (z != null) {
					z.discard();
				}
			});
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void buildingPopulationSpawnsOnceStaysClearedThenResumes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 1, 4, 1, 4);
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
		long id = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
		Unit unit = new Unit(0, floorY, floorY + 2, "Home", footprint);
		// Tagged Downtown (scale 1, no gentle days) so the counts below do not depend on where the test runs.
		Building building = new Building(id, Map.of("building", "house", LostCitiesBuildingSource.TAG_ZONE, "downtown"),
				new int[]{floorY}, floorY, floorY + 3, footprint, List.of(unit));
		// 16 cells x 20 per 100 m2 = round(3.2) = 3 zombies
		BuildingProfile.Population population = new BuildingProfile.Population(20f, Map.of(ZombieVariant.ZOMBIE, 1));

		Runnable install = () -> BuildingPopulations.setOverridesForTesting(
				l -> l == id ? building : AshfallSalvage.buildings().get(l), (b, u) -> population);
		Runnable restore = () -> BuildingPopulations.setOverridesForTesting(null, null);

		List<Zombie> first = new ArrayList<>();
		install.run();
		try {
			helper.assertValueEqual(BuildingPopulations.populate(level, building), 3, "first population");
			helper.assertValueEqual(BuildingPopulations.populate(level, building), 0, "a building populates only once");
			BuildingRecord record = DirectorState.get(level.getServer()).building(id);
			helper.assertValueEqual(record.status(), BuildingRecord.Status.POPULATED, "status after spawning");
			for (UUID uuid : record.members()) {
				Zombie z = (Zombie) level.getEntity(uuid);
				helper.assertTrue(z != null, "member not in the level");
				helper.assertValueEqual(DirectorSpawns.tagOf(z).buildingId(), id, "member's building id");
				helper.assertTrue(z.requiresCustomPersistence(), "building zombie inside its building must not despawn");
				first.add(z);
			}
		} finally {
			restore.run();
		}
		first.forEach(z -> z.kill(level));

		// A POPULATED building whose members are nowhere loaded (while its chunks are) unsticks to CLEARED.
		long ghostId = id + 1;
		Building ghost = new Building(ghostId, Map.of("building", "house"), new int[]{floorY}, floorY, floorY + 3, footprint, List.of(unit));
		DirectorState ghostState = DirectorState.get(level.getServer());
		ghostState.getOrCreate(ghostId).populated(3, List.of(UUID.randomUUID()));
		helper.assertValueEqual(BuildingPopulations.populate(level, ghost), 0, "a reconciled building does not respawn at once");
		helper.assertValueEqual(ghostState.building(ghostId).status(), BuildingRecord.Status.CLEARED, "ghost members unstick the building");

		helper.runAfterDelay(45, () -> {
			BuildingRecord record = DirectorState.get(level.getServer()).building(id);
			helper.assertValueEqual(record.status(), BuildingRecord.Status.CLEARED, "status after all members died");
			install.run();
			List<Zombie> second = new ArrayList<>();
			try {
				helper.assertValueEqual(BuildingPopulations.populate(level, building), 0, "a cleared building stays cleared");
				record.tickCleared(true, SpawnRules.CLEARED_TICKS * 2, SpawnRules.CLEARED_TICKS);
				helper.assertValueEqual(record.status(), BuildingRecord.Status.CLEARED, "timer pauses while a player is near");
				record.tickCleared(false, SpawnRules.CLEARED_TICKS, SpawnRules.CLEARED_TICKS);
				helper.assertValueEqual(record.status(), BuildingRecord.Status.READY, "status after 7 quiet days");
				helper.assertValueEqual(BuildingPopulations.populate(level, building), 2, "repopulation is half the size");
				for (UUID uuid : record.members()) {
					if (level.getEntity(uuid) instanceof Zombie z) {
						second.add(z);
					}
				}
			} finally {
				restore.run();
				second.forEach(Zombie::discard);
			}
			helper.succeed();
		});
	}

	/**
	 * The zone scales a building's population (plan section 1.2): Mission x1.5, the wasteland none at all
	 * (darkness spawning only), Montford x0.6 during its gentle first days. Uses the shipped zone JSON, so
	 * this also checks that the data and the Director agree.
	 */
	@GameTest(maxTicks = 40)
	public void buildingPopulationFollowsZoneScale(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ZoneMap.current().byId("mission").isPresent(), "zone data is loaded");
		floor(helper, 1, 4, 1, 4);
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
		Unit unit = new Unit(0, floorY, floorY + 2, "Floor", footprint);
		long base = 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
		Map<Long, Building> byId = new java.util.HashMap<>();
		for (String zone : List.of("mission", "wasteland", "montford")) {
			long id = base + byId.size();
			byId.put(id, new Building(id, Map.of(LostCitiesBuildingSource.TAG_ZONE, zone), new int[]{floorY}, floorY, floorY + 3,
					footprint, List.of(unit)));
		}
		// 16 cells x 20 per 100 m2 = 3 before the zone.
		BuildingProfile.Population population = new BuildingProfile.Population(20f, Map.of(ZombieVariant.ZOMBIE, 1));
		BuildingPopulations.setOverridesForTesting(l -> byId.containsKey(l) ? byId.get(l) : AshfallSalvage.buildings().get(l),
				(b, u) -> population);
		List<UUID> spawned = new ArrayList<>();
		try {
			DirectorState state = DirectorState.get(level.getServer());
			boolean gentle = DirectorDay.today(level) <= 3;
			int[] expected = {5, 0, gentle ? 2 : 3};
			int i = 0;
			for (long id = base; id < base + 3; id++, i++) {
				Building building = byId.get(id);
				String zone = building.tags().get(LostCitiesBuildingSource.TAG_ZONE);
				helper.assertValueEqual(BuildingPopulations.populate(level, building), expected[i], zone + " population");
				BuildingRecord record = state.building(id);
				if (record != null) {
					spawned.addAll(record.members());
					BuildingPopulations.clear(level, id);
				}
			}
			helper.assertTrue(state.building(base + 1) == null, "a darkness-only zone never creates a population record");
		} finally {
			BuildingPopulations.setOverridesForTesting(null, null);
			for (UUID uuid : spawned) {
				if (level.getEntity(uuid) instanceof Zombie z) {
					z.discard();
				}
			}
		}
		helper.succeed();
	}
}
