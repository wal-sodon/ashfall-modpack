package xyz.toesinthesand.ashfall.director.horde;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A horde-night wave that only partly spawns owes the rest and retries before the next wave ({@link HordeNights#afterAttempt}). */
class HordeRemainderTest {
	private static final long NOW = 1_000L, NEXT = 600L;

	@Test
	void aFullWaveMovesOnToTheNext() {
		HordeNights.Progress p = HordeNights.afterAttempt(new HordeNights.Progress(4, 0, NOW, 0), 12, 12, NOW, NEXT);
		assertEquals(new HordeNights.Progress(4, 1, NOW + NEXT, 0), p);
	}

	@Test
	void aPartWaveOwesTheRestAndRetries() {
		HordeNights.Progress p = HordeNights.afterAttempt(new HordeNights.Progress(4, 0, NOW, 0), 12, 5, NOW, NEXT);
		assertEquals(new HordeNights.Progress(4, 0, NOW + HordeNights.RETRY_TICKS, 7), p, "7 of 12 still owed, same wave");
		// the retry is for the 7 owed: 3 more come, 4 are still owed
		p = HordeNights.afterAttempt(p, p.owed(), 3, NOW + 100, NEXT);
		assertEquals(new HordeNights.Progress(4, 0, NOW + 100 + HordeNights.RETRY_TICKS, 4), p);
		// the last 4: on to the next wave
		p = HordeNights.afterAttempt(p, p.owed(), 4, NOW + 200, NEXT);
		assertEquals(new HordeNights.Progress(4, 1, NOW + 200 + NEXT, 0), p);
	}

	@Test
	void nothingSpawnedKeepsWhatWasOwed() {
		assertEquals(new HordeNights.Progress(4, 2, NOW + HordeNights.RETRY_TICKS, 0),
				HordeNights.afterAttempt(new HordeNights.Progress(4, 2, NOW, 0), 12, 0, NOW, NEXT), "the whole wave still owed");
		assertEquals(new HordeNights.Progress(4, 2, NOW + HordeNights.RETRY_TICKS, 6),
				HordeNights.afterAttempt(new HordeNights.Progress(4, 2, NOW, 6), 6, 0, NOW, NEXT), "the remainder still owed");
	}
}
