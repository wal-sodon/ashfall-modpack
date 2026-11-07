package xyz.toesinthesand.ashfall.arms.story;

import xyz.toesinthesand.ashfall.salvage.atlas.AtlasHash;

/**
 * Which Relay Log a Power Relay carries. Pure so it is unit-testable and identical on every server: the
 * same relay always plays the same fragment, which lets players trade "the relay by the brewery has #12"
 * and keeps the pool stable across restarts without storing anything per relay.
 * <p>
 * The relay's packed block position goes through the atlas's SplitMix64 finaliser before the modulo,
 * because neighbouring relays differ only in their low bits and a plain modulo would hand a street of
 * relays consecutive logs.
 */
public final class RelayLogPicker {
	/** Fixed salt so the log hash is independent of every other hash of the same position. */
	static final long SALT = 0x5E1A_7106L;

	private RelayLogPicker() {
	}

	/** Index into a pool of {@code poolSize} logs (0-based) for the relay at {@code packedPos}; -1 for an empty pool. */
	public static int index(long packedPos, int poolSize) {
		if (poolSize <= 0) {
			return -1;
		}
		return (int) Math.floorMod(AtlasHash.hash(packedPos, SALT), (long) poolSize);
	}
}
