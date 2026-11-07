package xyz.toesinthesand.ashfall.arms.story;

import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

/**
 * How far along the relight loop a district state is, for advancements and the radio. The loop runs
 * DARK, REPAIRED, ENERGIZED, SIEGE, RECLAIMED; an advancement for "energize River Arts" must still be
 * granted to a player who first joins after River Arts is already reclaimed, so criteria test "reached at
 * least" rather than equality.
 * <p>
 * BROKEN (a breach after the ending) ranks with DARK: the district has to be relit, and nothing new is
 * awarded for losing it.
 */
public final class DistrictProgress {
	private DistrictProgress() {
	}

	public static int rank(DistrictState state) {
		return switch (state) {
			case DARK, BROKEN -> 0;
			// a blackout keeps what the district earned: it was reclaimed, and comes back without a siege
			case RECLAIMED, BLACKOUT -> 4;
			case REPAIRED -> 1;
			case ENERGIZED -> 2;
			case SIEGE -> 3;
		};
	}

	/** True when a district in {@code current} has got at least as far as {@code wanted}. */
	public static boolean reached(DistrictState current, DistrictState wanted) {
		return rank(current) >= rank(wanted) && rank(wanted) > 0;
	}

	/** Whether the district still counts as unlit for the radio's "nearest dark substation" pointer. */
	public static boolean unlit(DistrictState state) {
		return state != DistrictState.RECLAIMED;
	}
}
