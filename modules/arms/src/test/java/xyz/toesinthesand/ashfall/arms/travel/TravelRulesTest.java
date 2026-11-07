package xyz.toesinthesand.ashfall.arms.travel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Trip cost by distance and which Cell Packs pay for it. */
class TravelRulesTest {
	private static final TravelRules RULES = TravelRules.DEFAULT;

	@Test
	void costGrowsWithDistanceUpToTheCap() {
		assertEquals(10, RULES.cost(0));
		assertEquals(15, RULES.cost(80));
		assertEquals(25, RULES.cost(300));
		assertEquals(40, RULES.cost(600));
		assertEquals(40, RULES.cost(5_000));
		assertEquals(40, RULES.cost(-1), "another dimension costs the most");
	}

	@Test
	void leavingABunkerCostsDouble() {
		assertEquals(25, RULES.cost(300, false));
		assertEquals(50, RULES.cost(300, true));
		assertEquals(80, RULES.cost(5_000, true));
	}

	@Test
	void emptiestPacksPayFirst() {
		assertArrayEquals(new long[]{0, 5, 20}, TravelRules.drawPlan(new long[]{60, 5, 30}, 25));
		assertArrayEquals(new long[]{25}, TravelRules.drawPlan(new long[]{25}, 25));
		assertArrayEquals(new long[]{0, 0}, TravelRules.drawPlan(new long[]{0, 10}, 0));
	}

	@Test
	void tooLittleChargePaysNothing() {
		assertNull(TravelRules.drawPlan(new long[]{10, 10}, 25));
		assertNull(TravelRules.drawPlan(new long[0], 10));
	}
}
