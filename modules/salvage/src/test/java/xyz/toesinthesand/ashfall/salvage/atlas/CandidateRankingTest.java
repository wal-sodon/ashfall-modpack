package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateRankingTest {
	private static List<BlockPos> room() {
		List<BlockPos> cells = new ArrayList<>();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				cells.add(new BlockPos(100 + x, 64, -50 + z));
			}
		}
		return cells;
	}

	@Test
	void rankingIsAPermutationIndependentOfInputOrder() {
		List<BlockPos> cells = room();
		List<BlockPos> shuffled = new ArrayList<>(cells);
		Collections.shuffle(shuffled, new java.util.Random(7));
		List<BlockPos> a = CandidateRanking.rank(cells, 1234);
		List<BlockPos> b = CandidateRanking.rank(shuffled, 1234);
		assertEquals(a, b);
		assertEquals(cells.size(), a.size());
		assertTrue(a.containsAll(cells));
	}

	@Test
	void rankingIsNotScanOrderAndDependsOnSalt() {
		List<BlockPos> cells = room();
		assertNotEquals(cells, CandidateRanking.rank(cells, 1));
		assertNotEquals(CandidateRanking.rank(cells, 1), CandidateRanking.rank(cells, 2));
	}

	@Test
	void topPicksSpreadAcrossTheRoom() {
		// Over many units, the first pick should land in every quadrant, not just the scan origin.
		int[] quadrants = new int[4];
		for (long salt = 0; salt < 400; salt++) {
			BlockPos first = CandidateRanking.rank(room(), salt).getFirst();
			int q = ((first.getX() - 100) / 4) * 2 + (first.getZ() + 50) / 4;
			quadrants[q]++;
		}
		for (int q : quadrants) {
			assertTrue(q > 60, "first pick is clustered: " + java.util.Arrays.toString(quadrants));
		}
	}

	@Test
	void weightedPickFollowsWeights() {
		int[] counts = new int[3];
		for (long i = 0; i < 30_000; i++) {
			counts[CandidateRanking.weightedPick(new int[]{1, 2, 7}, AtlasHash.hash(i))]++;
		}
		assertEquals(0.1, counts[0] / 30_000.0, 0.02);
		assertEquals(0.2, counts[1] / 30_000.0, 0.02);
		assertEquals(0.7, counts[2] / 30_000.0, 0.02);
		assertEquals(-1, CandidateRanking.weightedPick(new int[0], 5));
		assertEquals(1, CandidateRanking.weightedPick(new int[]{0, 3}, 5));
	}
}
