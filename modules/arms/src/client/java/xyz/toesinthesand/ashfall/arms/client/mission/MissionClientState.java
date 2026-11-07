package xyz.toesinthesand.ashfall.arms.client.mission;

import xyz.toesinthesand.ashfall.arms.mission.MissionPos;
import xyz.toesinthesand.ashfall.arms.mission.MissionSyncPayload;
import xyz.toesinthesand.ashfall.arms.mission.Objective;

import java.util.Optional;

/**
 * The client's copy of the last {@code ashfall:objective}: the current objective and the player's Bunker Core.
 * Cleared on disconnect so one world's objective never shows in the next.
 */
public final class MissionClientState {
	private static volatile MissionSyncPayload last;

	private MissionClientState() {
	}

	public static void accept(MissionSyncPayload payload) {
		last = payload;
	}

	public static void clear() {
		last = null;
	}

	/** The objective to draw, if the server sent one and allows prompts. */
	public static Optional<Objective> objective() {
		MissionSyncPayload p = last;
		return p == null || !p.prompts() ? Optional.empty() : Optional.of(p.objective());
	}

	/** The objective even when prompts are off (the Grid Map is a tool the player opened, not a prompt). */
	public static Optional<Objective> objectiveForMap() {
		MissionSyncPayload p = last;
		return p == null ? Optional.empty() : Optional.of(p.objective());
	}

	public static Optional<MissionPos> bunker() {
		MissionSyncPayload p = last;
		return p == null ? Optional.empty() : p.bunker();
	}
}
