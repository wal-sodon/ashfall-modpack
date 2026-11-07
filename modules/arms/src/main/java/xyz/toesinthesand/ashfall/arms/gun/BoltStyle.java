package xyz.toesinthesand.ashfall.arms.gun;

/**
 * How a gun's bolt looks: beam length and thickness, how many trail sparks it sheds per tick and how
 * big its muzzle flash and impact are. Pure visuals, fixed per gun like the firing sound (balance
 * numbers live in {@link GunStats}). The server stamps it onto each bolt and it is synced as a byte, so
 * the client renders the right beam without knowing which gun fired.
 *
 * @param length      beam length behind the head, in blocks
 * @param coreRadius  half-width of the white-hot core, in blocks
 * @param glowRadius  half-width of the coloured glow around it, in blocks
 * @param trailSparks trail sparks spawned per client tick
 * @param flashSize   muzzle flash size, in blocks (third person; first person is drawn smaller)
 * @param impactScale multiplier on the impact burst (spark count and flash size)
 */
public enum BoltStyle {
	/** Spark Pistol, Volt Sidearm: short thin darts. */
	PISTOL(1.2f, 0.022f, 0.075f, 1, 0.28f, 0.8f),
	/** Arc Magnum: a fat pistol slug. */
	MAGNUM(1.4f, 0.035f, 0.11f, 2, 0.4f, 1.2f),
	/** Pulse Carbine: medium bolts. */
	CARBINE(1.5f, 0.028f, 0.09f, 2, 0.32f, 1.0f),
	/** Ion Rifle: a long rifle bolt. */
	RIFLE(1.7f, 0.03f, 0.095f, 2, 0.36f, 1.1f),
	/** Lancer Longrifle: a long, thin, very bright lance. */
	LANCER(2.0f, 0.02f, 0.08f, 3, 0.5f, 1.4f),
	/** Storm Repeater: short, thick, angry slugs. */
	STORM(1.2f, 0.045f, 0.14f, 2, 0.38f, 1.0f);

	private final float length;
	private final float coreRadius;
	private final float glowRadius;
	private final int trailSparks;
	private final float flashSize;
	private final float impactScale;

	BoltStyle(float length, float coreRadius, float glowRadius, int trailSparks, float flashSize, float impactScale) {
		this.length = length;
		this.coreRadius = coreRadius;
		this.glowRadius = glowRadius;
		this.trailSparks = trailSparks;
		this.flashSize = flashSize;
		this.impactScale = impactScale;
	}

	public float length() {
		return length;
	}

	public float coreRadius() {
		return coreRadius;
	}

	public float glowRadius() {
		return glowRadius;
	}

	public int trailSparks() {
		return trailSparks;
	}

	public float flashSize() {
		return flashSize;
	}

	public float impactScale() {
		return impactScale;
	}

	/** The style with this ordinal, or {@link #CARBINE} for anything out of range (e.g. a newer server). */
	public static BoltStyle byId(int id) {
		BoltStyle[] values = values();
		return id >= 0 && id < values.length ? values[id] : CARBINE;
	}
}
