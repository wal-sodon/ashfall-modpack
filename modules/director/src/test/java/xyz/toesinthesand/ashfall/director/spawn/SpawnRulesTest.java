package xyz.toesinthesand.ashfall.director.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The spec's light table, day damping, ambient cap curve, cap maths and population sizes. */
class SpawnRulesTest {
	@Test
	void lightTable() {
		assertEquals(1.0f, SpawnRules.lightWeight(0));
		for (int l = 1; l <= 4; l++) {
			assertEquals(0.6f, SpawnRules.lightWeight(l), "light " + l);
		}
		for (int l = 5; l <= 7; l++) {
			assertEquals(0.3f, SpawnRules.lightWeight(l), "light " + l);
		}
		for (int l = 8; l <= 11; l++) {
			assertEquals(0.1f, SpawnRules.lightWeight(l), "light " + l);
		}
		for (int l = 12; l <= 14; l++) {
			assertEquals(0.02f, SpawnRules.lightWeight(l), "light " + l);
		}
		assertEquals(0.0f, SpawnRules.lightWeight(15), "full light never spawns");
	}

	@Test
	void dayDampsOnlyOutdoors() {
		assertEquals(0.4f, SpawnRules.spotWeight(0, true, true), 1e-6);
		assertEquals(1.0f, SpawnRules.spotWeight(0, true, false), "dark interiors stay dangerous by day");
		assertEquals(1.0f, SpawnRules.spotWeight(0, false, true), "night outdoors is undamped");
	}

	@Test
	void dayCounter() {
		assertEquals(1, SpawnRules.day(0));
		assertEquals(1, SpawnRules.day(23_999));
		assertEquals(2, SpawnRules.day(24_000));
		assertEquals(3, SpawnRules.day(2 * 24_000 + 5));
	}

	@Test
	void ambientCapRisesLinearly() {
		assertEquals(30, SpawnRules.ambientCap(1));
		assertEquals(45, SpawnRules.ambientCap(60), "day 60 is about halfway");
		assertEquals(60, SpawnRules.ambientCap(120));
		assertEquals(60, SpawnRules.ambientCap(500));
		int last = 0;
		for (int d = 1; d <= 120; d++) {
			int cap = SpawnRules.ambientCap(d);
			assertEquals(true, cap >= last, "monotonic at day " + d);
			last = cap;
		}
		assertEquals(10, SpawnRules.ambientCap(1, 10, 20));
		assertEquals(20, SpawnRules.ambientCap(120, 10, 20));
	}

	@Test
	void allowanceIsTightestCap() {
		assertEquals(30, SpawnRules.allowance(0, 30, 0, 120, 0, 150));
		assertEquals(5, SpawnRules.allowance(25, 30, 0, 120, 0, 150), "ambient cap binds");
		assertEquals(2, SpawnRules.allowance(0, 30, 118, 120, 0, 150), "player hard cap binds");
		assertEquals(1, SpawnRules.allowance(0, 30, 0, 120, 149, 150), "world hard cap binds");
		assertEquals(0, SpawnRules.allowance(40, 30, 0, 120, 0, 150), "never negative");
		assertEquals(0, SpawnRules.hardCapRoom(0, 120, 150, 150));
		assertEquals(20, SpawnRules.hardCapRoom(100, 120, 0, 150), "building zombies skip the ambient cap");
	}

	@Test
	void populationCountAndHalving() {
		assertEquals(0, SpawnRules.basePopulation(0));
		assertEquals(3, SpawnRules.basePopulation(1.0 * 300));
		assertEquals(12, SpawnRules.basePopulation(1.2 * 5000), "capped at 12");
		assertEquals(12, SpawnRules.populationSize(12, 0));
		assertEquals(6, SpawnRules.populationSize(12, 1));
		assertEquals(3, SpawnRules.populationSize(12, 2));
		assertEquals(1, SpawnRules.populationSize(1, 5), "at least one while the base is non-zero");
		assertEquals(0, SpawnRules.populationSize(0, 1));
	}

	@Test
	void zoneScalesPopulation() {
		assertEquals(5, SpawnRules.scaledPopulation(3, 1.5), "Mission: most infested");
		assertEquals(2, SpawnRules.scaledPopulation(3, 0.6), "Montford's gentle days");
		assertEquals(1, SpawnRules.scaledPopulation(1, 0.3), "a positive scale keeps one zombie");
		assertEquals(0, SpawnRules.scaledPopulation(3, 0.0), "darkness-only zones have no populations");
		assertEquals(0, SpawnRules.scaledPopulation(0, 2.0));
	}

	@Test
	void zoneScalesDarkSpotsOnly() {
		assertEquals(0.39f, SpawnRules.zoneSpotWeight(0.3f, 6, 1.3f, 1f), 1e-6f, "darkness x1.3 in the dark");
		assertEquals(0.1f, SpawnRules.zoneSpotWeight(0.1f, 9, 1.3f, 1f), 1e-6f, "lit spots keep their weight");
		assertEquals(1.0f, SpawnRules.zoneSpotWeight(1.0f, 0, 1.3f, 1f), 1e-6f, "a probability never exceeds 1");
		assertEquals(0.6f, SpawnRules.zoneSpotWeight(1.0f, 0, 1f, 0.6f), 1e-6f, "gentle days damp every spot");
	}
}
