package xyz.toesinthesand.ashfall.director.boss;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * The spawn-once rule, kept free of game state so it is unit-tested on its own.
 * <p>
 * A story boss spawns the first time a player comes near its lair and then never again in that world: the
 * crystals it guards are the story's gate, and a farmable boss would turn the gate into a grind. The only way
 * back is the post-ending "Aftershock": a lair with a {@code respawn} rule may wake again once the story has
 * ended and the given number of days has passed since the ending or since its last defeat, whichever is later.
 */
public final class LairRules {
	/** Ticks in an in-game day; game time (not day time) so {@code /time set} cannot skip a respawn wait. */
	public static final long DAY_TICKS = 24_000L;

	public enum Status {
		/** Never spawned in this world. */
		READY,
		/** Spawned and not yet killed (it may be unloaded with its chunk). */
		ACTIVE,
		/** Killed; stays dead unless the respawn rule allows a return. */
		DEFEATED
	}

	private LairRules() {
	}

	/**
	 * Whether the lair may spawn its boss now.
	 *
	 * @param status       the lair's recorded status
	 * @param sealed       the lair is still sealed (its JSON says sealed and nothing has unsealed it)
	 * @param defeatedTick game time of the last defeat (ignored unless DEFEATED)
	 * @param endingTick   game time the story ended, if it has
	 * @param respawn      the lair's respawn rule, if any
	 * @param now          current game time
	 */
	public static boolean maySpawn(Status status, boolean sealed, long defeatedTick, OptionalLong endingTick,
			Optional<BossLair.Respawn> respawn, long now) {
		return maySpawn(status, sealed, defeatedTick, endingTick, respawn, now, false);
	}

	/**
	 * {@link #maySpawn(Status, boolean, long, OptionalLong, Optional, long)} for a lair gated on a story flag
	 * ({@link BossLair#requiresFlag}): with {@code endingGated}, a lair that never spawned is treated as defeated at
	 * the ending, so even its first appearance waits {@code respawn} days after the ending, and without an ending or a
	 * respawn rule it never spawns. The caller has already checked that the flag is set.
	 */
	public static boolean maySpawn(Status status, boolean sealed, long defeatedTick, OptionalLong endingTick,
			Optional<BossLair.Respawn> respawn, long now, boolean endingGated) {
		if (endingGated && status == Status.READY) {
			return !sealed && respawn.isPresent() && endingTick.isPresent()
					&& now - endingTick.getAsLong() >= respawn.get().afterEndingDays() * DAY_TICKS;
		}
		if (sealed) {
			return false;
		}
		return switch (status) {
			case READY -> true;
			case ACTIVE -> false;
			case DEFEATED -> respawn.isPresent() && endingTick.isPresent()
					&& now - Math.max(endingTick.getAsLong(), defeatedTick) >= respawn.get().afterEndingDays() * DAY_TICKS;
		};
	}
}
