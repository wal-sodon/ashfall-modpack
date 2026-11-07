package xyz.toesinthesand.ashfall.arms.gun;

/**
 * Decides whether a bolt struck a target's head, from the impact height alone.
 * <p>
 * Minecraft hitboxes are a single box, so the head is approximated as the band just below eye level:
 * anything at or above {@code eyeY - margin} counts, where the margin is 15% of the body height but at
 * most 0.3 blocks. For a player (eyes at 1.62, height 1.8) that is everything above 1.35.
 */
public final class Headshots {
	public static final double MAX_MARGIN = 0.3;
	public static final double MARGIN_FRACTION = 0.15;

	private Headshots() {
	}

	public static boolean isHeadshot(double hitY, double eyeY, double height) {
		double margin = Math.min(MAX_MARGIN, height * MARGIN_FRACTION);
		return hitY >= eyeY - margin;
	}
}
