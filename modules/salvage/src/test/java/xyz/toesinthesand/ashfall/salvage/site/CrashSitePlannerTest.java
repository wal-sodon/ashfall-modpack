package xyz.toesinthesand.ashfall.salvage.site;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashSitePlannerTest {
	private static final Path RULES = Path.of("src/main/resources/data/ashfall/ashfall/crash_sites");

	private static CrashSiteRule rule(String zone, int region, float chance, long salt) {
		return new CrashSiteRule(List.of(zone), region, chance, salt,
				List.of(new CrashSiteRule.WeightedTemplate("ashfall:a", 3), new CrashSiteRule.WeightedTemplate("ashfall:b", 1)),
				new CrashSiteRule.Guards(1, 3, 6), 48);
	}

	/** The plan §1.2 layout in miniature: ridge wedge east of x 1700, wasteland ring beyond 1600. */
	private static String zoneAt(int x, int z) {
		if (x > 1700 && Math.abs(z) < 900) {
			return "ridge";
		}
		double r = Math.hypot(x, z);
		return r >= 1600 && r < 2600 ? "wasteland" : "downtown";
	}

	@Test
	void sameSeedSameSitesDifferentSeedDifferentSites() {
		Map<String, CrashSiteRule> rules = new TreeMap<>(Map.of("ashfall:ridge", rule("ridge", 10, 0.4f, 2300)));
		List<CrashSitePlanner.Planned> a = CrashSitePlanner.inBox(42L, rules, 1700, -900, 2600, 900, CrashSitePlannerTest::zoneAt,
				CrashSitePlanner.ChunkFilter.ANY);
		List<CrashSitePlanner.Planned> b = CrashSitePlanner.inBox(42L, rules, 1700, -900, 2600, 900, CrashSitePlannerTest::zoneAt,
				CrashSitePlanner.ChunkFilter.ANY);
		List<CrashSitePlanner.Planned> c = CrashSitePlanner.inBox(43L, rules, 1700, -900, 2600, 900, CrashSitePlannerTest::zoneAt,
				CrashSitePlanner.ChunkFilter.ANY);
		assertFalse(a.isEmpty(), "the ridge should hold some crash sites");
		assertEquals(a, b);
		assertNotEquals(a, c);
		for (CrashSitePlanner.Planned p : a) {
			assertEquals("ridge", zoneAt(p.centerX(), p.centerZ()), p.key());
		}
	}

	@Test
	void chanceRoughlyMatchesAndSitesKeepTheirDistance() {
		CrashSiteRule rule = rule("anything", 10, 0.4f, 7);
		int hits = 0, regions = 0;
		for (int rx = -30; rx < 30; rx++) {
			for (int rz = -30; rz < 30; rz++) {
				regions++;
				var site = CrashSitePlanner.roll(99L, "r", rule, rx, rz);
				if (site.isPresent()) {
					hits++;
					var p = site.get();
					// One chunk of margin inside the region on every side.
					assertTrue(Math.floorMod(p.chunkX(), 10) >= 1 && Math.floorMod(p.chunkX(), 10) <= 8, p.key());
					assertTrue(Math.floorMod(p.chunkZ(), 10) >= 1 && Math.floorMod(p.chunkZ(), 10) <= 8, p.key());
				}
			}
		}
		double rate = hits / (double) regions;
		assertTrue(rate > 0.35 && rate < 0.45, "rate " + rate);
	}

	@Test
	void nearAgreesWithBoxAndRespectsTheFilter() {
		Map<String, CrashSiteRule> rules = new TreeMap<>(Map.of("ashfall:wasteland", rule("wasteland", 16, 0.5f, 1600)));
		List<CrashSitePlanner.Planned> box = CrashSitePlanner.inBox(5L, rules, 1500, 1500, 2300, 2300, CrashSitePlannerTest::zoneAt,
				CrashSitePlanner.ChunkFilter.ANY);
		assertFalse(box.isEmpty());
		CrashSitePlanner.Planned first = box.getFirst();
		List<CrashSitePlanner.Planned> near = CrashSitePlanner.near(5L, rules, first.centerX(), first.centerZ(), 8,
				CrashSitePlannerTest::zoneAt, CrashSitePlanner.ChunkFilter.ANY);
		assertEquals(List.of(first), near);
		List<CrashSitePlanner.Planned> filtered = CrashSitePlanner.near(5L, rules, first.centerX(), first.centerZ(), 8,
				CrashSitePlannerTest::zoneAt, (cx, cz) -> !(cx == first.chunkX() && cz == first.chunkZ()));
		assertTrue(filtered.isEmpty(), "a filtered chunk must not get a site");
	}

	@Test
	void templatePickFollowsWeights() {
		CrashSiteRule rule = rule("x", 10, 1f, 0);
		assertEquals("ashfall:a", CrashSitePlanner.pickTemplate(rule, 0.0));
		assertEquals("ashfall:a", CrashSitePlanner.pickTemplate(rule, 0.74));
		assertEquals("ashfall:b", CrashSitePlanner.pickTemplate(rule, 0.76));
		assertEquals("ashfall:b", CrashSitePlanner.pickTemplate(rule, 0.9999));
	}

	@Test
	void shippedRulesParseAndCoverRidgeAndWasteland() throws IOException {
		Map<String, CrashSiteRule> rules = new TreeMap<>();
		try (var files = Files.list(RULES)) {
			for (Path file : files.toList()) {
				rules.put(file.getFileName().toString(), CrashSiteRule.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(file))).getOrThrow());
			}
		}
		assertTrue(rules.values().stream().anyMatch(r -> r.zones().contains("ridge")));
		assertTrue(rules.values().stream().anyMatch(r -> r.zones().contains("wasteland")));
		for (CrashSiteRule rule : rules.values()) {
			for (CrashSiteRule.WeightedTemplate t : rule.templates()) {
				Path template = Path.of("src/main/resources/data/ashfall/ashfall/site_template",
						t.template().substring(t.template().indexOf(':') + 1) + ".json");
				assertTrue(Files.exists(template), "missing template " + template);
			}
		}
	}
}
