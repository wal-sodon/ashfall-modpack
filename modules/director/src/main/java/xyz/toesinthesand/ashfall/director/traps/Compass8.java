package xyz.toesinthesand.ashfall.director.traps;

import net.minecraft.network.chat.Component;

/**
 * Turns a horizontal offset into one of eight compass words ("north-east"), for the Tripline Alarm's action-bar
 * line. North is -Z and east is +X, as on the F3 screen. Director can't use arms' compass, so this is its own.
 */
public final class Compass8 {
	/** Clockwise from north; the lang keys are {@code direction.ashfall.<name>}. */
	private static final String[] NAMES = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

	private Compass8() {
	}

	/** The compass point nearest the direction of {@code (dx, dz)}; north for a zero offset. */
	public static String name(double dx, double dz) {
		if (dx == 0.0 && dz == 0.0) {
			return NAMES[0];
		}
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		int index = Math.floorMod((int) Math.round(degrees / 45.0), 8);
		return NAMES[index];
	}

	public static Component component(double dx, double dz) {
		return Component.translatable("direction.ashfall." + name(dx, dz));
	}
}
