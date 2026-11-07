package xyz.toesinthesand.ashfall.director.haven;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionTimersTest {
	private final UUID player = UUID.randomUUID();

	@Test
	void lastsExactlyTheDuration() {
		ProtectionTimers t = new ProtectionTimers();
		t.start(player, 1000, 200);
		assertTrue(t.isProtected(player, 1000));
		assertTrue(t.isProtected(player, 1199));
		assertEquals(1, t.remaining(player, 1199));
		assertFalse(t.isProtected(player, 1200));
		assertEquals(0, t.remaining(player, 1200));
	}

	@Test
	void restartGivesTheFullDurationAgain() {
		ProtectionTimers t = new ProtectionTimers();
		t.start(player, 0, 200);
		t.start(player, 150, 200);
		assertTrue(t.isProtected(player, 300));
		assertEquals(50, t.remaining(player, 300));
	}

	@Test
	void attackingCancels() {
		ProtectionTimers t = new ProtectionTimers();
		t.start(player, 0, 200);
		assertTrue(t.cancel(player, 10));
		assertFalse(t.isProtected(player, 11));
		assertFalse(t.cancel(player, 12), "second cancel reports nothing was running");
	}

	@Test
	void zeroDurationProtectsNobodyAndPurgeDropsExpired() {
		ProtectionTimers t = new ProtectionTimers();
		t.start(player, 0, 0);
		assertFalse(t.isProtected(player, 0));
		t.start(UUID.randomUUID(), 0, 10);
		t.start(UUID.randomUUID(), 0, 100);
		t.purge(50);
		assertEquals(1, t.size());
	}
}
