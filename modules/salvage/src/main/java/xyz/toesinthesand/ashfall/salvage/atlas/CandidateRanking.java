package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic ordering and weighted picks for container placement. Ranking by a position hash
 * (rather than scan order) spreads containers around a room instead of piling them in its
 * north-west corner, while giving the same layout every time the chunk is processed.
 */
public final class CandidateRanking {
	private CandidateRanking() {
	}

	/** Candidates sorted by hash(salt, pos); ties (practically impossible) fall back to position order. */
	public static List<BlockPos> rank(List<BlockPos> candidates, long salt) {
		List<BlockPos> out = new ArrayList<>(candidates);
		out.sort(Comparator.<BlockPos>comparingLong(p -> AtlasHash.hash(salt, p.asLong()))
				.thenComparingLong(BlockPos::asLong));
		return out;
	}

	/**
	 * Index into {@code weights} picked proportionally to weight by a hash; -1 if there is nothing
	 * with positive weight.
	 */
	public static int weightedPick(int[] weights, long hash) {
		long total = 0;
		for (int w : weights) {
			total += Math.max(0, w);
		}
		if (total == 0) {
			return -1;
		}
		long roll = (long) (AtlasHash.unit(hash) * total);
		for (int i = 0; i < weights.length; i++) {
			roll -= Math.max(0, weights[i]);
			if (roll < 0) {
				return i;
			}
		}
		return weights.length - 1;
	}
}
