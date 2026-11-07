package xyz.toesinthesand.ashfall.arms.grid;

import java.util.Locale;

/**
 * Where one district stands in the Relight loop (endgame-design section 3). The order is the loop's order,
 * so tooling may compare ordinals, but code should name states: a failed siege goes from SIEGE back to
 * REPAIRED, and BROKEN (a Breach after the ending) will sit outside the forward path.
 */
public enum DistrictState {
	/** The grid is down here; the Substation Core waits for its Grid Couplers. */
	DARK,
	/** Every coupler is socketed; the core takes Energy Cells, holds and links from here. */
	REPAIRED,
	/** Hold done and every key relay linked; a District Siege is scheduled for the coming nightfall. */
	ENERGIZED,
	/** The siege night is running around the core. */
	SIEGE,
	/** The district survived its siege and is back on the grid. */
	RECLAIMED,
	/** Reserved for Breach events (endgame-design section 5): a reclaimed core that broke again. */
	BROKEN,
	/**
	 * A reclaimed district whose relays wore out ({@link GridDecay}): dark, spawning as usual, but still won. Repairing
	 * relays until enough work again returns it straight to RECLAIMED, with no new siege.
	 */
	BLACKOUT;

	/** Whether a once-reclaimed district has lost its power (its grid lamps go dark). */
	public boolean dark() {
		return this == BROKEN || this == BLACKOUT;
	}

	/** Lower-case name, as used in commands, saved data and the sync payload. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The state named {@code id} (case-insensitive), or null. */
	public static DistrictState byId(String id) {
		for (DistrictState state : values()) {
			if (state.id().equalsIgnoreCase(id)) {
				return state;
			}
		}
		return null;
	}
}
