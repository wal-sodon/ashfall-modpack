package xyz.toesinthesand.ashfall.arms.gun;

/**
 * Colour ramp for the HUD heat bar: blue when cold, orange at half, red when nearly overheated.
 * Pure arithmetic so it can be unit-tested away from the client.
 */
public final class HeatColors {
	public static final int COLD = 0x3C8CFF;
	public static final int WARM = 0xFF9A3C;
	public static final int HOT = 0xFF3C3C;

	private HeatColors() {
	}

	/** Opaque ARGB colour for a heat fraction in [0, 1] (clamped). */
	public static int color(float fraction) {
		float f = Math.max(0f, Math.min(1f, fraction));
		int rgb = f < 0.5f ? lerp(COLD, WARM, f / 0.5f) : lerp(WARM, HOT, (f - 0.5f) / 0.5f);
		return 0xFF000000 | rgb;
	}

	private static int lerp(int from, int to, float t) {
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}
}
