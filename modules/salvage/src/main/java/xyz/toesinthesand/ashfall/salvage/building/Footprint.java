package xyz.toesinthesand.ashfall.salvage.building;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A set of block columns stored as rows of inclusive x runs, sorted by z then x. This is the
 * run-length format Arnis's Ashfall sidecar writes ({@code [z, x_start, x_end]}).
 */
public final class Footprint {
	private final int[] z;
	private final int[] x0;
	private final int[] x1;
	private final int area;
	private final int minX, minZ, maxX, maxZ;

	private Footprint(int[] z, int[] x0, int[] x1) {
		this.z = z;
		this.x0 = x0;
		this.x1 = x1;
		int a = 0, mnx = Integer.MAX_VALUE, mnz = Integer.MAX_VALUE, mxx = Integer.MIN_VALUE, mxz = Integer.MIN_VALUE;
		for (int i = 0; i < z.length; i++) {
			a += x1[i] - x0[i] + 1;
			mnx = Math.min(mnx, x0[i]);
			mxx = Math.max(mxx, x1[i]);
			mnz = Math.min(mnz, z[i]);
			mxz = Math.max(mxz, z[i]);
		}
		this.area = a;
		this.minX = mnx;
		this.minZ = mnz;
		this.maxX = mxx;
		this.maxZ = mxz;
	}

	/** Builds a footprint from possibly overlapping, unsorted rows; overlapping runs are merged. */
	public static Footprint of(List<int[]> rows) {
		List<int[]> sorted = new ArrayList<>(rows);
		sorted.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		List<int[]> merged = new ArrayList<>();
		for (int[] row : sorted) {
			int[] last = merged.isEmpty() ? null : merged.getLast();
			if (last != null && last[0] == row[0] && row[1] <= last[2] + 1) {
				last[2] = Math.max(last[2], row[2]);
			} else {
				merged.add(new int[]{row[0], row[1], row[2]});
			}
		}
		int n = merged.size();
		int[] z = new int[n], x0 = new int[n], x1 = new int[n];
		for (int i = 0; i < n; i++) {
			z[i] = merged.get(i)[0];
			x0[i] = merged.get(i)[1];
			x1[i] = merged.get(i)[2];
		}
		return new Footprint(z, x0, x1);
	}

	public boolean contains(int x, int zz) {
		int lo = 0, hi = z.length - 1;
		// Binary search the first row at this z, then scan its runs.
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (z[mid] < zz) {
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		for (int i = lo; i < z.length && z[i] == zz; i++) {
			if (x >= x0[i] && x <= x1[i]) {
				return true;
			}
		}
		return false;
	}

	/** Visits every column as (x, z). */
	public void forEach(BiConsumer<Integer, Integer> visitor) {
		for (int i = 0; i < z.length; i++) {
			for (int x = x0[i]; x <= x1[i]; x++) {
				visitor.accept(x, z[i]);
			}
		}
	}

	/** Number of columns. */
	public int area() {
		return area;
	}

	public int minX() {
		return minX;
	}

	public int minZ() {
		return minZ;
	}

	public int maxX() {
		return maxX;
	}

	public int maxZ() {
		return maxZ;
	}
}
