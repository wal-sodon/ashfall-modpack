package xyz.toesinthesand.ashfall.director.senses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightRulesTest {
	@Test
	void nightThresholdIsSeven() {
		assertEquals(0.0, LightRules.radius(6, true, false));
		assertEquals(28.0, LightRules.radius(7, true, false));
		assertEquals(56.0, LightRules.radius(14, true, true), "a held torch at night");
	}

	@Test
	void daylightOutdoorsHidesEverything() {
		assertEquals(0.0, LightRules.radius(15, false, false));
	}

	@Test
	void brightInteriorsShowByDayAtThirtyPercent() {
		assertEquals(0.0, LightRules.radius(9, false, true));
		assertEquals(10 * 4.0 * 0.3, LightRules.radius(10, false, true), 1e-9);
		assertEquals(15 * 4.0 * 0.3, LightRules.radius(15, false, true), 1e-9);
	}
}
