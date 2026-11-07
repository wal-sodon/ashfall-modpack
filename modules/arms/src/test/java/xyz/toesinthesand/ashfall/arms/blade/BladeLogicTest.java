package xyz.toesinthesand.ashfall.arms.blade;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Plasma Blade's sweep rules: target shares, hunger, wear and swing counting. */
class BladeLogicTest {
	private static final float EPS = 1e-4f;
	private static final BladeStats S = BladeStats.DEFAULT;

	@Test
	void sweepSharesFollowThePlan() {
		assertEquals(1f, BladeLogic.targetMultiplier(1, 3, 0.6f), EPS);
		assertEquals(0.6f, BladeLogic.targetMultiplier(2, 3, 0.6f), EPS);
		assertEquals(0.6f, BladeLogic.targetMultiplier(3, 3, 0.6f), EPS);
		assertEquals(0f, BladeLogic.targetMultiplier(4, 3, 0.6f), EPS);
	}

	@Test
	void hungerOnlyForExtraTargets() {
		assertEquals(0f, BladeLogic.extraExhaustion(1, 3, 1f), EPS);
		assertEquals(1f, BladeLogic.extraExhaustion(2, 3, 1f), EPS);
		assertEquals(1f, BladeLogic.extraExhaustion(3, 3, 1f), EPS);
		assertEquals(0f, BladeLogic.extraExhaustion(4, 3, 1f), EPS);
	}

	@Test
	void strikeDamageRescalesFromTheAttribute() {
		assertEquals(9f, BladeLogic.strikeDamage(9f, 9f, S, true, 1, false), EPS);
		assertEquals(5.4f, BladeLogic.strikeDamage(9f, 9f, S, true, 2, false), EPS);
		assertEquals(0f, BladeLogic.strikeDamage(9f, 9f, S, true, 4, false), EPS);
		assertEquals(2f, BladeLogic.strikeDamage(9f, 9f, S, false, 1, false), EPS);
		assertEquals(18f, BladeLogic.strikeDamage(9f, 9f, S, true, 1, true), EPS);
		assertEquals(2f, BladeLogic.strikeDamage(9f, 9f, S, false, 1, true), EPS, "the hilt gets no pylon bonus");
		assertEquals(4.5f, BladeLogic.strikeDamage(4.5f, 9f, S, true, 1, false), EPS, "a half-charged swing keeps its share");
	}

	@Test
	void wearNeverGoesNegative() {
		assertEquals(599, BladeLogic.wearAfter(600, 1));
		assertEquals(0, BladeLogic.wearAfter(1, 5));
		assertEquals(10, BladeLogic.wearAfter(10, -3));
	}

	@Test
	void swingsAreCountedPerTick() {
		UUID player = UUID.randomUUID();
		assertNull(BladeLogic.current(player, 100));
		BladeLogic.Swing first = BladeLogic.registerHit(player, 100, true);
		assertTrue(first.first());
		assertEquals(2, BladeLogic.registerHit(player, 100, false).hits());
		BladeLogic.Swing third = BladeLogic.registerHit(player, 100, false);
		assertEquals(3, third.hits());
		assertTrue(third.powered(), "power is decided by the first target");
		assertFalse(BladeLogic.overCap(player, 100, 3));
		BladeLogic.registerHit(player, 100, false);
		assertTrue(BladeLogic.overCap(player, 100, 3));
		BladeLogic.Swing next = BladeLogic.registerHit(player, 120, false);
		assertEquals(1, next.hits());
		assertFalse(next.powered());
		assertNull(BladeLogic.current(player, 100));
		BladeLogic.forget(player);
		assertNull(BladeLogic.current(player, 120));
	}
}
