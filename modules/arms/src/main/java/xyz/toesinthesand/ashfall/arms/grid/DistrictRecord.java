package xyz.toesinthesand.ashfall.arms.grid;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One district's saved loop progress. Plain mutable fields with no Minecraft types, so {@link DistrictLoop}
 * (the rules) is unit-tested without a server; {@link GridState} owns the codec and the dirty flag.
 * <p>
 * Times come in two clocks on purpose. {@link #siegeAt} is on the overworld day clock, because a siege
 * belongs to a particular nightfall. Hold and siege progress are counted in ticks the controller actually
 * ran ({@link #holdTicks}, {@link #siegeTicks}, {@link #presenceTicks}), so sleeping, which jumps the day clock,
 * can never fast-forward a hold or a siege.
 */
public final class DistrictRecord {
	/** {@link #corePos} before any Substation Core registered (no block can pack to this). */
	public static final long NO_CORE = Long.MIN_VALUE;
	/** {@link #siegeAt} / {@link #reclaimedAtDay} when not set. */
	public static final long UNSET = -1L;

	public DistrictState state = DistrictState.DARK;
	/** Grid Couplers socketed so far. */
	public int couplers;
	/** Energy Cells loaded so far; the hold starts when the last one goes in. */
	public int cells;
	/** Ticks of hold done (only ticks with a player near the core count). */
	public long holdTicks;
	/** Key relays linked, as packed chunk positions (one key relay per chunk, see RelayPlan). */
	public final Set<Long> linked = new TreeSet<>();
	/** Day-clock tick of the nightfall the siege starts at, or {@link #UNSET}. */
	public long siegeAt = UNSET;
	/** The siege warning for {@link #siegeAt} has been sounded. */
	public boolean warned;
	/** Core health during a siege. */
	public int coreHp;
	/** Siege waves spawned so far. */
	public int wavesSpawned;
	/** {@link #siegeTicks} when the last wave spawned. */
	public long lastWaveAt;
	/** Ticks the current siege has run. */
	public long siegeTicks;
	/** Siege ticks with a player near the core: a siege nobody attends can't be won. */
	public long presenceTicks;
	/** World day (1-based) the district was reclaimed, or {@link #UNSET}. */
	public long reclaimedAtDay = UNSET;
	/** Packed block position of the district's Substation Core, or {@link #NO_CORE}. */
	public long corePos = NO_CORE;
	/** The per-player reward cache has been placed. */
	public boolean rewarded;
	/**
	 * The running SIEGE is a Breach (endgame-design section 5): a horde hitting a reclaimed core. Losing it breaks the
	 * district (BROKEN) instead of setting it back to REPAIRED, and holding it returns the district to RECLAIMED.
	 */
	public boolean breach;
	/**
	 * Condition (0 to {@link #FULL_CONDITION}) of linked key relays that have worn, by packed chunk; a relay not in
	 * the map is in full condition. A relay at 0 is offline (see {@link GridDecay}).
	 */
	public final Map<Long, Integer> relayCondition = new TreeMap<>();
	/** World day decay was last applied for, or {@link #UNSET} before the first decay check. */
	public long wornThroughDay = UNSET;
	/** World day the district started flickering (too few working relays), or {@link #UNSET}. */
	public long flickerSinceDay = UNSET;
	/**
	 * Not saved: set for the one state change that returns a district to RECLAIMED after a Breach or a re-repair, so
	 * the Reclaimed effects can tell "back on the grid" from a first reclaim (no second fanfare, no second reward).
	 */
	public transient boolean restoring;

	/** Full relay condition, in percent. */
	public static final int FULL_CONDITION = 100;

	/** The condition of the relay in {@code chunk}, in percent. */
	public int condition(long chunk) {
		return relayCondition.getOrDefault(chunk, FULL_CONDITION);
	}

	/** Sets a relay's condition, clamped; full condition is stored as absence. */
	public void setCondition(long chunk, int condition) {
		int c = Math.clamp(condition, 0, FULL_CONDITION);
		if (c >= FULL_CONDITION) {
			relayCondition.remove(chunk);
		} else {
			relayCondition.put(chunk, c);
		}
	}

	public boolean hasCore() {
		return corePos != NO_CORE;
	}

	/** Whether every cell is loaded, which is what starts the hold. */
	public boolean energizing(LoopRules rules) {
		return cells >= rules.cells();
	}

	public boolean holdDone(LoopRules rules) {
		return energizing(rules) && holdTicks >= rules.holdTicks();
	}

	/** Back to a fresh, dark district; the core position is kept because the block is still there. */
	public void reset() {
		long core = corePos;
		state = DistrictState.DARK;
		couplers = cells = coreHp = wavesSpawned = 0;
		holdTicks = lastWaveAt = siegeTicks = presenceTicks = 0;
		linked.clear();
		relayCondition.clear();
		siegeAt = reclaimedAtDay = wornThroughDay = flickerSinceDay = UNSET;
		warned = rewarded = breach = restoring = false;
		corePos = core;
	}
}
