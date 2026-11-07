package xyz.toesinthesand.ashfall.director.spawn;

/**
 * The numbers of spec section 1 and the arithmetic over them, free of any Minecraft state so JUnit can
 * pin the tables down. Everything the budget loop decides numerically goes through here; the loop
 * itself only gathers inputs (light, counts, time) from the world.
 */
public final class SpawnRules {
	/** The budget loop runs once per player every this many ticks (staggered by entity id). */
	public static final int CYCLE_TICKS = 20;
	public static final long TICKS_PER_DAY = 24_000L;

	/** Ambient candidate ring around the player. */
	public static final int MIN_SPAWN_DISTANCE = 24;
	public static final int MAX_SPAWN_DISTANCE = 64;
	/** Spots tried per player per cycle, and the most zombies one cycle may add for one player. */
	public static final int ATTEMPTS_PER_CYCLE = 6;
	public static final int MAX_SPAWNS_PER_CYCLE = 2;
	/** How far above/below the player's feet the indoor column scan looks for a floor. */
	public static final int COLUMN_SCAN_HALF_HEIGHT = 10;
	/** Players further than this cannot "see" a candidate spot, so the line-of-sight ray is skipped. */
	public static final int SIGHT_CHECK_RADIUS = 128;

	/** Ambient Director zombies within this radius of a player count towards that player's ambient cap. */
	public static final int AMBIENT_RADIUS = 96;
	/** Default ambient cap on day 1, the cap it rises to, and the day it gets there (game rules override the first two). */
	public static final int AMBIENT_CAP_START = 30;
	public static final int AMBIENT_CAP_MAX = 60;
	public static final int AMBIENT_CAP_MAX_DAY = 120;

	/** Director zombies (ambient + building) within {@link #PLAYER_CAP_RADIUS} of one player. */
	public static final int PLAYER_HARD_CAP = 120;
	public static final int PLAYER_CAP_RADIUS = 128;
	/** Loaded Director zombies in the whole overworld, the co-op ceiling. */
	public static final int WORLD_HARD_CAP = 150;

	/**
	 * Full light (15) never spawns. The spec's table gives 12+ a weight of 0.02, but its own test list
	 * demands "no spawns at light 15", so 15 is a hard zero and 12-14 keep the 0.02 trickle.
	 */
	public static final int MAX_SPAWN_LIGHT = 14;
	/** Outdoors (sky visible) during the day the light weight is multiplied by this. */
	public static final float DAY_OUTDOOR_FACTOR = 0.4f;

	/** No ambient spawns this close to any online player's respawn point (bed, anchor or world spawn). */
	public static final int RESPAWN_EXCLUSION = 16;

	/** A player this close to a building's footprint triggers its population and pauses its cleared timer. */
	public static final int BUILDING_TRIGGER_RADIUS = 48;
	public static final int BUILDING_POPULATION_CAP = 12;
	/**
	 * Share of a Lost Cities storey's 16x16-per-chunk area that counts as floor space for populations (plan
	 * section 1.7). Lost Cities storeys are whole chunks including walls, stairwells and corridors; 0.6 makes a
	 * chunk storey weigh about what a furnished Arnis unit of the same footprint did.
	 */
	public static final double LC_FLOOR_FILL = 0.6;
	/** Light levels up to this count as "darkness" for a zone's darkness scale (the 0.3-weight band and below). */
	public static final int DARK_LIGHT = 7;
	/** A cleared building stays empty for this many ticks of nobody being near it (7 in-game days). */
	public static final long CLEARED_TICKS = 7 * TICKS_PER_DAY;

	private SpawnRules() {
	}

	/** Spawn weight of a spot by its light level (the max of block light and darkened sky light). */
	public static float lightWeight(int light) {
		if (light <= 0) {
			return 1.0f;
		}
		if (light <= 4) {
			return 0.6f;
		}
		if (light <= 7) {
			return 0.3f;
		}
		if (light <= 11) {
			return 0.1f;
		}
		return light <= MAX_SPAWN_LIGHT ? 0.02f : 0.0f;
	}

