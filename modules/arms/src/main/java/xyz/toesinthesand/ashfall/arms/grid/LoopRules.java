package xyz.toesinthesand.ashfall.arms.grid;

/**
 * The numbers {@link DistrictLoop} needs, in ticks, taken from {@link GridSettings} so the rules never read a
 * datapack themselves.
 *
 * @param couplers          Grid Couplers that repair a core
 * @param cells             Energy Cells that start the hold
 * @param holdTicks         attended ticks the hold needs
 * @param coreHp            core health at the start of a siege
 * @param warningTicks      how long before the siege nightfall the warning sounds
 * @param nightTicks        nightfall to dawn: how long a siege night lasts on the day clock
 * @param minPresenceTicks  attended siege ticks needed for a dawn win
 * @param relays            key relays that must be linked (capped at how many the district has)
 */
public record LoopRules(int couplers, int cells, long holdTicks, int coreHp, long warningTicks, long nightTicks,
		long minPresenceTicks, int relays) {
	public static final long DAY_TICKS = 24_000L;
	/** Time of day the horde scheduler and the siege both call nightfall (see the director's SirenLogic). */
	public static final long NIGHTFALL = 12_000L;

	/** Rules with the default relay count. */
	public LoopRules(int couplers, int cells, long holdTicks, int coreHp, long warningTicks, long nightTicks, long minPresenceTicks) {
		this(couplers, cells, holdTicks, coreHp, warningTicks, nightTicks, minPresenceTicks, GridSettings.DEFAULT_RELAYS_REQUIRED);
	}

	/** How many of {@code keyRelays} must be linked. */
	public int relaysRequired(java.util.Set<Long> keyRelays) {
		return Math.min(relays, keyRelays.size());
	}

	/** How many of {@code keyRelays} are in {@code linked}. */
	public static int linkedCount(java.util.Set<Long> linked, java.util.Set<Long> keyRelays) {
		return (int) linked.stream().filter(keyRelays::contains).count();
	}
}
