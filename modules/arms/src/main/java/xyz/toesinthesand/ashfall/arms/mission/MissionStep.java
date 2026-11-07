package xyz.toesinthesand.ashfall.arms.mission;

import java.util.Locale;

/**
 * The stages of the mission, in the order the checklist lists them (endgame-design section 3 and 4). DISTRICT stands
 * for all four districts: which one is meant travels in the objective's arguments.
 */
public enum MissionStep {
	/** Find the Field Radio in the safehouse radio cache. Personal. */
	RADIO,
	/** Craft a first energy weapon and a Cell Pack. Personal. */
	ARM,
	/** Optional: place a Bunker Core. Per party. Only a checklist row, never the current objective. */
	HAVEN,
	/** Reclaim a district. World-wide. */
	DISTRICT,
	/** Gather the Resonance Crystals the Uplink needs. Counted across the online party. */
	CRYSTALS,
	/** Craft the Grid Uplink. Counted across the online party. */
	UPLINK,
	/** Seat the Uplink and survive the three phases. World-wide. */
	BLACK_START,
	/** Vote Sever or Bind. World-wide. */
	CHOOSE,
	/** After the ending: the sandbox goals. World-wide. */
	AFTER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
