package xyz.toesinthesand.ashfall.director.spawn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cleared-timer rules: clears when all members are gone, pauses near players, then halves. */
class BuildingRecordTest {
	private static BuildingRecord populated(UUID... members) {
		BuildingRecord r = new BuildingRecord(1, BuildingRecord.Status.READY, 0, 0, 0, List.of());
		r.populated(4, List.of(members));
		return r;
	}

	@Test
	void clearsWhenLastMemberGone() {
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		BuildingRecord r = populated(a, b);
		assertFalse(r.memberGone(a));
		assertEquals(BuildingRecord.Status.POPULATED, r.status());
		assertFalse(r.memberGone(UUID.randomUUID()), "strangers do not count");
		assertTrue(r.memberGone(b));
		assertEquals(BuildingRecord.Status.CLEARED, r.status());
	}

	@Test
	void timerPausesWhilePlayerNear() {
		BuildingRecord r = populated(UUID.randomUUID());
		r.memberGone(r.members().iterator().next());
		assertFalse(r.tickCleared(false, 100, 1000));
		assertEquals(100, r.clearedTicks());
		assertFalse(r.tickCleared(true, 5000, 1000), "paused, not reset or advanced");
		assertEquals(100, r.clearedTicks());
		assertFalse(r.tickCleared(false, 899, 1000));
		assertTrue(r.tickCleared(false, 1, 1000));
		assertEquals(BuildingRecord.Status.READY, r.status());
		assertEquals(1, r.generation());
		assertEquals(2, r.nextCount(), "fresh population is half the size");
	}

	@Test
	void timerOnlyRunsWhenCleared() {
		BuildingRecord r = populated(UUID.randomUUID());
		assertFalse(r.tickCleared(false, 1_000_000, 1000));
		assertEquals(BuildingRecord.Status.POPULATED, r.status());
	}

	@Test
	void emptySpawnGoesStraightToCleared() {
		BuildingRecord r = new BuildingRecord(1, BuildingRecord.Status.READY, 0, 0, 0, List.of());
		r.populated(3, List.of());
		assertEquals(BuildingRecord.Status.CLEARED, r.status());
	}

	@Test
	void adminCommands() {
		BuildingRecord r = populated(UUID.randomUUID());
		r.forceCleared();
		assertEquals(BuildingRecord.Status.CLEARED, r.status());
		assertTrue(r.members().isEmpty());
		r.tickCleared(false, 10_000, 1000);
		r.forceReady();
		assertEquals(0, r.generation());
		assertEquals(4, r.nextCount());
	}
}