	/**
	 * The full spot weight: light weight, damped outdoors by day. Indoors keeps its weight by day on
	 * purpose, so dark interiors stay dangerous at noon.
	 */
	public static float spotWeight(int light, boolean day, boolean outdoor) {
		float weight = lightWeight(light);
		return day && outdoor ? weight * DAY_OUTDOOR_FACTOR : weight;
	}

	/**
	 * Day number of a tick count, 1 on the first day: ticks / 24000, counted from one. Pure arithmetic only: the
	 * world's "today" is {@link DirectorDay#today}, which feeds this the day clock, never elapsed game time (sleeping
	 * moves the clock but not game time, and difficulty must follow the nights players lived through).
	 */
	public static int day(long gameTime) {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0, gameTime) / TICKS_PER_DAY + 1);
	}

	/** Ambient cap with the default numbers. */
	public static int ambientCap(int day) {
		return ambientCap(day, AMBIENT_CAP_START, AMBIENT_CAP_MAX);
	}

	/** Linear from {@code start} on day 1 to {@code max} on {@link #AMBIENT_CAP_MAX_DAY}, flat after. */
	public static int ambientCap(int day, int start, int max) {
		if (day <= 1) {
			return start;
		}
		if (day >= AMBIENT_CAP_MAX_DAY) {
			return max;
		}
		double t = (day - 1) / (double) (AMBIENT_CAP_MAX_DAY - 1);
		return (int) Math.round(start + (max - start) * t);
	}

	/**
	 * How many more zombies one player's cycle may add: the tightest of the ambient cap, the per-player
	 * hard cap and the world hard cap, never negative.
	 */
	public static int allowance(int ambientNear, int ambientCap, int directorNear, int playerCap, int worldTotal, int worldCap) {
		int room = Math.min(ambientCap - ambientNear, Math.min(playerCap - directorNear, worldCap - worldTotal));
		return Math.max(0, room);
	}

	/** Room left under the hard caps alone, for building populations (which skip the ambient cap). */
	public static int hardCapRoom(int directorNear, int playerCap, int worldTotal, int worldCap) {
		return Math.max(0, Math.min(playerCap - directorNear, worldCap - worldTotal));
	}

	/**
	 * First population of a building: round(sum of per_100m2 x unit floor area / 100), capped.
	 *
	 * @param weightedArea the sum over units of {@code per_100m2 * unit area}
	 */
	public static int basePopulation(double weightedArea) {
		if (weightedArea <= 0) {
			return 0;
		}
		return (int) Math.min(BUILDING_POPULATION_CAP, Math.round(weightedArea / 100.0));
	}

	/**
	 * A building's base population after its zone's scale (plan: "x the zone's population_scale"). Zero scale
	 * means the zone has no populations (the wasteland and beyond are darkness-only); any positive scale keeps
	 * at least one zombie so small buildings in gentle zones are not silently empty.
	 */
	public static int scaledPopulation(int base, double scale) {
		if (base <= 0 || scale <= 0) {
			return 0;
		}
		return (int) Math.max(1, Math.round(base * scale));
	}

	/**
	 * Ambient spot weight in a zone: dark spots (light {@value #DARK_LIGHT} or less) take the zone's darkness
	 * scale, every spot takes the gentle scale, and the result is a probability so it is clamped to 1.
	 */
	public static float zoneSpotWeight(float weight, int light, float darknessScale, float gentleScale) {
		float w = weight * gentleScale;
		if (light <= DARK_LIGHT) {
			w *= darknessScale;
		}
		return Math.max(0f, Math.min(1f, w));
	}

	/** Each repopulation is half the previous one (rounded, at least one while the base is non-zero). */
	public static int populationSize(int base, int generation) {
		if (base <= 0) {
			return 0;
		}
		return (int) Math.max(1, Math.round(base / Math.pow(2, Math.max(0, generation))));
	}

	/** {@code count} times the story ending's spawn multiplier, rounded; never below 0. */
	public static int scaleBySpawnMultiplier(int count, double multiplier) {
		return Math.max(0, (int) Math.round(count * multiplier));
	}
}
