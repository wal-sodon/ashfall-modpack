package xyz.toesinthesand.ashfall.salvage.test;

import mcjty.lostcities.LostCities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.LandmarkCensus;
import xyz.toesinthesand.ashfall.salvage.city.Landmarks;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;

import java.util.Optional;

/**
 * The landmark assets generate on a real 26.3 server with The Lost Cities: each one is planned at its
 * predefined chunk, and once generated it has its loot, no ladders below the roof, no light sources,
 * and (substations) exactly one Substation Core marker.
 * <p>
 * The game's own predefined cities pin the landmarks in the overworld, which is flat in gametests, so
 * this test mod ships a predefined city with all nine landmarks (and one random police precinct) in {@code ashfall_test:lc_city} (see
 * {@code data/ashfall_test/lostcities/predefinedcities/landmarks.json}), centred on chunk (60, -60) in the
 * suburbs zone, away from the Downtown chunks {@link CityZoneGameTest} samples.
 */
public class LandmarkGameTest {
	private static final ResourceKey<Level> LC_CITY = ResourceKey.create(Registries.DIMENSION,
			Identifier.fromNamespaceAndPath("ashfall_test", "lc_city"));
	private static final int CX = 60, CZ = -60;
	private static final int SETTLE_TICKS = 20;

	@GameTest(maxTicks = 1200)
	public void safehouseAndPharmacy(GameTestHelper helper) {
		ServerLevel level = level(helper);
		generate(helper, level, "ashfall:safehouse", 0, 0, 1, 1);
		generate(helper, level, "ashfall:pharmacy", 2, 0, 1, 1);
		// Count a moment later: chunk-load listeners (LandmarkCleanup) run as the chunks reach the level.
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			LandmarkCensus safehouse = census(helper, level, "ashfall:safehouse", 0, 0, 1, 1);
			helper.assertTrue(safehouse.beds() >= 10, "safehouse has only " + safehouse.beds() + " bed blocks");
			helper.assertTrue(safehouse.lootTables().getOrDefault("ashfall:buildings/home", 0) >= 6, "safehouse storage: " + safehouse.lootTables());
			helper.assertTrue(safehouse.lootTables().getOrDefault("ashfall_cache:field_radio", 0) == 1, "safehouse Field Radio cache: " + safehouse.lootTables());
			LandmarkCensus pharmacy = census(helper, level, "ashfall:pharmacy", 2, 0, 1, 1);
			helper.assertTrue(pharmacy.lootTables().getOrDefault("ashfall:buildings/pharmacy", 0) >= 6, "pharmacy stock: " + pharmacy.lootTables());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 1200)
	public void policeAndHospitalCaches(GameTestHelper helper) {
		ServerLevel level = level(helper);
		generate(helper, level, "ashfall:police_station_00", 4, 0, 2, 1);
		generate(helper, level, "ashfall:hospital_00", 0, 3, 3, 2);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			LandmarkCensus police = census(helper, level, "ashfall:police_station_00", 4, 0, 2, 1);
			helper.assertTrue(police.lootTables().getOrDefault("ashfall_cache:police_armory", 0) == 1, "police armory cache: " + police.lootTables());
			LandmarkCensus hospital = census(helper, level, "ashfall:hospital_00", 0, 3, 3, 2);
			helper.assertTrue(hospital.lootTables().getOrDefault("ashfall_cache:hospital_supply", 0) == 1, "hospital supply cache: " + hospital.lootTables());
			helper.assertTrue(hospital.beds() >= 60, "hospital has only " + hospital.beds() + " bed blocks");
			CityChunk plan = LostCityContext.chunk(level, CX, CZ + 3).orElseThrow();
			helper.assertTrue(plan.cellars() == 2 && plan.floors() == 3, "hospital is " + plan.floors() + " floors, " + plan.cellars() + " cellars");
			helper.succeed();
		});
	}

	/** A random police station: the landmark's rooms under other ids, so it has police loot but no armory cache. */
	@GameTest(maxTicks = 1200)
	public void policePrecinctHasNoArmory(GameTestHelper helper) {
		ServerLevel level = level(helper);
		int cx = CX + 2, cz = CZ + 2;
		CityChunk plan = LostCityContext.chunk(level, cx, cz).orElseThrow();
		helper.assertTrue("ashfall:police_precinct_00".equals(plan.buildingId()), "chunk plans " + plan.buildingId());
		helper.assertTrue(!Landmarks.isLandmarkBuilding(plan.buildingId()), "a precinct counts as a landmark");
		for (int x = cx - 1; x <= cx + 2; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				level.getChunk(x, z, ChunkStatus.FULL, true);
			}
		}
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			// Its profile (police, not police_armory) is checked in BuildingProfilesTest; here, what it generates.
			int ground = plan.groundY();
			LandmarkCensus c = LandmarkCensus.scan(level, cx << 4, ground - plan.cellars() * 6, cz << 4,
					(cx << 4) + 31, ground + plan.floors() * 6 + 12, (cz << 4) + 15);
			helper.assertTrue(c.lootTables().getOrDefault("ashfall:buildings/police", 0) >= 1, "precinct loot: " + c.lootTables());
			helper.assertTrue(!c.lootTables().containsKey("ashfall_cache:police_armory"), "precinct has an armory cache: " + c.lootTables());
			helper.succeed();
		});
	}

	private static final String[][] SUBSTATIONS = {{"ashfall:substation_downtown_00", "7", "0"}, {"ashfall:substation_river_arts_00", "7", "3"},
			{"ashfall:substation_south_slope_00", "0", "6"}, {"ashfall:substation_mission_00", "3", "6"}};

	@GameTest(maxTicks = 1200)
	public void substationsAndWarehouse(GameTestHelper helper) {
		ServerLevel level = level(helper);
		for (String[] s : SUBSTATIONS) {
			generate(helper, level, s[0], Integer.parseInt(s[1]), Integer.parseInt(s[2]), 2, 2);
		}
		generate(helper, level, "ashfall:kiln_warehouse_00", 4, 3, 2, 2);
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			for (String[] s : SUBSTATIONS) {
				LandmarkCensus c = census(helper, level, s[0], Integer.parseInt(s[1]), Integer.parseInt(s[2]), 2, 2);
				helper.assertTrue(c.cores() == 1, s[0] + " has " + c.cores() + " Substation Core markers");
				helper.assertTrue(c.deadLamps() >= 2, s[0] + " has " + c.deadLamps() + " dead lamps");
			}
			LandmarkCensus warehouse = census(helper, level, "ashfall:kiln_warehouse_00", 4, 3, 2, 2);
			helper.assertTrue(warehouse.lootTables().getOrDefault("ashfall_cache:kiln_office", 0) == 1, "Kiln & Co. office cache: " + warehouse.lootTables());
			helper.succeed();
		});
	}

	private static ServerLevel level(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(LC_CITY);
		helper.assertTrue(level != null, "test dimension ashfall_test:lc_city is missing");
		LostCities.lostCitiesImp.registerDimension(level, LC_CITY, LostCityContext.PROFILE);
		return level;
	}

	/** Checks the plan of one landmark whose north-west chunk is (CX + dx, CZ + dz) and generates it. */
	private static void generate(GameTestHelper helper, ServerLevel level, String building, int dx, int dz, int w, int h) {
		int cx = CX + dx, cz = CZ + dz;
		Optional<CityChunk> plan = LostCityContext.chunk(level, cx, cz);
		helper.assertTrue(plan.isPresent() && building.equals(plan.get().buildingId()),
				"chunk " + cx + ", " + cz + " plans " + plan.map(CityChunk::buildingId).orElse("nothing") + ", expected " + building);
		helper.assertTrue(Landmarks.isLandmarkBuilding(building), building + " is not listed in Landmarks.ALL");
		helper.assertTrue(plan.get().ruinLevel() < 0, building + " is ruined at y " + plan.get().ruinLevel());
		// The footprint plus a ring around it, so Lost Cities' post-generation todo list (loot, block
		// entities) has run for every chunk we count.
		for (int x = cx - 1; x <= cx + w; x++) {
			for (int z = cz - 1; z <= cz + h; z++) {
				level.getChunk(x, z, ChunkStatus.FULL, true);
			}
		}
	}

	/** Counts a generated landmark and checks what every landmark must satisfy. */
	private static LandmarkCensus census(GameTestHelper helper, ServerLevel level, String building, int dx, int dz, int w, int h) {
		int cx = CX + dx, cz = CZ + dz;
		CityChunk plan = LostCityContext.chunk(level, cx, cz).orElseThrow();
		int ground = plan.groundY();
		int bottom = ground - plan.cellars() * 6;
		int roof = ground + plan.floors() * 6;
		int x0 = cx << 4, z0 = cz << 4, x1 = x0 + w * 16 - 1, z1 = z0 + h * 16 - 1;
		LandmarkCensus all = LandmarkCensus.scan(level, x0, bottom, z0, x1, roof + 12, z1);
		LandmarkCensus inside = LandmarkCensus.scan(level, x0, bottom, z0, x1, roof - 1, z1);
		helper.assertTrue(inside.ladders() == 0, building + " has " + inside.ladders() + " ladders below the roof");
		helper.assertTrue(all.lights() == 0, building + " has light-emitting blocks: " + all.lightBlocks());
		helper.assertTrue(inside.stairs() >= 6, building + " has only " + inside.stairs() + " stair blocks");
		helper.assertTrue(inside.doors() >= 2, building + " has only " + inside.doors() + " door blocks");
		helper.assertTrue(!all.lootTables().isEmpty(), building + " has no loot containers");
		return all;
	}
}
