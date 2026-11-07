package xyz.toesinthesand.ashfall.arms.gun;

/**
 * The Optic's scope numbers: which magnifications a scoped blaster offers, how the view eases between
 * them, when the scope overlay shows and how much mouse sensitivity drops. Pure (no Minecraft classes) so
 * JUnit covers it; the client's {@code ScopeClient} drives it every frame.
 * <p>
 * Magnification is the inverse of the field-of-view multiplier: 4x means the view is a quarter as wide.
 * An attachment's {@code zoom} is the FOV multiplier (0.25 = 4x); a gun's {@code scope_zoom} is a
 * magnification (7 = 7x), because "a 7x rifle" is how players and balance tables talk about it.
 */
public final class ScopeMath {
	/** Closest the scope zooms, whatever a datapack asks for. */
	public static final float MAX_MAGNIFICATION = 12f;
	/** The lower scroll step never goes below this, so it always reads as a scope. */
	public static final float MIN_LOW_MAGNIFICATION = 1.5f;
	/** The scope overlay and sensitivity drop start once the view is this magnified. */
	public static final float OVERLAY_THRESHOLD = 1.4f;
	/** How fast the view approaches its target, per second (about 0.25 s to 95%). */
	public static final float EASE_RATE = 12f;
	/** Vignette size at the start of the zoom and when fully zoomed, as a fraction of the shorter screen side. */
	public static final float OVERLAY_SCALE_START = 0.7f;
	public static final float OVERLAY_SCALE_FULL = 1.1f;

	private ScopeMath() {
	}

	/**
	 * The two scope steps for a blaster. The high step is the gun's own {@code scope_zoom} when it has one
	 * (marksman rifles), else the strongest fitted optic; the low step is half of it. {@code levels()} is 1
	 * when halving would drop below {@link #MIN_LOW_MAGNIFICATION}.
	 *
	 * @param opticFov     strongest fitted FOV multiplier ({@link AttachmentMath#zoom}); 1 = no optic
	 * @param gunScopeZoom the gun's {@code scope_zoom} magnification, or 0 when it has none
	 * @return the steps, or null when the gun has no optic (no scope)
	 */
	public static Levels levels(float opticFov, float gunScopeZoom) {
		if (opticFov >= 1f) {
			return null;
		}
		float high = gunScopeZoom > 0f ? gunScopeZoom : 1f / opticFov;
		high = clamp(high, 1f, MAX_MAGNIFICATION);
		float low = high / 2f;
		return low < MIN_LOW_MAGNIFICATION ? new Levels(high, high) : new Levels(low, high);
	}

	/** One frame of easing from {@code current} toward {@code target}, frame-rate independent; snaps when close. */
	public static float ease(float current, float target, float dtSeconds) {
		float dt = clamp(dtSeconds, 0f, 0.25f);
		float next = current + (target - current) * (1f - (float) Math.exp(-EASE_RATE * dt));
		return Math.abs(target - next) < 0.002f ? target : next;
	}

	/** Field-of-view multiplier for a magnification. */
	public static float fovMultiplier(float magnification) {
		return 1f / Math.max(1f, magnification);
	}

	/**
	 * Mouse sensitivity multiplier: the inverse of the zoom, so a mouse movement sweeps the same distance
	 * across the screen at 4x as unscoped (like the spyglass, but scaled to the zoom). It follows the eased
	 * zoom, so it changes smoothly instead of jumping.
	 */
	public static double sensitivity(float magnification) {
		return 1.0 / Math.max(1f, magnification);
	}

	/** Whether the scope overlay is up (and the crosshair and held blaster are hidden). */
	public static boolean overlayVisible(float magnification) {
		return magnification >= OVERLAY_THRESHOLD;
	}

	/**
	 * Size of the scope vignette as a fraction of the shorter screen side: it opens from
	 * {@link #OVERLAY_SCALE_START} to {@link #OVERLAY_SCALE_FULL} as the view settles on {@code target}.
	 */
	public static float overlayScale(float magnification, float target) {
		if (target <= OVERLAY_THRESHOLD) {
			return OVERLAY_SCALE_FULL;
		}
		float progress = clamp((magnification - OVERLAY_THRESHOLD) / (target - OVERLAY_THRESHOLD), 0f, 1f);
		return OVERLAY_SCALE_START + (OVERLAY_SCALE_FULL - OVERLAY_SCALE_START) * progress;
	}

	/** Label for a magnification: "4x", "3.5x". */
	public static String label(float magnification) {
		float rounded = Math.round(magnification * 10f) / 10f;
		return (rounded == Math.rint(rounded) ? String.valueOf((int) rounded) : String.valueOf(rounded)) + "x";
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/**
	 * The scope's steps. Index 1 (the high step) is where aiming starts; scrolling down picks index 0.
	 */
	public record Levels(float low, float high) {
		public int count() {
			return low == high ? 1 : 2;
		}

		/** Magnification at step {@code index}, clamped into range. */
		public float magnification(int index) {
			return index <= 0 && count() == 2 ? low : high;
		}

		/** The step after one scroll notch: up ({@code > 0}) zooms in, down zooms out. */
		public int step(int index, double scroll) {
			if (count() == 1 || scroll == 0) {
				return count() == 1 ? 1 : index;
			}
			return scroll > 0 ? 1 : 0;
		}

		/** "2x / 4x", or "4x" with one step. */
		public String describe() {
			return count() == 1 ? label(high) : label(low) + " / " + label(high);
		}
	}
}
