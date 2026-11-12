package xyz.toesinthesand.ashfall.director.skills;

/**
 * Vanilla's XP curve as plain arithmetic: how many points a level is worth. Skills are priced in points, not levels,
 * so a rank costs the same at level 3 as at level 30 (five levels near 30 are worth nearly ten times five near 0).
 */
public final class XpMath {
	private XpMath() {
	}

	/** Points needed to go from level 0 to {@code level} (the sum of vanilla's per-level costs). */
	public static int pointsAtLevel(int level) {
		if (level <= 0) {
			return 0;
		}
		long l = level;
		if (level <= 16) {
			return (int) (l * l + 6 * l);
		}
		if (level <= 31) {
			return (int) Math.round(2.5 * l * l - 40.5 * l + 360);
		}
		return (int) Math.round(4.5 * l * l - 162.5 * l + 2220);
	}

	/** Points needed to go from {@code level} to the next one (vanilla's {@code getXpNeededForNextLevel}). */
	public static int pointsForNextLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** Points held by someone at {@code level} with {@code progress} (0 to 1) of the way to the next. */
	public static int points(int level, float progress) {
		return pointsAtLevel(level) + Math.round(progress * pointsForNextLevel(level));
	}

	/** The highest level whose cumulative points fit in {@code points}. */
	public static int levelFor(int points) {
		int level = 0;
		while (pointsAtLevel(level + 1) <= points) {
			level++;
		}
		return level;
	}
}
