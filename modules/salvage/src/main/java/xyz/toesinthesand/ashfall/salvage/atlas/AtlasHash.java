package xyz.toesinthesand.ashfall.salvage.atlas;

/**
 * Stable 64-bit mixing for every random-looking choice the atlas makes. Chunks are processed in
 * whatever order players load them, so nothing may depend on {@code Random} state or iteration
 * order: each choice is a pure function of (building, position, purpose).
 */
public final class AtlasHash {
	private AtlasHash() {
	}

	/** SplitMix64 finaliser: a good avalanche for adjacent inputs such as neighbouring block positions. */
	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Order-sensitive hash of several values. */
	public static long hash(long... values) {
		long h = 0x9E3779B97F4A7C15L;
		for (long v : values) {
			h = mix(h ^ mix(v + 0x9E3779B97F4A7C15L));
		}
		return h;
	}

	/** A uniform double in [0, 1) derived from a hash. */
	public static double unit(long hash) {
		return (hash >>> 11) * 0x1.0p-53;
	}
}
