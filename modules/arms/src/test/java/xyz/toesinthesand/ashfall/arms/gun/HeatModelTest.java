package xyz.toesinthesand.ashfall.arms.gun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatModelTest {
	private static final float EPS = 1e-4f;

	@Test
	void coolsLinearlyAndNeverBelowZero() {
		// 25/s for 1 s (20 ticks) takes 50 down to 25.
		assertEquals(25f, HeatModel.heatAt(50f, 100, false, 120, 25f), EPS);
		assertEquals(0f, HeatModel.heatAt(50f, 100, false, 1_000, 25f), EPS);
		// A shot "in the future" (a gun from a save with a later clock) counts as long cold, so it
		// can't stay hot or locked until this world's clock catches up.
		assertEquals(0f, HeatModel.heatAt(50f, 100, false, 50, 25f), EPS);
		assertFalse(HeatModel.lockedAt(100f, 10_000, true, 50, 25f));
	}

	@Test
	void lockedGunsCoolAtEightyPercent() {
		assertEquals(100f - 20f, HeatModel.heatAt(100f, 0, true, 20, 25f), EPS);
	}

	@Test
	void reachingCapacityLocksAndClamps() {
		HeatModel.Shot shot = HeatModel.fire(95f, 0, false, 0, 25f, 12f, 100f);
		assertEquals(100f, shot.heat(), EPS);
		assertTrue(shot.locked());
		assertTrue(shot.justLocked());

		HeatModel.Shot cool = HeatModel.fire(10f, 0, false, 0, 25f, 12f, 100f);
		assertEquals(22f, cool.heat(), EPS);
		assertFalse(cool.locked());
		assertFalse(cool.justLocked());
	}

	@Test
	void lockoutHoldsUntilFullyCold() {
		// 100 heat, locked, cooling 20/s effective: still locked after 4.9 s, free at 5 s.
		assertTrue(HeatModel.lockedAt(100f, 0, true, 98, 25f));
		assertFalse(HeatModel.lockedAt(100f, 0, true, 100, 25f));
		// An unlocked gun is never reported locked.
		assertFalse(HeatModel.lockedAt(100f, 0, false, 0, 25f));
	}

	@Test
	void sparkPistolOverheatsOnTheFourteenthFullRateShot() {
		// 12 heat/shot at 300 RPM (4 ticks), cooling 25/s = 5 heat per 4 ticks: net +7 per shot after the first.
		float heat = 0f;
		long tick = 0;
		boolean locked = false;
		int shots = 0;
		while (!locked && shots < 100) {
			HeatModel.Shot shot = HeatModel.fire(heat, tick, false, tick + 4, 25f, 12f, 100f);
			heat = shot.heat();
			locked = shot.locked();
			tick += 4;
			shots++;
		}
		assertEquals(14, shots);
	}

	@Test
	void rpmBecomesWholeTicks() {
		assertEquals(4, HeatModel.ticksBetweenShots(300));
		assertEquals(5, HeatModel.ticksBetweenShots(240));
		assertEquals(6, HeatModel.ticksBetweenShots(200));
		assertEquals(13, HeatModel.ticksBetweenShots(90));
		assertEquals(30, HeatModel.ticksBetweenShots(40));
		assertEquals(2, HeatModel.ticksBetweenShots(600));
		assertEquals(1, HeatModel.ticksBetweenShots(5_000));
	}
}
