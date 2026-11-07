package xyz.toesinthesand.ashfall.arms.mission;

import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

import java.util.List;
import java.util.Optional;

/**
 * Everything the {@link MissionResolver} needs to pick an objective, read once from the world by
 * {@link MissionWorld}. A plain snapshot with no Minecraft types, so every step of the chain is unit-tested from
 * hand-made states instead of a running server.
 * <p>
 * Personal facts (the radio, being armed) are this player's own. Grid, boss and finale facts are the world's, so a
 * whole party sees the same district objective. Endgame items (crystals, the frame, banks, the Uplink) are counted
 * across every online player: one player usually carries them for the party, and the others should not be told to
 * go and collect what their friend already holds. On top of that, each of them is sticky at world level once seen
 * ({@link MissionState}): items put away in a chest, or carried by someone who logged off, still count as made.
 *
 * @param radio          the player has found the Field Radio (the synced radio flag)
 * @param armed          the player has held an energy weapon and a Cell Pack at once (sticky, see {@link MissionProgress})
 * @param bunker         the player's own or party's Bunker Core, if one stands
 * @param districts      every scored district, in the recommended order (River Arts first)
 * @param crystals       Resonance Crystals held by the online party ({@code crystalsNeeded} once the world has gathered them)
 * @param crystalsNeeded crystals the Uplink Frame takes
 * @param bosses         story bosses defeated, out of {@code bossesTotal}
 * @param bossesTotal    the story bosses that drop a crystal
 * @param nearestBoss    the nearest story boss lair not yet cleared
 * @param frame          an Uplink Frame was made in this world
 * @param banks          Capacitor Banks held by the online party ({@code banksNeeded} once the world has made them)
 * @param banksNeeded    banks the Grid Uplink takes
 * @param uplink         a Grid Uplink was made in this world
 * @param finale         where the Black Start stands
 * @param voteOpen       the Sever-or-Bind vote is running now (it may never open: {@code ashfall:objective} off)
 * @param pylonsBroken   Heart pylons broken in the running Black Start
 * @param pylonsTotal    Heart pylons in all
 * @param gridPercent    the City Grid %
 * @param ending         the world's ending: "none", "sever" or "bind"
 * @param safehouse      the Montford safehouse (the map marker)
 * @param hull           the Wreck hull (the map marker)
 */
public record MissionFacts(boolean radio, boolean armed, Optional<MissionPos> bunker, List<District> districts, int crystals,
		int crystalsNeeded, int bosses, int bossesTotal, Optional<MissionPos> nearestBoss, boolean frame, int banks, int banksNeeded,
		boolean uplink, Finale finale, boolean voteOpen, int pylonsBroken, int pylonsTotal, int gridPercent, String ending, Optional<MissionPos> safehouse,
		Optional<MissionPos> hull) {
	public static final String NO_ENDING = "none";

	public MissionFacts {
		districts = List.copyOf(districts);
	}

	/** Where the Black Start stands, from the finale's saved run. */
	public enum Finale {
		/** Not started (or reset for a replay with no Uplink seated). */
		IDLE,
		/** Reset for a replay: the spent Uplink waits in its socket for an empty hand. */
		STANDBY,
		SURGE,
		FEEDBACK,
		HEART,
		/** A failed phase: the run waits for a patch or a cell. */
		PAUSED,
		/** The Heart fell; the vote decides the ending. */
		COMPLETE;

		/** A run is going on (or waiting to resume). */
		public boolean running() {
			return this == SURGE || this == FEEDBACK || this == HEART || this == PAUSED;
		}
	}

	/**
	 * One district's loop progress, as the objective needs it.
	 *
	 * @param id             district id
	 * @param state          loop state
	 * @param couplers       couplers socketed
	 * @param couplersNeeded couplers this state needs (the half-cost re-repair for a BROKEN core)
	 * @param cells          Energy Cells loaded
	 * @param cellsNeeded    cells that start the hold
	 * @param holdPercent    hold progress, 0-100
	 * @param holdRadius     how close a player must stay for the hold to count
	 * @param linked         key relays linked
	 * @param keys           key relays in the district
	 * @param core           the Substation Core (or its map marker before the core registered)
	 * @param nextRelay      the nearest key relay not linked yet, if known
	 */
	public record District(String id, DistrictState state, int couplers, int couplersNeeded, int cells, int cellsNeeded, int holdPercent,
			int holdRadius, int linked, int keys, Optional<MissionPos> core, Optional<MissionPos> nextRelay) {
		public boolean reclaimed() {
			return state == DistrictState.RECLAIMED;
		}

		/** Work has started here: a coupler went in, or the loop moved past DARK. */
		public boolean started() {
			return state != DistrictState.DARK || couplers > 0;
		}
	}

	public boolean endingChosen() {
		return !NO_ENDING.equals(ending);
	}
}
