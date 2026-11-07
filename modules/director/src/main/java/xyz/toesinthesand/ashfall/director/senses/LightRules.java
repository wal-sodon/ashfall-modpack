package xyz.toesinthesand.ashfall.director.senses;

/**
 * When a player's light gives them away, and how far (spec section 2). Pure arithmetic so the rules are
 * unit tested apart from the level.
 * <p>
 * Light only matters around players (the user's rule): placed lamps far from anyone attract nothing,
 * so bases can be lit without becoming beacons while empty. Daylight outdoors drowns everything out;
 * by day only a bright interior shows, and less far.
 */
public final class LightRules {
	/** At night a light this bright (a held torch is 14, a lantern 15) is noticed. */
	public static final int NIGHT_THRESHOLD = 7;
	/** By day only a bright interior shows through the windows. */
	public static final int DAY_INDOOR_THRESHOLD = 10;
	/** Radius per light level, in blocks. */
	public static final double RADIUS_PER_LEVEL = 4.0;
	/** Daytime radius multiplier. */
	public static final double DAY_FACTOR = 0.3;

	private LightRules() {
	}

	/**
	 * LIGHT radius for a player standing in (or holding) light of {@code level}, or 0 when it attracts
	 * nothing.
	 *
	 * @param night   the sky is dark ({@code Level#isDarkOutside})
	 * @param indoors the player has no sky above them
	 */
	public static double radius(int level, boolean night, boolean indoors) {
		if (night) {
			return level >= NIGHT_THRESHOLD ? level * RADIUS_PER_LEVEL : 0.0;
		}
		if (indoors && level >= DAY_INDOOR_THRESHOLD) {
			return level * RADIUS_PER_LEVEL * DAY_FACTOR;
		}
		return 0.0;
	}
}
