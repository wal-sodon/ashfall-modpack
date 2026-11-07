package xyz.toesinthesand.ashfall.salvage.atlas;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits a unit's container target across the chunks it spans.
 * <p>
 * Chunks are post-processed independently and in any order, so a chunk cannot ask its neighbours
 * how many containers they already placed. Instead every chunk recomputes the same split from the
 * unit's full footprint: each chunk gets its share of the target by cell count (largest remainder,
 * ties broken by a hash so no chunk is systematically favoured), and the shares always sum to the
 * target.
 */
public final class QuotaAllocator {
	private QuotaAllocator() {
	}

	/** Containers a unit should hold: per-100-cells density, rounded, clamped to [min, max]. */
	public static int target(float per100m2, int min, int max, int unitArea) {
		int raw = Math.round(per100m2 * unitArea / 100f);
		return Math.max(min, Math.min(Math.max(min, max), raw));
	}

	/**
	 * @param target containers for the whole unit
	 * @param cells  packed chunk pos -> number of the unit's cells in that chunk (zero entries ignored)
	 * @param salt   unit identity, so ties break differently per unit but identically per call
	 * @return packed chunk pos -> quota; quotas sum to {@code target} when any chunk has cells
	 */
	public static Long2IntMap allocate(int target, Long2IntMap cells, long salt) {
		Long2IntMap out = new Long2IntOpenHashMap();
		long total = 0;
		List<long[]> entries = new ArrayList<>();
		for (Long2IntMap.Entry e : cells.long2IntEntrySet()) {
			if (e.getIntValue() > 0) {
				total += e.getIntValue();
				entries.add(new long[]{e.getLongKey(), e.getIntValue()});
			}
		}
		if (total == 0 || target <= 0) {
			entries.forEach(e -> out.put(e[0], 0));
			return out;
		}
		// Sort by chunk so the result never depends on hash map iteration order.
		entries.sort((a, b) -> Long.compare(a[0], b[0]));
		int n = entries.size();
		long[] remainder = new long[n];
		int assigned = 0;
		for (int i = 0; i < n; i++) {
			long scaled = (long) target * entries.get(i)[1];
			int floor = (int) (scaled / total);
			remainder[i] = scaled % total;
			out.put(entries.get(i)[0], floor);
			assigned += floor;
		}
		Integer[] order = new Integer[n];
		Arrays.setAll(order, i -> i);
		Arrays.sort(order, (a, b) -> {
			int c = Long.compare(remainder[b], remainder[a]);
			if (c != 0) {
				return c;
			}
			return Long.compareUnsigned(AtlasHash.hash(salt, entries.get(a)[0]), AtlasHash.hash(salt, entries.get(b)[0]));
		});
		for (int k = 0; k < target - assigned; k++) {
			long chunk = entries.get(order[k])[0];
			out.put(chunk, out.get(chunk) + 1);
		}
		return out;
	}
}
