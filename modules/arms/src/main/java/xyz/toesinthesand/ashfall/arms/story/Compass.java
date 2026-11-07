package xyz.toesinthesand.ashfall.arms.story;

/**
 * Eight-way compass bearings for the Field Radio's "nearest dark substation" line. A radio gives a rough
 * heading, not coordinates: players still have to cross the city, and the Grid Map is where exact
 * positions live. Minecraft's north is -Z and east is +X.
 */
public final class Compass {
	/** Bearing keys, clockwise from north; the lang file holds the words. */
	public static final String[] POINTS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	private Compass() {
	}

	/** The compass point from (fromX, fromZ) toward (toX, toZ); "n" when the points coincide. */
	public static String point(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX, dz = toZ - fromZ;
		if (dx == 0 && dz == 0) {
			return POINTS[0];
		}
		// atan2 of (east, north) gives the clockwise angle from north.
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
		return POINTS[sector];
	}

	/** Distance rounded to the nearest 10 blocks: radio direction-finding is not survey work. */
	public static int roughDistance(double fromX, double fromZ, double toX, double toZ) {
		double d = Math.hypot(toX - fromX, toZ - fromZ);
		return (int) (Math.round(d / 10.0) * 10);
	}
}
