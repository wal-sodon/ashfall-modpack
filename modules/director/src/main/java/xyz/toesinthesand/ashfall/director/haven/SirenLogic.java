package xyz.toesinthesand.ashfall.director.haven;

/**
 * The horde siren's decisions, free of the world so they are unit-tested with a fake horde flag: which phase
 * the countdown is in, when the siren sounds, and how long until nightfall.
 * <p>
 * Phases:
 * <ul>
 *   <li>{@link Phase#WARNING}: a horde is expected at the coming nightfall and the time of day is inside the
 *   warning window before it. The countdown shows, the siren sounds on entry and then every
 *   {@code repeatTicks}, so a player who was deep in a building still hears it.</li>
 *   <li>{@link Phase#HORDE}: the scheduler says a horde night is running. One siren on entry.</li>
 *   <li>{@link Phase#IDLE}: anything else.</li>
 * </ul>
 * The window ends at {@link #NIGHTFALL}, the tick Undead Nights starts its night (it compares the time of day
 * against 12000), so the countdown reaches zero exactly when the horde night is decided.
 */
public final class SirenLogic {
	/** Time of day when the horde scheduler starts the night. */
	public static final long NIGHTFALL = 12000;
	public static final long DAY_LENGTH = 24000;

	public enum Phase { IDLE, WARNING, HORDE }

	/**
	 * One update's outcome.
	 *
	 * @param phase           the phase after this update
	 * @param siren           sound the siren now
	 * @param entered         the phase changed on this update (show a title)
	 * @param ticksToNightfall ticks left in the warning window, 0 outside it
	 */
	public record Step(Phase phase, boolean siren, boolean entered, long ticksToNightfall) {
	}

	private Phase phase = Phase.IDLE;
	private long lastSiren = Long.MIN_VALUE;

	public Phase phase() {
		return phase;
	}

	/**
	 * Advances the state machine.
	 *
	 * @param horde       the scheduler's state
	 * @param dayTime     the overworld clock (any day; only the time of day matters)
	 * @param gameTime    a monotonic tick count, for the repeat interval
	 * @param warnTicks   how long before nightfall the warning starts
	 * @param repeatTicks siren repeat interval during the warning; 0 or less sounds it only on entry
	 */
	public Step update(HordeSnapshot horde, long dayTime, long gameTime, long warnTicks, long repeatTicks) {
		long timeOfDay = Math.floorMod(dayTime, DAY_LENGTH);
		Phase next;
		long left = 0;
		if (horde.hordeNight()) {
			next = Phase.HORDE;
		} else if (horde.hordeTonight() && timeOfDay < NIGHTFALL && timeOfDay >= NIGHTFALL - warnTicks) {
			next = Phase.WARNING;
			left = NIGHTFALL - timeOfDay;
		} else {
			next = Phase.IDLE;
		}
		boolean entered = next != phase;
		boolean siren = false;
		if (next == Phase.HORDE) {
			siren = entered;
		} else if (next == Phase.WARNING) {
			siren = entered || (repeatTicks > 0 && gameTime - lastSiren >= repeatTicks);
		}
		if (siren) {
			lastSiren = gameTime;
		}
		phase = next;
		return new Step(next, siren, entered, left);
	}

	public void reset() {
		phase = Phase.IDLE;
		lastSiren = Long.MIN_VALUE;
	}
}
