package xyz.toesinthesand.ashfall.salvage.loot;

import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

/**
 * The pure refill decision, kept apart from block entities so it can be unit-tested.
 * <p>
 * A shared container refills only if it was looted (opened) at least {@code delayTicks} ago AND the
 * area around it has since been left alone for a full quiet period. The second condition stops
 * camping: a player who parks next to a pharmacy never sees it restock, and a restock never happens
 * in front of someone.
 */
public final class RefillRule {
	private RefillRule() {
	}

	/**
	 * @param rules           refill rules of the zone or profile owning the container
	 * @param lastOpened      game time the container was last opened; negative = never
	 * @param now             current game time
	 * @param lastQuietGapEnd game time a quiet period in the container's area last ended; negative = unknown
	 */
	public static boolean shouldRefill(BuildingProfile.Refill rules, long lastOpened, long now, long lastQuietGapEnd) {
		return rules.enabled()
				&& lastOpened >= 0
				&& now - lastOpened >= rules.delayTicks()
				&& lastQuietGapEnd > lastOpened;
	}

	/** Deterministic loot seed for a container's n-th roll, so a reroll differs from the last one. */
	public static long seed(long packedPos, int roll) {
		long h = packedPos * 0x9E3779B97F4A7C15L + roll * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ (h >>> 29);
	}
}
