package xyz.toesinthesand.ashfall.arms.story;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryLogicTest {
	private static long pack(int x, int y, int z) {
		// BlockPos.asLong layout (26 bits x, 26 bits z, 12 bits y), without touching Minecraft classes.
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	@Test
	void pickerIsStableAndInRange() {
		for (int i = 0; i < 1000; i++) {
			long pos = pack(i * 13 - 500, 64, i * 7 - 300);
			int a = RelayLogPicker.index(pos, 30);
			assertEquals(a, RelayLogPicker.index(pos, 30));
			assertTrue(a >= 0 && a < 30);
		}
		assertEquals(-1, RelayLogPicker.index(pack(0, 0, 0), 0));
	}

	@Test
	void neighbouringRelaysDoNotGetConsecutiveLogs() {
		Set<Integer> seen = new HashSet<>();
		int consecutive = 0, last = -1;
		for (int x = 0; x < 120; x++) {
			int index = RelayLogPicker.index(pack(x, 70, 0), 30);
			seen.add(index);
			if (index == (last + 1) % 30) {
				consecutive++;
			}
			last = index;
		}
		assertTrue(seen.size() >= 28, "120 neighbouring relays reached only " + seen.size() + " logs");
		assertTrue(consecutive < 15, "too many consecutive picks: " + consecutive);
	}

	@Test
	void compassPointsUseMinecraftAxes() {
		assertEquals("n", Compass.point(0, 0, 0, -100));
		assertEquals("e", Compass.point(0, 0, 100, 0));
		assertEquals("s", Compass.point(0, 0, 0, 100));
		assertEquals("w", Compass.point(0, 0, -100, 0));
		assertEquals("ne", Compass.point(0, 0, 100, -100));
		assertEquals("sw", Compass.point(10, 10, -90, 110));
		assertEquals("n", Compass.point(5, 5, 5, 5));
		assertEquals(640, Compass.roughDistance(0, 0, 0, 643));
		assertEquals(0, Compass.roughDistance(0, 0, 3, 0));
	}

	@Test
	void districtProgressCountsReachedStates() {
		assertTrue(DistrictProgress.reached(DistrictState.ENERGIZED, DistrictState.REPAIRED));
		assertTrue(DistrictProgress.reached(DistrictState.RECLAIMED, DistrictState.SIEGE));
		assertTrue(DistrictProgress.reached(DistrictState.REPAIRED, DistrictState.REPAIRED));
		assertFalse(DistrictProgress.reached(DistrictState.REPAIRED, DistrictState.ENERGIZED));
		assertFalse(DistrictProgress.reached(DistrictState.DARK, DistrictState.DARK), "DARK is not an achievement");
		assertFalse(DistrictProgress.reached(DistrictState.BROKEN, DistrictState.REPAIRED));
		assertTrue(DistrictProgress.unlit(DistrictState.SIEGE));
		assertFalse(DistrictProgress.unlit(DistrictState.RECLAIMED));
	}
}
