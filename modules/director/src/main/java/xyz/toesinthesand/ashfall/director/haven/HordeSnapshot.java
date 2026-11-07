package xyz.toesinthesand.ashfall.director.haven;

/**
 * What the siren needs from the horde scheduler (our own schedule since M8; Undead Nights for the MVP), read once
 * per siren update.
 *
 * @param hordeNight  a horde night is running right now
 * @param daysCounter nights left until the next horde night, as the scheduler counts them
 * @param gracePeriod grace nights left at the start of a world (no hordes while above 0)
 */
public record HordeSnapshot(boolean hordeNight, int daysCounter, int gracePeriod) {
	/** No scheduler installed: never a horde. */
	public static final HordeSnapshot NONE = new HordeSnapshot(false, Integer.MAX_VALUE, Integer.MAX_VALUE);

	/**
	 * Whether the coming nightfall will start a horde night. Our schedule reports no grace and a day counter of 1 on
	 * a horde day. The counter semantics come from the Undead Nights 2.2.6 era and still mirror its
	 * {@code HordeSpawner#tick} at night start (checked against the jar's bytecode): while the grace period
	 * runs it counts down, and the night it reaches 0 the day counter is set to 1; then the day counter counts
	 * down, and the night it reaches 0 (or is already 0) rolls the horde night. So "tonight" is grace == 1, or
	 * no grace left and a day counter of 1 or less. The final roll ({@code chanceForHordeNight}) can still say
	 * no; the warning text says "expected" for that reason.
	 */
	public boolean hordeTonight() {
		if (hordeNight) {
			return false;
		}
		return gracePeriod == 1 || (gracePeriod <= 0 && daysCounter <= 1);
	}
}
