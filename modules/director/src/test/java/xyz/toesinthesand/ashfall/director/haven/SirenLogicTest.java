package xyz.toesinthesand.ashfall.director.haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The siren with a fake horde flag: the countdown window, repeats and the horde-night trigger. */
class SirenLogicTest {
	private static final long WARN = 3000, REPEAT = 600;
	private static final HordeSnapshot TONIGHT = new HordeSnapshot(false, 1, 0);
	private static final HordeSnapshot LATER = new HordeSnapshot(false, 3, 0);
	private static final HordeSnapshot RUNNING = new HordeSnapshot(true, 0, 0);

	@Test
	void hordeTonightMirrorsUndeadNightsCounters() {
		assertTrue(new HordeSnapshot(false, 1, 0).hordeTonight());
		assertTrue(new HordeSnapshot(false, 0, 0).hordeTonight());
		assertTrue(new HordeSnapshot(false, 5, 1).hordeTonight(), "last grace night rolls the horde at once");
		assertFalse(new HordeSnapshot(false, 5, 2).hordeTonight());
		assertFalse(new HordeSnapshot(false, 2, 0).hordeTonight());
		assertFalse(RUNNING.hordeTonight(), "already running is not 'tonight'");
		assertFalse(HordeSnapshot.NONE.hordeTonight());
	}

	@Test
	void idleOutsideTheWindowOrWithoutAHorde() {
		SirenLogic logic = new SirenLogic();
		assertEquals(SirenLogic.Phase.IDLE, logic.update(TONIGHT, 8999, 0, WARN, REPEAT).phase());
		assertEquals(SirenLogic.Phase.IDLE, logic.update(LATER, 11000, 20, WARN, REPEAT).phase());
		assertEquals(SirenLogic.Phase.IDLE, logic.update(TONIGHT, 12000, 40, WARN, REPEAT).phase(),
				"past nightfall without the flag (the roll said no) is quiet");
	}

	@Test
	void warningStartsWithSirenAndCountsDown() {
		SirenLogic logic = new SirenLogic();
		SirenLogic.Step first = logic.update(TONIGHT, 24000L * 4 + 9000, 100, WARN, REPEAT);
		assertEquals(SirenLogic.Phase.WARNING, first.phase());
		assertTrue(first.siren());
		assertTrue(first.entered());
		assertEquals(3000, first.ticksToNightfall());
		SirenLogic.Step next = logic.update(TONIGHT, 24000L * 4 + 9020, 120, WARN, REPEAT);
		assertFalse(next.siren(), "no repeat yet");
		assertFalse(next.entered());
		assertEquals(2980, next.ticksToNightfall());
		assertTrue(logic.update(TONIGHT, 24000L * 4 + 9600, 700, WARN, REPEAT).siren(), "repeat after the interval");
	}

	@Test
	void hordeNightSoundsOnceOnTheFlag() {
		SirenLogic logic = new SirenLogic();
		logic.update(TONIGHT, 11980, 0, WARN, REPEAT);
		SirenLogic.Step horde = logic.update(RUNNING, 12000, 20, WARN, REPEAT);
		assertEquals(SirenLogic.Phase.HORDE, horde.phase());
		assertTrue(horde.siren());
		assertTrue(horde.entered());
		assertFalse(logic.update(RUNNING, 12020, 2000, WARN, REPEAT).siren(), "no repeats during the horde night");
		SirenLogic.Step morning = logic.update(new HordeSnapshot(false, 7, 0), 23000, 3000, WARN, REPEAT);
		assertEquals(SirenLogic.Phase.IDLE, morning.phase());
		assertTrue(morning.entered());
	}

	@Test
	void zeroRepeatSoundsOnlyOnEntry() {
		SirenLogic logic = new SirenLogic();
		assertTrue(logic.update(TONIGHT, 10000, 0, WARN, 0).siren());
		assertFalse(logic.update(TONIGHT, 11000, 5000, WARN, 0).siren());
	}
}
