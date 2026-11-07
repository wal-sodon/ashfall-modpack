package xyz.toesinthesand.ashfall.arms.charger;

/**
 * Turns a fractional rate (quarter units per tick) into whole energy units for one tick.
 * <p>
 * Energy moves in whole units, so a 0.25/tick charger has to move 1 unit every fourth tick. Deriving
 * the amount from the game time, rather than keeping a per-block remainder, needs no saved state and
 * gives exactly {@code quarters * ticks / 4} over any run of ticks.
 */
public final class ChargeRate {
	private ChargeRate() {
	}

	/** Whole units due on tick {@code gameTime} at {@code quartersPerTick} quarter units per tick. */
	public static long unitsThisTick(long gameTime, int quartersPerTick) {
		if (quartersPerTick <= 0 || gameTime < 0) {
			return 0;
		}
		return Math.floorDiv((gameTime + 1) * quartersPerTick, 4) - Math.floorDiv(gameTime * quartersPerTick, 4);
	}
}
