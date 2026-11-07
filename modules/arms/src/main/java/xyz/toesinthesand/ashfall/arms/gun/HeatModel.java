package xyz.toesinthesand.ashfall.arms.gun;

/**
 * Pure heat arithmetic for energy guns, kept free of Minecraft types so it can be unit-tested.
 * <p>
 * Heat is never ticked. A gun stores the heat it had at its last shot and that shot's game tick; the
 * current heat is derived on demand by subtracting linear cooling for the elapsed time. That way a gun
 * cools in a chest, in another player's hands or in an unloaded chunk without any item ticking, and
 * the client can draw the same number the server will use.
 * <p>
 * Reaching capacity locks the gun. A locked gun cools at {@link #LOCKED_COOL_FACTOR} of its normal rate
 * and unlocks only once it is fully cold, so overheating costs a real pause instead of a few ticks.
 */
public final class HeatModel {
	/** Cooling multiplier while locked out (spec: 0.8x cool/s). */
	public static final float LOCKED_COOL_FACTOR = 0.8f;
	public static final float TICKS_PER_SECOND = 20f;

	/** The outcome of one shot: heat after the shot, whether the gun is now locked, and whether this shot locked it. */
	public record Shot(float heat, boolean locked, boolean justLocked) {
	}

	private HeatModel() {
	}

	/**
	 * Heat at {@code now}, given {@code heat} recorded at tick {@code since}. Never negative.
	 * <p>
	 * A shot tick in the future means the gun came from a world with a later clock (another save, a
	 * restored backup); without this it would stay hot, and a locked one stay locked, until this world's
	 * clock caught up. Such a gun is treated as long cold.
	 */
	public static float heatAt(float heat, long since, boolean locked, long now, float coolPerSecond) {
		if (now < since) {
			return 0f;
		}
		long elapsed = now - since;
		float rate = locked ? coolPerSecond * LOCKED_COOL_FACTOR : coolPerSecond;
		return Math.max(0f, heat - rate * (elapsed / TICKS_PER_SECOND));
	}

	/** Whether the lockout still holds at {@code now}: it only clears once the gun has fully cooled. */
	public static boolean lockedAt(float heat, long since, boolean locked, long now, float coolPerSecond) {
		return locked && heatAt(heat, since, true, now, coolPerSecond) > 0f;
	}

	/**
	 * Applies one shot at {@code now}. The caller must already have checked {@link #lockedAt}; a shot that
	 * reaches {@code capacity} clamps there and locks the gun.
	 */
	public static Shot fire(float heat, long since, boolean locked, long now, float coolPerSecond,
							float heatPerShot, float capacity) {
		boolean stillLocked = lockedAt(heat, since, locked, now, coolPerSecond);
		float current = heatAt(heat, since, stillLocked, now, coolPerSecond);
		float next = current + heatPerShot;
		if (next >= capacity) {
			return new Shot(capacity, true, !stillLocked);
		}
		return new Shot(next, stillLocked, false);
	}

	/** Game ticks between shots for a rate of fire, rounded to whole ticks and never below one. */
	public static int ticksBetweenShots(float rpm) {
		if (rpm <= 0f) {
			return Integer.MAX_VALUE;
		}
		return Math.max(1, Math.round(1200f / rpm));
	}
}
