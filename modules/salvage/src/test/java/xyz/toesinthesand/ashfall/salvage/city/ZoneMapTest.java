package xyz.toesinthesand.ashfall.salvage.city;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zone math over the zone JSON we actually ship, decoded with the same codec the server uses. Catches
 * typos in the data (a disc in the wrong place, a missing city style) without booting a server.
 */
class ZoneMapTest {
	private static final Path ZONES = Path.of("src/main/resources/data/ashfall/ashfall/zones");
	private static ZoneMap map;

	@BeforeAll
	static void load() throws IOException {
		List<Zone> zones = new ArrayList<>();
		try (Stream<Path> files = Files.list(ZONES)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				String id = f.getFileName().toString().replace(".json", "");
				Zone zone = Zone.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f))).getOrThrow();
				assertEquals(id, zone.id(), "the id field must match the file name in " + f);
				zones.add(zone.withId(id));
			}
		}
		map = new ZoneMap(zones);
	}

	@AfterEach
	void reset() {
		ZoneMap.setCurrent(null);
	}

	private static String at(int x, int z) {
		return map.find(x, z).id();
	}

	@Test
	void districtCentresResolveToTheirDistrict() {
		assertEquals("downtown", at(0, 0));
		assertEquals("river_arts", at(-900, 100));
		assertEquals("south_slope", at(100, 800));
		assertEquals("mission", at(750, 650));
		assertEquals("montford", at(-150, -800));
	}

	@Test
	void everyDistrictChunkIsForcedCityButNotTheRest() {
		ZoneMap.setCurrent(map);
		assertTrue(CityZoneRules.forcesCity(0, 0), "Downtown centre");
		assertTrue(CityZoneRules.forcesCity(4, -1), "the Downtown substation chunk");
		assertTrue(CityZoneRules.forcesCity(-900 >> 4, 100 >> 4), "River Arts");
		assertTrue(CityZoneRules.forcesCity(100 >> 4, 800 >> 4), "South Slope");
		assertTrue(CityZoneRules.forcesCity(750 >> 4, 650 >> 4), "Mission");
		assertFalse(CityZoneRules.forcesCity(-150 >> 4, -800 >> 4), "Montford keeps Lost Cities' own mix");
		assertFalse(CityZoneRules.forcesCity(1000 >> 4, -1000 >> 4), "suburbs");
		assertFalse(CityZoneRules.forcesCity(2300 >> 4, -100 >> 4), "the ridge is wilderness");
	}

	@Test
	void villagesAreKeptOutOfDistrictsAndTheirEdges() {
		ZoneMap.setCurrent(map);
		assertTrue(CityZoneRules.nearDistrict(0, 0), "Downtown centre");
		assertTrue(CityZoneRules.nearDistrict(400 / 16 + 4, 0), "just outside the Downtown disc (r 400)");
		assertFalse(CityZoneRules.nearDistrict(1000 >> 4, -1000 >> 4), "suburbs far from any district");
		assertFalse(CityZoneRules.nearDistrict(3000 >> 4, 0), "undiscovered");
	}

	@Test
	void ringsAndFallbacks() {
		assertEquals("suburbs", at(1000, -1000));       // ~1414 from origin, no district
		assertEquals("wasteland", at(-2000, 1000));     // ~2236
		assertEquals("ridge", at(2300, -100));          // the Wreck hull
		assertEquals("undiscovered", at(3000, 0));
		assertEquals("undiscovered", at(-5000, 5000));
	}

	@Test
	void borders() {
		assertEquals("downtown", at(399, 0));
		assertEquals("suburbs", at(400, 0));            // disc radius is exclusive
		assertEquals("suburbs", at(1599, 0));
		assertEquals("wasteland", at(1600, 0));
		assertEquals("wasteland", at(1699, 0));
		assertEquals("ridge", at(1700, 0));             // the ridge wedge beats the wasteland ring
		assertEquals("wasteland", at(1800, 1000));      // outside the wedge's |z| < 900
		assertEquals("undiscovered", at(0, 2600));
	}

	@Test
	void districtsDoNotOverlap() {
		List<Zone> discs = map.zones().stream().filter(z -> z.priority() == 100).toList();
		for (int i = 0; i < discs.size(); i++) {
			for (int j = i + 1; j < discs.size(); j++) {
				ZoneShape.Disc a = (ZoneShape.Disc) discs.get(i).shape();
				ZoneShape.Disc b = (ZoneShape.Disc) discs.get(j).shape();
				double d = Math.hypot(a.x() - b.x(), a.z() - b.z());
				assertTrue(d >= a.r() + b.r(), discs.get(i).id() + " overlaps " + discs.get(j).id());
			}
		}
	}

	@Test
	void cityZonesHaveAshfallStylesAndWildZonesNone() {
		for (Zone z : map.zones()) {
			if (z.city()) {
				assertTrue(z.cityStyle().orElse("").startsWith("ashfall:"), z.id() + " needs an ashfall city style");
			} else {
				assertTrue(z.cityStyle().isEmpty(), z.id() + " is wilderness but names a city style");
			}
		}
		assertEquals(4, map.zones().stream().filter(Zone::isDistrict).count());
	}

	@Test
	void chunkLookupUsesTheChunkCentre() {
		ZoneMap.setCurrent(map);
		// Chunk 24 spans x 384..399: its centre (392) is inside Downtown's 400 radius.
		assertEquals("downtown", ZoneMap.zoneAtChunk(24, 0).id());
		assertEquals("suburbs", ZoneMap.zoneAtChunk(25, 0).id());
	}

	@Test
	void rulesFollowTheLoadedMap() {
		ZoneMap.setCurrent(map);
		assertTrue(CityZoneRules.allowsCity(0, 0));
		assertFalse(CityZoneRules.allowsCity(2300 >> 4, -100 >> 4));
		assertFalse(CityZoneRules.allowsCity(3000 >> 4, 0));
		assertEquals("ashfall:downtown", CityZoneRules.cityStyle(0, 0).orElseThrow());
		assertEquals("ashfall:wasteland", CityZoneRules.cityStyle(-2000 >> 4, 1000 >> 4).orElseThrow());
		assertEquals(0.06f, CityZoneRules.ruinChance(0, 0, 0.12f), 1e-6);
		assertEquals(0.45f, CityZoneRules.ruinChance(-2000 >> 4, 1000 >> 4, 0.12f), 1e-6);
	}

	@Test
	void withoutDataTheRulesLeaveLostCitiesAlone() {
		ZoneMap.setCurrent(null);
		assertTrue(CityZoneRules.allowsCity(1000, 1000));
		assertTrue(CityZoneRules.cityStyle(0, 0).isEmpty());
		assertEquals(0.12f, CityZoneRules.ruinChance(0, 0, 0.12f), 1e-6);
	}
}
