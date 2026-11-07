package xyz.toesinthesand.ashfall.arms.gun;

/**
 * Fractional energy costs on whole-unit packs.
 * <p>
 * Team Reborn Energy stores whole {@code long} units, but some guns cost 1.5 per shot. Rather than scale
 * every number (which would break the shared pack capacities chargers rely on), the gun keeps a small
 * credit: when a shot draws a whole unit more than it needs, the surplus pays toward the next shot. A
 * 1.5-cost gun therefore draws 2, 1, 2, 1... and a 30-unit pack gives exactly 20 shots.
 */
public final class EnergyCost {
	private static final float EPSILON = 1e-4f;

	private EnergyCost() {
	}

	/** Whole units to draw from the pack for a shot costing {@code cost}, after spending {@code credit}. */
	public static long unitsToDraw(float cost, float credit) {
		float owed = cost - credit;
		if (owed <= EPSILON) {
			return 0L;
		}
		return (long) Math.ceil(owed - EPSILON);
	}

	/** Credit left after drawing {@code units} for a shot costing {@code cost}. Always in [0, 1). */
	public static float creditAfter(float cost, float credit, long units) {
		float left = credit + units - cost;
		return left < EPSILON ? 0f : left;
	}
}
