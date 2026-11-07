package xyz.toesinthesand.ashfall.director.senses;

/**
 * What a zombie can perceive. Each kind carries its own lifetime: a noise is investigated for a few
 * seconds after it happened, while light is re-emitted by its source every 40 ticks and so only needs
 * to outlive one emission interval (a light that stops being refreshed vanishes quickly).
 */
public enum StimulusType {
	/** Heard through walls: investigated whenever it is within hearing range. */
	SOUND(100),
	/** Seen: only acted on with a clear line of sight (see {@link LightSight}). */
	LIGHT(40),
	/**
	 * Smelled: the blood a badly wounded player leaves behind ({@link BloodTrails}). Small radius, no line of sight,
	 * and a long life (30 s) so the drops form a trail a zombie can follow from one to the next.
	 */
	BLOOD(600);

	private final int lifetimeTicks;

	StimulusType(int lifetimeTicks) {
		this.lifetimeTicks = lifetimeTicks;
	}

	/** Ticks a stimulus of this kind stays active after it was emitted or last refreshed. */
	public int lifetimeTicks() {
		return lifetimeTicks;
	}
}
