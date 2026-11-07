package xyz.toesinthesand.ashfall.director.spawn;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Weighted variant picks: min_day, environment and building bias. */
class ZombieVariantsTest {
	private static ZombieVariant variant(int weight, int minDay, ZombieVariant.Environment env, Map<Identifier, Float> bias) {
		return new ZombieVariant(ZombieVariant.ZOMBIE, weight, minDay, env, 0f, Map.of(), ZombieVariant.Equipment.NONE, bias);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static Map<Identifier, ZombieVariant> standard() {
		Map<Identifier, ZombieVariant> m = new TreeMap<>();
		m.put(id("armored"), variant(10, 7, ZombieVariant.Environment.LAND, Map.of(id("police"), 4.0f)));
		m.put(id("drowned"), variant(100, 1, ZombieVariant.Environment.WATER, Map.of()));
		m.put(id("runner"), variant(20, 3, ZombieVariant.Environment.LAND, Map.of()));
		m.put(id("walker"), variant(70, 1, ZombieVariant.Environment.LAND, Map.of()));
		return m;
	}

	/** Share of rolls (on a fine uniform grid) that land on {@code name}. */
	private static double share(Map<Identifier, ZombieVariant> m, String name, int day, ZombieVariant.Environment env, Identifier profile) {
		int hits = 0, n = 10_000;
		for (int i = 0; i < n; i++) {
			var e = ZombieVariants.pick(m, (i + 0.5) / n, day, env, profile);
			if (e != null && e.getKey().getPath().equals(name)) {
				hits++;
			}
		}
		return hits / (double) n;
	}

	@Test
	void dayOneIsAllWalkers() {
		assertEquals(1.0, share(standard(), "walker", 1, ZombieVariant.Environment.LAND, null), 1e-9);
	}

	@Test
	void fullTableFromDaySeven() {
		Map<Identifier, ZombieVariant> m = standard();
		assertEquals(0.70, share(m, "walker", 7, ZombieVariant.Environment.LAND, null), 1e-3);
		assertEquals(0.20, share(m, "runner", 7, ZombieVariant.Environment.LAND, null), 1e-3);
		assertEquals(0.10, share(m, "armored", 7, ZombieVariant.Environment.LAND, null), 1e-3);
	}

	@Test
	void policeBiasesArmored() {
		assertEquals(40.0 / 130.0, share(standard(), "armored", 7, ZombieVariant.Environment.LAND, id("police")), 1e-3);
	}

	@Test
	void waterOnlyDrowned() {
		assertEquals(1.0, share(standard(), "drowned", 1, ZombieVariant.Environment.WATER, null), 1e-9);
		assertEquals(0.0, share(standard(), "drowned", 1, ZombieVariant.Environment.HOT, null), 1e-9);
	}

	@Test
	void nothingEligible() {
		assertNull(ZombieVariants.pick(Map.of(), 0.5, 1, ZombieVariant.Environment.LAND, null));
	}

	@Test
	void conditionsFilterBySpawnContext() {
		Map<Identifier, ZombieVariant> m = new TreeMap<>(standard());
		ZombieVariant.Conditions streetsOfMission = new ZombieVariant.Conditions(List.of("mission"), Optional.empty(),
				Optional.of(true), List.of());
		m.put(id("crawler"), new ZombieVariant(ZombieVariant.ZOMBIE, 90, 1, ZombieVariant.Environment.LAND, 0f, Map.of(),
				ZombieVariant.Equipment.NONE, Map.of(), streetsOfMission, Map.of()));
		SpawnContext missionStreet = new SpawnContext("mission", false, true, null);
		SpawnContext missionHouse = new SpawnContext("mission", true, false, "lostcities:building1");
		SpawnContext downtownStreet = new SpawnContext("downtown", false, true, null);
		assertEquals(90.0 / 160.0, share(m, "crawler", 1, missionStreet), 1e-3);
		assertEquals(0.0, share(m, "crawler", 1, missionHouse), 1e-9, "on_street fails inside a building");
		assertEquals(0.0, share(m, "crawler", 1, downtownStreet), 1e-9, "zones fails elsewhere");
		assertEquals(0.0, share(m, "crawler", 1, SpawnContext.NONE), 1e-9, "no context never meets a condition");
	}

	@Test
	void zoneBiasWeightsByZone() {
		Map<Identifier, ZombieVariant> m = new TreeMap<>(standard());
		m.put(id("patient"), new ZombieVariant(ZombieVariant.ZOMBIE, 10, 1, ZombieVariant.Environment.LAND, 0f, Map.of(),
				ZombieVariant.Equipment.NONE, Map.of(), ZombieVariant.Conditions.ANY, Map.of("mission", 5.0f)));
		assertEquals(50.0 / 120.0, share(m, "patient", 1, new SpawnContext("mission", true, false, null)), 1e-3);
		assertEquals(10.0 / 80.0, share(m, "patient", 1, new SpawnContext("downtown", true, false, null)), 1e-3);
	}

	private static double share(Map<Identifier, ZombieVariant> m, String name, int day, SpawnContext ctx) {
		int hits = 0, n = 10_000;
		for (int i = 0; i < n; i++) {
			var e = ZombieVariants.pick(m, (i + 0.5) / n, day, ZombieVariant.Environment.LAND, null, ctx);
			if (e != null && e.getKey().getPath().equals(name)) {
				hits++;
			}
		}
		return hits / (double) n;
	}
}
