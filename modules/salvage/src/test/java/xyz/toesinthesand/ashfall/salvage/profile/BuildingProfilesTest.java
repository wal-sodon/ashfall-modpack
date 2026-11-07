package xyz.toesinthesand.ashfall.salvage.profile;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.Unit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingProfilesTest {
	private static BuildingProfile parse(String json) {
		return BuildingProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	@AfterEach
	void reset() {
		BuildingProfiles.setForTesting(Map.of());
	}

	@Test
	void tagRulesBeatArnisUsesAndExactUseBeatsFamily() {
		BuildingProfiles.setForTesting(Map.of(
				id("police"), parse("{\"match\":[{\"amenity\":\"police\"}],\"priority\":50,\"tier\":4}"),
				id("pharmacy"), parse("{\"match\":[{\"amenity\":\"pharmacy\"},{\"shop\":\"chemist|pharmacy\"}],\"arnis_uses\":[\"Shop(Pharmacy)\"],\"tier\":3}"),
				id("shop"), parse("{\"arnis_uses\":[\"Shop\"],\"tier\":2}"),
				id("home"), parse("{\"arnis_uses\":[\"Home\"]}")));
		Footprint f = Footprint.of(List.of(new int[]{0, 0, 3}));

		Building police = new Building(1, Map.of("amenity", "police"), new int[]{0}, 0, 4, f, List.of());
		assertEquals(id("police"), BuildingProfiles.classify(police, new Unit(0, 0, 3, "Office", f)));

		Building plain = new Building(2, Map.of("building", "yes"), new int[]{0}, 0, 4, f, List.of());
		assertEquals(id("pharmacy"), BuildingProfiles.classify(plain, new Unit(0, 0, 3, "Shop(Pharmacy)", f)));
		assertEquals(id("shop"), BuildingProfiles.classify(plain, new Unit(0, 0, 3, "Shop(Books)", f)));
		assertEquals(id("home"), BuildingProfiles.classify(plain, new Unit(1, 4, 7, "Home", f)));
		assertEquals(BuildingProfiles.GENERIC, BuildingProfiles.classify(plain, new Unit(0, 0, 3, "Barn", f)));

		Building chemist = new Building(3, Map.of("shop", "chemist"), new int[]{0}, 0, 4, f, List.of());
		assertEquals(id("pharmacy"), BuildingProfiles.classify(chemist, new Unit(0, 0, 3, "Home", f)));
	}

	@Test
	void valuesAreGlobsWithAlternatives() {
		assertTrue(BuildingProfile.valueMatches("*:building?", "lcmt:building5"));
		assertTrue(BuildingProfile.valueMatches("*:building?", "lostcities:building1"));
		assertFalse(BuildingProfile.valueMatches("*:building?", "lcmt:building10"));
		assertFalse(BuildingProfile.valueMatches("*:building?", "lcmt:center/center00"));
		assertTrue(BuildingProfile.valueMatches("downtown|mission", "mission"));
		assertTrue(BuildingProfile.valueMatches("*gas_station*", "lcmt:highway/gas_station"));
		assertTrue(BuildingProfile.valueMatches("*", "anything"));
		assertTrue(BuildingProfile.valueMatches("police", "police"));
		assertFalse(BuildingProfile.valueMatches("police", "police_station"));
		assertFalse(BuildingProfile.valueMatches("*", null));
	}

	private static final Path PROFILES = Path.of("src/main/resources/data/ashfall/ashfall/building_profile");
	/** Modern Tweaks (Lights Off, AqUDSjNd) buildings at a multibuilding's origin, with the multibuilding id. */
	private static final String[][] LCMT_MULTI = {
			{"lcmt:center/center00", "lcmt:center"}, {"lcmt:factory/factory_00", "lcmt:factory"},
			{"lcmt:highway/restaurant_main", "lcmt:highway_restaurant"}, {"lcmt:building4", "lcmt:huge1"},
			{"lcmt:building5", "lcmt:huge2"}, {"lcmt:library/library00", "lcmt:library"}, {"lcmt:building1", "lcmt:multi1"},
			{"lcmt:building2", "lcmt:multi2"}, {"lcmt:building3", "lcmt:multi3"}, {"lcmt:building4", "lcmt:multi4"},
			{"lcmt:building5", "lcmt:multi5"}, {"lcmt:shopping/shopping_open00", "lcmt:shopping_open"},
			{"lcmt:shopping/shopping00", "lcmt:shopping"}, {"lcmt:townhall/town00", "lcmt:townhall"}};
	private static final List<String> CITY_ZONES = List.of("downtown", "river_arts", "south_slope", "mission", "montford",
			"suburbs", "wasteland");

	private static void loadShipped() throws IOException {
		Map<Identifier, BuildingProfile> loaded = new HashMap<>();
		try (Stream<Path> files = Files.list(PROFILES)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				loaded.put(id(f.getFileName().toString().replace(".json", "")), parse(Files.readString(f)));
			}
		}
		assertEquals(27, loaded.size(), "shipped profile count");
		BuildingProfiles.setForTesting(loaded);
	}

	private static Identifier lc(String building, String multi, String zone) {
		Map<String, String> tags = new HashMap<>();
		tags.put("lc_building", building);
		if (multi != null) {
			tags.put("lc_multibuilding", multi);
		}
		tags.put("lc_zone", zone);
		Footprint f = Footprint.of(List.of(new int[]{0, 0, 15}));
		return BuildingProfiles.classifyByTags(new Building(9, tags, new int[]{70}, 70, 76, f, List.of()));
	}

	@Test
	void shippedProfilesPlaceModernTweaksBuildingsByZone() throws IOException {
		loadShipped();
		assertEquals(id("office"), lc("lcmt:building5", null, "downtown"));
		assertEquals(id("hotel"), lc("lcmt:building8", null, "downtown"));
		assertEquals(id("bank"), lc("lcmt:building6", null, "downtown"));
		assertEquals(id("home"), lc("lcmt:building1", null, "montford"));
		assertEquals(id("home"), lc("lostcities:building3", null, "suburbs"));
		assertEquals(id("warehouse"), lc("lcmt:building1", null, "river_arts"));
		assertEquals(id("bar"), lc("lcmt:building2", null, "south_slope"));
		assertEquals(id("clinic"), lc("lcmt:building5", null, "mission"));
		assertEquals(id("hospital"), lc("lcmt:building4", "lcmt:huge1", "mission"));
		assertEquals(id("office"), lc("lcmt:building4", "lcmt:huge1", "suburbs"));
		assertEquals(id("electronics"), lc("lcmt:center/center00", "lcmt:center", "downtown"));
		assertEquals(id("government"), lc("lcmt:townhall/town00", "lcmt:townhall", "suburbs"));
		assertEquals(id("school"), lc("lcmt:library/library00", "lcmt:library", "montford"));
		assertEquals(id("workshop"), lc("lcmt:factory/factory_00", "lcmt:factory", "wasteland"));
		assertEquals(id("grocery"), lc("lcmt:shopping/shopping00", "lcmt:shopping", "downtown"));
		assertEquals(id("fuel"), lc("lcmt:highway/gas_station", null, "wasteland"));
		assertEquals(id("restaurant"), lc("lcmt:highway/restaurant_main", "lcmt:highway_restaurant", "wasteland"));
		assertEquals(id("home"), lc("lcmt:cabin", null, "wasteland"));
		// Landmarks authored later already have their profiles.
		assertEquals(id("police_armory"), lc("ashfall:police_station/p00", "ashfall:police_station", "downtown"));
		// Random police stations: the same rooms, the plain police profile (no armory cache), in any district.
		assertEquals(id("police"), lc("ashfall:police_precinct_00", "ashfall:police_precinct", "montford"));
		assertEquals(id("police"), lc("ashfall:police_precinct_10", "ashfall:police_precinct", "river_arts"));
		assertEquals(id("hospital"), lc("ashfall:hospital/h00", "ashfall:hospital", "mission"));
		assertEquals(id("pharmacy"), lc("ashfall:pharmacy", null, "suburbs"));
		// The playtest found these on the generic profile: the spawn safehouse then got a zombie population.
		assertEquals(id("safehouse"), lc("ashfall:safehouse", null, "montford"));
		assertEquals(id("workshop"), lc("ashfall:substation_downtown_00", "ashfall:substation_downtown", "downtown"));
		assertEquals(id("workshop"), lc("ashfall:substation_mission_11", "ashfall:substation_mission", "mission"));
		assertEquals(id("warehouse"), lc("ashfall:kiln_warehouse_00", "ashfall:kiln_warehouse", "river_arts"));
	}

	/** The spawn building must never be populated or hand out loot; the Field Radio cache is placed separately. */
	@Test
	void safehouseIsEmptyAndSafe() throws IOException {
		loadShipped();
		BuildingProfile safehouse = BuildingProfiles.get(id("safehouse"));
		assertNotNull(safehouse);
		assertEquals(0f, safehouse.population().per100m2());
		assertEquals(0, safehouse.containers().max());
		assertTrue(safehouse.safeCandidate());
	}

	@Test
	void everyModernTweaksBuildingHasAProfileInEveryCityZone() throws IOException {
		loadShipped();
		for (String zone : CITY_ZONES) {
			for (int n = 1; n <= 8; n++) {
				assertNotNull(lc("lcmt:building" + n, null, zone), "lcmt:building" + n + " in " + zone);
			}
			for (String[] multi : LCMT_MULTI) {
				assertNotNull(lc(multi[0], multi[1], zone), multi[1] + " in " + zone);
			}
		}
	}
}
