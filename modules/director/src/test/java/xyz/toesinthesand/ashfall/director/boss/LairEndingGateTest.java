package xyz.toesinthesand.ashfall.director.boss;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A lair gated on a story flag (the Bind ending's Ascendant): first visit and every return wait out the respawn days. */
class LairEndingGateTest {
	private static final long DAY = LairRules.DAY_TICKS;
	private static final Optional<BossLair.Respawn> TEN = Optional.of(new BossLair.Respawn(10));

	@Test
	void neverSpawnsWithoutAnEnding() {
		assertFalse(LairRules.maySpawn(LairRules.Status.READY, false, 0, OptionalLong.empty(), TEN, 100 * DAY, true));
	}

	@Test
	void firstVisitWaitsTenDaysAfterTheEnding() {
		OptionalLong ending = OptionalLong.of(50 * DAY);
		assertFalse(LairRules.maySpawn(LairRules.Status.READY, false, 0, ending, TEN, 55 * DAY, true));
		assertTrue(LairRules.maySpawn(LairRules.Status.READY, false, 0, ending, TEN, 60 * DAY, true));
		assertTrue(LairRules.maySpawn(LairRules.Status.READY, false, 0, ending, TEN, 55 * DAY, false), "ungated lairs spawn at once");
	}

	@Test
	void returnsEveryTenDaysAfterEachDefeat() {
		OptionalLong ending = OptionalLong.of(50 * DAY);
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 61 * DAY, ending, TEN, 70 * DAY, true));
		assertTrue(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 61 * DAY, ending, TEN, 71 * DAY, true));
		assertFalse(LairRules.maySpawn(LairRules.Status.ACTIVE, false, 0, ending, TEN, 200 * DAY, true));
	}

	@Test
	void gatedLairWithoutRespawnRuleNeverSpawns() {
		assertFalse(LairRules.maySpawn(LairRules.Status.READY, false, 0, OptionalLong.of(0), Optional.empty(), 500 * DAY, true));
	}
}
