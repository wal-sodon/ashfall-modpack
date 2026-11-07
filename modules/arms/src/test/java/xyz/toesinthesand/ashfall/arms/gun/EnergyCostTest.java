package xyz.toesinthesand.ashfall.arms.gun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyCostTest {
	/** Shots a pack of {@code capacity} gives at {@code cost} per shot, following the credit rules. */
	private static int shots(long capacity, float cost) {
		long stored = capacity;
		float credit = 0f;
		int shots = 0;
		while (true) {
			long units = EnergyCost.unitsToDraw(cost, credit);
			if (stored < units) {
				return shots;
			}
			stored -= units;
			credit = EnergyCost.creditAfter(cost, credit, units);
			shots++;
		}
	}

	@Test
	void wholeCostsDrawExactly() {
		assertEquals(1L, EnergyCost.unitsToDraw(1f, 0f));
		assertEquals(3L, EnergyCost.unitsToDraw(3f, 0f));
		assertEquals(0f, EnergyCost.creditAfter(3f, 0f, 3L), 1e-6f);
	}

	@Test
	void fractionalCostsAlternate() {
		assertEquals(2L, EnergyCost.unitsToDraw(1.5f, 0f));
		assertEquals(0.5f, EnergyCost.creditAfter(1.5f, 0f, 2L), 1e-6f);
		assertEquals(1L, EnergyCost.unitsToDraw(1.5f, 0.5f));
		assertEquals(0f, EnergyCost.creditAfter(1.5f, 0.5f, 1L), 1e-6f);
	}

	@Test
	void packsGiveTheExpectedShotCounts() {
		assertEquals(30, shots(30, 1f));    // spark pistol, small pack
		assertEquals(20, shots(30, 1.5f));  // volt sidearm, small pack
		assertEquals(30, shots(60, 2f));    // pulse carbine, standard pack
		assertEquals(10, shots(60, 6f));    // lancer longrifle, standard pack
		assertEquals(120, shots(120, 1f));  // storm repeater, heavy pack
	}
}
