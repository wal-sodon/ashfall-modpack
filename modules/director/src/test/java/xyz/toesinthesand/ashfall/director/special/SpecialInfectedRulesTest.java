package xyz.toesinthesand.ashfall.director.special;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Special infected picks (per-mille weights, min_day, zones, caps, siege multiplier) and the shipped files. */
class SpecialInfectedRulesTest {
	private static final Path DIR = Path.of("src/main/resources/data/ashfall/ashfall/special_infected");

	private static SpecialInfectedRule rule(int weight, int minDay, Map<String, Float> zones, float siege) {
		return new SpecialInfectedRule(weight, minDay, zones, 2, siege, Map.of(), Map.of());
	}

	private static Map<SpecialKind, SpecialInfectedRule> table() {
		Map<SpecialKind, SpecialInfectedRule> m = new EnumMap<>(SpecialKind.class);
		m.put(SpecialKind.BLOATED, rule(20, 3, Map.of("mission", 2.5f), 1.5f));
		m.put(SpecialKind.BRUTE, rule(10, 8, Map.of("wasteland", 3f, "montford", 0f), 2f));
		return m;
	}

	/** Share of a fine uniform grid of rolls that lands on {@code kind}. */
	private static double share(Map<SpecialKind, SpecialInfectedRule> m, SpecialKind kind, int day, String zone, boolean siege,
								java.util.function.Predicate<SpecialKind> cap) {
		int hits = 0, n = 100_000;
		for (int i = 0; i < n; i++) {
			if (SpecialInfectedRules.pick(m, (i + 0.5) / n, day, zone, siege, cap) == kind) {
				hits++;
			}
		}
		return hits / (double) n;
	}

	@Test
	void weightsArePerMille() {
		assertEquals(0.020, share(table(), SpecialKind.BLOATED, 10, "downtown", false, k -> true), 1e-4);
		assertEquals(0.010, share(table(), SpecialKind.BRUTE, 10, "downtown", false, k -> true), 1e-4);
		// most rolls are no special at all
		assertNull(SpecialInfectedRules.pick(table(), 0.5, 10, "downtown", false, k -> true));
	}

	@Test
	void minDayZoneAndSiegeMultiply() {
		assertEquals(0.0, share(table(), SpecialKind.BRUTE, 7, "wasteland", false, k -> true));
		assertEquals(0.030, share(table(), SpecialKind.BRUTE, 8, "wasteland", false, k -> true), 1e-4);
		assertEquals(0.0, share(table(), SpecialKind.BRUTE, 20, "montford", false, k -> true));
		assertEquals(0.050, share(table(), SpecialKind.BLOATED, 5, "mission", false, k -> true), 1e-4);
		assertEquals(0.075, share(table(), SpecialKind.BLOATED, 5, "mission", true, k -> true), 1e-4);
	}

	@Test
	void cappedKindIsSkippedWithoutChangingOthers() {
		var cap = (java.util.function.Predicate<SpecialKind>) k -> k != SpecialKind.BLOATED;
		assertEquals(0.0, share(table(), SpecialKind.BLOATED, 10, "downtown", false, cap));
		assertEquals(0.010, share(table(), SpecialKind.BRUTE, 10, "downtown", false, cap), 1e-4);
	}

	@Test
	void capScalesWithPlayers() {
		SpecialInfectedRule screamer = new SpecialInfectedRule(8, 5, Map.of(), 2, 1f, Map.of(), Map.of());
		assertTrue(screamer.underCap(1, 0));
		assertTrue(!screamer.underCap(2, 1));
		assertTrue(screamer.underCap(3, 2));
		assertTrue(!screamer.underCap(4, 2));
	}

	@Test
	void shippedFilesParseAndCoverEveryKind() throws IOException {
		Map<SpecialKind, SpecialInfectedRule> loaded = new EnumMap<>(SpecialKind.class);
		for (SpecialKind kind : SpecialKind.values()) {
			Path file = DIR.resolve(kind.id() + ".json");
			assertTrue(Files.exists(file), "missing " + file);
			loaded.put(kind, SpecialInfectedRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow());
		}
		SpecialInfectedRule brute = loaded.get(SpecialKind.BRUTE);
		assertEquals(60.0, brute.attributes().get(Identifier.withDefaultNamespace("max_health")));
		assertEquals(1.6, brute.attributes().get(Identifier.withDefaultNamespace("scale")));
		assertEquals(2, loaded.get(SpecialKind.SCREAMER).maxAlivePerPlayer(), "at most 2 screamers per player");
		assertTrue(loaded.get(SpecialKind.BLOATED).zoneMultiplier().get("mission") > 1f, "Mission breeds bloated");
		assertTrue(loaded.get(SpecialKind.SPITTER).zoneMultiplier().get("mission") > 1f, "Mission breeds spitters");
		assertTrue(loaded.get(SpecialKind.BRUTE).zoneMultiplier().get("wasteland") > 1f, "the wasteland breeds brutes");
		// even the worst zone on a siege night leaves most spawns ordinary zombies
		for (String zone : new String[]{"mission", "wasteland", "downtown", "montford", "ridge"}) {
			double total = 0;
			for (SpecialInfectedRule r : loaded.values()) {
				total += r.weightFor(100, zone, r.siegeMultiplier());
			}
			assertTrue(total < SpecialInfectedRule.PER * 0.4, zone + " siege special share " + total);
		}
		assertNotNull(SpecialKind.byId("stalker"));
	}
}
