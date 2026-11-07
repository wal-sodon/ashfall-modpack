package xyz.toesinthesand.ashfall.arms.survivor;

import java.util.Locale;

/**
 * What a survivor is doing when it is not fighting or running: walking behind its leader, holding one spot, or living
 * at its home base (wandering inside the Bunker Core's area). A survivor escorted into its leader's Bunker Core area
 * settles there and switches from {@link #FOLLOW} to {@link #HOME} by itself.
 */
public enum SurvivorMode {
	FOLLOW,
	STAY,
	HOME;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static SurvivorMode byId(String id) {
		for (SurvivorMode mode : values()) {
			if (mode.id().equals(id)) {
				return mode;
			}
		}
		return STAY;
	}
}
