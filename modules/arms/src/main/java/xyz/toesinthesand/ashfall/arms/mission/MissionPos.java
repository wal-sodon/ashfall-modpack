package xyz.toesinthesand.ashfall.arms.mission;

/**
 * A place an objective points at, as block x and z. No height and no dimension: every target (substations, relays,
 * boss lairs, the Wreck, a Bunker Core in the overworld) is an overworld spot found by walking, and the HUD and the
 * radio only ever give a heading and a distance. Kept free of Minecraft types so the resolver is unit-tested plain.
 */
public record MissionPos(int x, int z) {
	/** Horizontal distance from ({@code fromX}, {@code fromZ}). */
	public double distanceFrom(double fromX, double fromZ) {
		return Math.hypot(x - fromX, z - fromZ);
	}
}
