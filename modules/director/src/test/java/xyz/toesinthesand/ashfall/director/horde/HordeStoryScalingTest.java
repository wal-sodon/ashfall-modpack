package xyz.toesinthesand.ashfall.director.horde;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.director.spawn.SpawnRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The endings' horde and spawn scaling: Sever's rarer nights, Bind's bigger waves, and their limits. */
class HordeStoryScalingTest {
	@Test
	void neutralScalingIsTheSameSchedule() {
		assertSame(HordeSchedule.DEFAULT, HordeSchedule.DEFAULT.scaled(1, 1.0));
	}

	@Test
	void intervalStretchesFromTheSameFirstNight() {
		HordeSchedule base = HordeSchedule.DEFAULT;
		HordeSchedule rare = base.scaled(3, 1.0);
		assertEquals(base.everyNights() * 3, rare.everyNights());
		assertEquals(base.firstNight(), rare.firstNight());
		int nights = 0;
		for (int day = 1; day <= 300; day++) {
			if (rare.isHordeDay(day, 42L)) {
				nights++;
			}
		}
		assertTrue(nights <= 300 / rare.everyNights() + 1, "too many Aftershock nights: " + nights);
	}

	@Test
	void wavesGrowRoundedUpAndCapped() {
		assertEquals(10, HordeSchedule.scaleWave(10, 1.0));
		assertEquals(12, HordeSchedule.scaleWave(10, 1.2));
		assertEquals(7, HordeSchedule.scaleWave(6, 1.1), "rounded up");
		assertEquals(100, HordeSchedule.scaleWave(90, 2.0), "the codec's 100 limit");
		assertEquals(10, HordeSchedule.scaleWave(10, 0.5), "an ending never shrinks waves");
		HordeSchedule big = HordeSchedule.DEFAULT.scaled(1, 1.5);
		assertEquals(HordeSchedule.scaleWave(HordeSchedule.DEFAULT.tiers().getFirst().waves().getFirst().size(), 1.5),
				big.tiers().getFirst().waves().getFirst().size());
		assertSame(big, HordeSchedule.DEFAULT.scaled(1, 1.5), "the last scaled schedule is cached");
	}

	@Test
	void storyTierNeverLiftsAboveTheDayAndGridTiersBeyondTheCap() {
		// HordeHooks.tier = schedule.tier(day, max(grid, story)); the story part is clamped to VI before that.
		assertEquals(6, HordeHooks.STORY_TIER_HARD_CAP);
		assertEquals(Math.max(HordeSchedule.DEFAULT.dayTier(1), 6), HordeSchedule.DEFAULT.tier(1, Math.min(9, HordeHooks.STORY_TIER_HARD_CAP)));
	}

	@Test
	void spawnMultiplierScalesBudgets() {
		assertEquals(8, SpawnRules.scaleBySpawnMultiplier(20, 0.4));
		assertEquals(20, SpawnRules.scaleBySpawnMultiplier(20, 1.0));
		assertEquals(0, SpawnRules.scaleBySpawnMultiplier(1, 0.4), "rounds to the nearest zombie");
		assertEquals(0, SpawnRules.scaleBySpawnMultiplier(5, -1.0));
	}
}
