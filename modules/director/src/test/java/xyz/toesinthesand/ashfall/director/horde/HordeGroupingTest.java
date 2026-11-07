package xyz.toesinthesand.ashfall.director.horde;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.director.senses.BloodTrails;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How lone zombies band together, when hordes merge, and who bleeds. */
class HordeGroupingTest {
	private static HordeGrouping.Point p(int id, double x, double z) {
		return new HordeGrouping.Point(id, x, 64, z);
	}

	@Test
	void nearbyZombiesFormAHordeLonersStayAlone() {
		List<HordeGrouping.Point> lone = List.of(p(0, 0, 0), p(1, 3, 0), p(2, 0, 4), p(3, 50, 50));
		List<List<Integer>> groups = HordeGrouping.cluster(lone, 8, 3, 40);
		assertEquals(List.of(List.of(0, 1, 2)), groups, "three together form one horde, led by the first");
	}

	@Test
	void tooFewDoNotFormAHorde() {
		assertTrue(HordeGrouping.cluster(List.of(p(0, 0, 0), p(1, 2, 0)), 8, 3, 40).isEmpty());
	}

	@Test
	void twoGroupsApartStaySeparateAndNoZombieIsInTwo() {
		List<HordeGrouping.Point> lone = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			lone.add(p(i, i, 0));
			lone.add(p(10 + i, 30 + i, 0));
		}
		List<List<Integer>> groups = HordeGrouping.cluster(lone, 8, 3, 40);
		assertEquals(2, groups.size());
		assertEquals(4, groups.get(0).size());
		assertEquals(4, groups.get(1).size());
		assertTrue(groups.get(0).stream().noneMatch(groups.get(1)::contains));
	}

	@Test
	void hordesAreCappedAtMaxSize() {
		List<HordeGrouping.Point> lone = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			lone.add(p(i, i * 0.5, 0));
		}
		List<List<Integer>> groups = HordeGrouping.cluster(lone, 8, 3, 6);
		assertEquals(6, groups.getFirst().size(), "the first horde is full at 6");
		assertEquals(4, groups.get(1).size(), "the rest form a second");
	}

	@Test
	void hordesMergeWhenCloseAndNotTooBig() {
		assertTrue(HordeGrouping.shouldMerge(15 * 15, 5, 5, 16, 40));
		assertFalse(HordeGrouping.shouldMerge(17 * 17, 5, 5, 16, 40), "too far apart");
		assertFalse(HordeGrouping.shouldMerge(4, 30, 20, 16, 40), "the merged horde would be too big");
	}

	@Test
	void loneZombiesJoinANearbyHordeWithRoom() {
		assertTrue(HordeGrouping.shouldJoin(7 * 7, 10, 8, 40));
		assertFalse(HordeGrouping.shouldJoin(9 * 9, 10, 8, 40));
		assertFalse(HordeGrouping.shouldJoin(1, 40, 8, 40), "a full horde takes no one");
	}

	@Test
	void onlyBadlyWoundedPlayersBleed() {
		assertTrue(BloodTrails.wounded(10f, 20f, 0.5f));
		assertFalse(BloodTrails.wounded(10.5f, 20f, 0.5f));
		assertFalse(BloodTrails.wounded(0f, 20f, 0.5f), "the dead leave no trail");
	}
}
