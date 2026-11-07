package xyz.toesinthesand.ashfall.director.haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespawnOptionTest {
	@Test
	void bunkerAlwaysWins() {
		assertEquals(RespawnOption.BUNKER, RespawnOption.pickDefault(true, true, true));
		assertEquals(RespawnOption.BUNKER, RespawnOption.pickDefault(true, false, false));
	}

	@Test
	void ownBedBeatsSafeZone() {
		assertEquals(RespawnOption.SPAWN, RespawnOption.pickDefault(false, true, true));
	}

	@Test
	void safeZoneThenWorldSpawn() {
		assertEquals(RespawnOption.SAFE_ZONE, RespawnOption.pickDefault(false, false, true));
		assertEquals(RespawnOption.SPAWN, RespawnOption.pickDefault(false, false, false));
	}
}
