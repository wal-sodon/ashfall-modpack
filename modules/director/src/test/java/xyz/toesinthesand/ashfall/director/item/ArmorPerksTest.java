package xyz.toesinthesand.ashfall.director.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmorPerksTest {
	@Test
	void eachPieceTakesItsShareAndAFullSetLeavesTheRest() {
		assertEquals(1f, ArmorPerks.factor(0, 0.15f), 1e-6);
		assertEquals(0.85f, ArmorPerks.factor(1, 0.15f), 1e-6);
		assertEquals(0.4f, ArmorPerks.factor(4, 0.15f), 1e-6);
	}

	@Test
	void theFactorNeverGoesNegative() {
		assertEquals(0f, ArmorPerks.factor(4, 0.3f), 1e-6);
	}
}
