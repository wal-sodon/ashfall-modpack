package xyz.toesinthesand.ashfall.director.horde;

import java.util.ArrayList;
import java.util.List;

/**
 * How lone zombies form hordes and when two hordes merge, as pure functions over points so JUnit covers them.
 * <p>
 * Grouping is greedy, not a full clustering: each lone zombie in turn gathers the other lone zombies within the
 * group radius of it, and they become a horde if there are enough of them. That is linear-ish on the at most 150
 * Director zombies a world may hold and runs only every couple of seconds; a precise clustering would cost more
 * and look the same in play, since hordes keep recruiting and merging on every update anyway.
 */
public final class HordeGrouping {
	/** A lone zombie: an index chosen by the caller and its position. */
	public record Point(int id, double x, double y, double z) {
		double distanceSqr(Point o) {
			double dx = x - o.x, dy = y - o.y, dz = z - o.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	private HordeGrouping() {
	}

	/**
	 * New hordes among lone zombies: lists of point ids, each at least {@code minGroup} and at most {@code maxSize}
	 * long, the first id being the leader. A point is in at most one list; points in no list stay alone.
	 */
	public static List<List<Integer>> cluster(List<Point> lone, double radius, int minGroup, int maxSize) {
		double r2 = radius * radius;
		boolean[] used = new boolean[lone.size()];
		List<List<Integer>> out = new ArrayList<>();
		for (int i = 0; i < lone.size(); i++) {
			if (used[i]) {
				continue;
			}
			Point seed = lone.get(i);
			List<Integer> picked = new ArrayList<>();
			picked.add(i);
			for (int j = i + 1; j < lone.size() && picked.size() < maxSize; j++) {
				if (!used[j] && seed.distanceSqr(lone.get(j)) <= r2) {
					picked.add(j);
				}
			}
			if (picked.size() >= minGroup) {
				List<Integer> ids = new ArrayList<>(picked.size());
				for (int index : picked) {
					used[index] = true;
					ids.add(lone.get(index).id());
				}
				out.add(ids);
			}
		}
		return out;
	}

	/** Whether two hordes merge: leaders within {@code radius} and the merged horde no bigger than {@code maxSize}. */
	public static boolean shouldMerge(double leaderDistanceSqr, int sizeA, int sizeB, double radius, int maxSize) {
		return leaderDistanceSqr <= radius * radius && sizeA + sizeB <= maxSize;
	}

	/** Whether a lone zombie this far from a horde's leader joins it. */
	public static boolean shouldJoin(double leaderDistanceSqr, int hordeSize, double radius, int maxSize) {
		return leaderDistanceSqr <= radius * radius && hordeSize < maxSize;
	}
}
