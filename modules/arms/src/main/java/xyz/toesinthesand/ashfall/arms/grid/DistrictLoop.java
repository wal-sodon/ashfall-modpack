package xyz.toesinthesand.ashfall.arms.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The district loop's rules (endgame-design section 3) as pure functions over a {@link DistrictRecord}:
 * repair, energize, link, siege, and what failing costs. No world access, so every transition is unit-tested;
 * the controller supplies "is anyone near", "how many siege zombies are left" and the clocks.
 * <p>
 * Failing costs time, not the run: a destroyed core drops back to REPAIRED with its couplers and links kept,
 * and only the cells and the hold have to be redone. A siege nobody attended is postponed, not lost. A Breach
 * (a horde hitting a reclaimed core) that destroys the core breaks the district (BROKEN) until it is re-repaired at
 * half the coupler cost.
 */
public final class DistrictLoop {
	/** What a player action did, for the message the player sees. */
	public enum Result {
		/** Accepted; more of the same is needed. */
		ACCEPTED,
		/** Accepted, and it completed this step (the last coupler, the last cell, the last link). */
		COMPLETED,
		/** The district is not at the step this action belongs to. */
		WRONG_STATE,
		/** This step already has everything it needs. */
		FULL,
		/** Linking: this relay is not one of the district's key relays. */
		NOT_KEY_RELAY,
		/** Linking: this relay is already linked. */
		ALREADY_LINKED
	}

	/** How a siege tick ended. */
	public enum SiegeOutcome { CONTINUE, WON, ABANDONED }

	private DistrictLoop() {
	}

	/**
	 * Couplers that re-repair a BROKEN core: half the full repair, rounded up (endgame-design section 5, "re-repaired at
	 * half cost"). The district already proved itself once, so a lost Breach costs a setback, not the whole loop.
	 */
	public static int reRepairCouplers(LoopRules rules) {
		return Math.max(1, (rules.couplers() + 1) / 2);
	}

	/**
	 * Repair: one coupler into the core. A BROKEN core takes {@link #reRepairCouplers} and then goes straight back to
	 * RECLAIMED: its relays are still linked and its siege was won before, so only the broken core needs fixing.
	 */
	public static Result socket(DistrictRecord r, LoopRules rules) {
		if (r.state == DistrictState.BROKEN) {
			r.couplers++;
			if (r.couplers >= reRepairCouplers(rules)) {
				r.couplers = rules.couplers();
				r.state = DistrictState.RECLAIMED;
				r.restoring = true;
				return Result.COMPLETED;
			}
			return Result.ACCEPTED;
		}
		if (r.state != DistrictState.DARK) {
			return r.state == DistrictState.REPAIRED ? Result.FULL : Result.WRONG_STATE;
		}
		r.couplers++;
		if (r.couplers >= rules.couplers()) {
			r.couplers = rules.couplers();
			r.state = DistrictState.REPAIRED;
			return Result.COMPLETED;
		}
		return Result.ACCEPTED;
	}

	/** Energize: one Energy Cell into a repaired core; the last one starts the hold. */
	public static Result loadCell(DistrictRecord r, LoopRules rules) {
		if (r.state != DistrictState.REPAIRED) {
			return Result.WRONG_STATE;
		}
		if (r.energizing(rules)) {
			return Result.FULL;
		}
		r.cells++;
		if (r.energizing(rules)) {
			r.holdTicks = 0;
			return Result.COMPLETED;
		}
		return Result.ACCEPTED;
	}

	/**
	 * Advances the hold by {@code ticks} if a player is near. @return true if the hold finished on this call
	 */
	public static boolean tickHold(DistrictRecord r, LoopRules rules, boolean playerNear, long ticks) {
		if (r.state != DistrictState.REPAIRED || !r.energizing(rules) || r.holdDone(rules) || !playerNear) {
			return false;
		}
		r.holdTicks = Math.min(rules.holdTicks(), r.holdTicks + ticks);
		return r.holdDone(rules);
	}

	/**
	 * Link: touch one key relay (packed chunk) with a coupler. Allowed as soon as the core is repaired, so the relay
	 * run can come first, in daylight, before the noisy hold. Only {@link LoopRules#relaysRequired} of them are needed.
	 */
	public static Result link(DistrictRecord r, LoopRules rules, long relayChunk, Set<Long> keyRelays) {
		boolean won = r.state == DistrictState.RECLAIMED || r.state == DistrictState.BLACKOUT;
		if (r.state != DistrictState.REPAIRED && !won) {
			return Result.WRONG_STATE;
		}
		if (!keyRelays.contains(relayChunk)) {
			return Result.NOT_KEY_RELAY;
		}
		if (!r.linked.add(relayChunk)) {
			return Result.ALREADY_LINKED;
		}
		if (won) {
			// A spare relay on a won district: one more that has to fail before the lights do.
			r.setCondition(relayChunk, DistrictRecord.FULL_CONDITION);
			return Result.ACCEPTED;
		}
		return LoopRules.linkedCount(r.linked, keyRelays) == rules.relaysRequired(keyRelays) ? Result.COMPLETED : Result.ACCEPTED;
	}

	/** Whether the hold is done and enough key relays are linked, i.e. the district may become ENERGIZED. */
	public static boolean readyToEnergize(DistrictRecord r, LoopRules rules, Set<Long> keyRelays) {
		return r.state == DistrictState.REPAIRED && r.holdDone(rules) && relaysDone(r, rules, keyRelays);
	}

	/** Whether enough key relays are linked. */
	public static boolean relaysDone(DistrictRecord r, LoopRules rules, Set<Long> keyRelays) {
		return LoopRules.linkedCount(r.linked, keyRelays) >= rules.relaysRequired(keyRelays);
	}

	/** ENERGIZED, with the siege at the coming nightfall ({@code clock} is the day clock). */
	public static void energize(DistrictRecord r, long clock) {
		r.state = DistrictState.ENERGIZED;
		r.siegeAt = nextNightfall(clock);
		r.warned = false;
	}

	/** The first nightfall after {@code clock}: today's if it is still day, else tomorrow's. */
	public static long nextNightfall(long clock) {
		long day = Math.floorDiv(clock, LoopRules.DAY_TICKS);
		long timeOfDay = Math.floorMod(clock, LoopRules.DAY_TICKS);
		return (timeOfDay < LoopRules.NIGHTFALL ? day : day + 1) * LoopRules.DAY_TICKS + LoopRules.NIGHTFALL;
	}

	/** Whether the siege warning should sound now (once per scheduled siege). */
	public static boolean shouldWarn(DistrictRecord r, LoopRules rules, long clock) {
		return r.state == DistrictState.ENERGIZED && !r.warned && r.siegeAt != DistrictRecord.UNSET
				&& clock >= r.siegeAt - rules.warningTicks() && clock < r.siegeAt;
	}

	/**
	 * Whether the scheduled siege starts now. If the whole siege night already went by without it (everyone
	 * slept, or the server was down), it is moved to the next nightfall instead: skipping the night never
	 * skips the siege.
	 */
	public static boolean shouldStart(DistrictRecord r, LoopRules rules, long clock) {
		if (r.state != DistrictState.ENERGIZED || r.siegeAt == DistrictRecord.UNSET || clock < r.siegeAt) {
			return false;
		}
		if (clock >= r.siegeAt + rules.nightTicks()) {
			r.siegeAt = nextNightfall(clock);
			r.warned = false;
			return false;
		}
		return true;
	}

	public static void startSiege(DistrictRecord r, LoopRules rules) {
		startSiege(r, rules, rules.coreHp());
	}

	/** Starts the siege with a core of {@code coreHp} (the siege tier's, {@link SiegeSettings.Tier#coreHp}). */
	public static void startSiege(DistrictRecord r, LoopRules rules, int coreHp) {
		r.state = DistrictState.SIEGE;
		r.coreHp = coreHp > 0 ? coreHp : rules.coreHp();
		r.wavesSpawned = 0;
		r.lastWaveAt = 0;
		r.siegeTicks = 0;
		r.presenceTicks = 0;
	}

	/**
	 * A Breach (endgame-design section 5): a horde hits a RECLAIMED core. It runs as a SIEGE flagged {@link
	 * DistrictRecord#breach}, starting now ({@code clock} on the day clock) and lasting until dawn, with a fresh core.
	 * The district stops counting as reclaimed while it lasts (no safe compound, no free power): it is under attack.
	 * <p>
	 * The siege night is anchored to this night's nightfall ({@link #breachNight}), not to {@code clock}: a Breach
	 * started late (by an admin, or after a restart) still ends at dawn. Anchoring at {@code clock} made a midnight
	 * Breach run 11000 ticks, to the next noon, which the Phase 4.5 playtest caught.
	 *
	 * @return false if the district is not reclaimed
	 */
	public static boolean startBreach(DistrictRecord r, LoopRules rules, long clock) {
		return startBreach(r, rules, clock, rules.coreHp());
	}

	/** {@link #startBreach} with a core of {@code coreHp} (the Breach tier's). */
	public static boolean startBreach(DistrictRecord r, LoopRules rules, long clock, int coreHp) {
		if (r.state != DistrictState.RECLAIMED) {
			return false;
		}
		r.breach = true;
		r.siegeAt = breachNight(rules, clock);
		startSiege(r, rules, coreHp);
		return true;
	}

	/**
	 * The nightfall a Breach at {@code clock} belongs to: tonight's if it is night now (so it ends at this dawn),
	 * else the next one (a daytime Breach lasts through the coming night).
	 */
	public static long breachNight(LoopRules rules, long clock) {
		long timeOfDay = Math.floorMod(clock, LoopRules.DAY_TICKS);
		if (timeOfDay >= LoopRules.NIGHTFALL && timeOfDay < LoopRules.NIGHTFALL + rules.nightTicks()) {
			return clock - timeOfDay + LoopRules.NIGHTFALL;
		}
		return nextNightfall(clock);
	}

	/** Counts {@code ticks} of siege, attended or not. */
	public static void tickSiege(DistrictRecord r, boolean playerNear, long ticks) {
		r.siegeTicks += ticks;
		if (playerNear) {
			r.presenceTicks += ticks;
		}
	}

	/**
	 * Damages the core. At 0 the siege is lost: back to REPAIRED with the couplers and links kept, the cells
	 * spent and the hold to be redone. A lost Breach breaks the district instead: BROKEN, dark again, with no
	 * couplers socketed (re-repair takes {@link #reRepairCouplers}); links and the reward flag are kept.
	 * @return true if this destroyed the core
	 */
	public static boolean damageCore(DistrictRecord r, int amount) {
		if (r.state != DistrictState.SIEGE || amount <= 0) {
			return false;
		}
		r.coreHp = Math.max(0, r.coreHp - amount);
		if (r.coreHp > 0) {
			return false;
		}
		if (r.breach) {
			r.state = DistrictState.BROKEN;
			r.breach = false;
			r.couplers = 0;
			r.cells = 0;
			r.holdTicks = 0;
			r.siegeAt = DistrictRecord.UNSET;
			r.warned = false;
			r.wavesSpawned = 0;
			return true;
		}
		r.state = DistrictState.REPAIRED;
		r.cells = 0;
		r.holdTicks = 0;
		r.siegeAt = DistrictRecord.UNSET;
		r.warned = false;
		r.wavesSpawned = 0;
		return true;
	}

	/**
	 * The siege's result after this tick: won once every wave is spawned and dead, or at dawn if players held
	 * the core long enough; at dawn without enough presence the siege is abandoned (postponed).
	 */
	public static SiegeOutcome siegeOutcome(DistrictRecord r, LoopRules rules, long clock, int alive, int totalWaves) {
		if (r.state != DistrictState.SIEGE) {
			return SiegeOutcome.CONTINUE;
		}
		if (r.wavesSpawned >= totalWaves && alive <= 0) {
			return SiegeOutcome.WON;
		}
		if (r.siegeAt != DistrictRecord.UNSET && clock >= r.siegeAt + rules.nightTicks()) {
			// A Breach is held if the core is still standing at dawn, attended or not: the district was already won.
			return r.breach || r.presenceTicks >= rules.minPresenceTicks() ? SiegeOutcome.WON : SiegeOutcome.ABANDONED;
		}
		return SiegeOutcome.CONTINUE;
	}

	/** RECLAIMED on world day {@code day}; a held Breach returns to RECLAIMED and keeps its original day. */
	public static void reclaim(DistrictRecord r, long day) {
		if (r.breach) {
			r.breach = false;
			r.restoring = true;
			if (r.reclaimedAtDay == DistrictRecord.UNSET) {
				r.reclaimedAtDay = day;
			}
		} else {
			r.reclaimedAtDay = day;
		}
		r.state = DistrictState.RECLAIMED;
		r.siegeAt = DistrictRecord.UNSET;
	}

	/** An unattended siege goes back to ENERGIZED and waits for the next nightfall. */
	public static void abandon(DistrictRecord r, long clock) {
		r.state = DistrictState.ENERGIZED;
		r.siegeAt = nextNightfall(clock);
		r.warned = false;
		r.wavesSpawned = 0;
	}

	/** What a decay update changed, for the broadcast. */
	public enum DecayChange { NONE, FLICKER_STARTED, FLICKER_ENDED, BLACKOUT, RESTORED }

	/** Linked key relays that still work (condition above 0). */
	public static int workingRelays(DistrictRecord r, Set<Long> keyRelays) {
		int n = 0;
		for (long chunk : r.linked) {
			if (keyRelays.contains(chunk) && r.condition(chunk) > 0) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Daily wear up to world day {@code day}: every linked key relay of a RECLAIMED district loses
	 * {@link DecaySettings#wearPerDay} for each day since the last check. The first check only starts the count, so an
	 * old world (or a fresh reclaim) is never charged for days before decay existed; the grace days after the first
	 * reclaim are free. Other states do not wear, and their days are not owed later.
	 * @return true if any relay changed
	 */
	public static boolean wear(DistrictRecord r, DecaySettings d, Set<Long> keyRelays, long day) {
		long since = r.wornThroughDay;
		r.wornThroughDay = day;
		if (r.state != DistrictState.RECLAIMED || since == DistrictRecord.UNSET || day <= since || d.wearPerDay() <= 0) {
			return false;
		}
		long graceEnd = r.reclaimedAtDay == DistrictRecord.UNSET ? since : r.reclaimedAtDay + d.graceDays();
		long days = day - Math.max(since, graceEnd);
		if (days <= 0) {
			return false;
		}
		int loss = (int) Math.min(DistrictRecord.FULL_CONDITION, days * d.wearPerDay());
		boolean changed = false;
		for (long chunk : r.linked) {
			if (keyRelays.contains(chunk) && r.condition(chunk) > 0) {
				r.setCondition(chunk, r.condition(chunk) - loss);
				changed = true;
			}
		}
		return changed;
	}

	/** A horde night through the district: {@code count} random working relays lose {@code amount}. @return relays hit */
	public static int damageRelays(DistrictRecord r, Set<Long> keyRelays, int count, int amount, Random random) {
		List<Long> working = new ArrayList<>();
		for (long chunk : r.linked) {
			if (keyRelays.contains(chunk) && r.condition(chunk) > 0) {
				working.add(chunk);
			}
		}
		Collections.shuffle(working, random);
		int hit = Math.min(count, working.size());
		for (int k = 0; k < hit; k++) {
			long chunk = working.get(k);
			r.setCondition(chunk, r.condition(chunk) - amount);
		}
		return hit;
	}

	/**
	 * Moves a won district between RECLAIMED, flickering and BLACKOUT by its working relays on world day {@code day}:
	 * too few working starts the flicker; after {@link DecaySettings#flickerDays} of it the district blacks out; enough
	 * working again ends the flicker, or brings a blacked-out district straight back to RECLAIMED (no new siege).
	 */
	public static DecayChange updateDecay(DistrictRecord r, LoopRules rules, DecaySettings d, Set<Long> keyRelays, long day) {
		boolean enough = workingRelays(r, keyRelays) >= rules.relaysRequired(keyRelays);
		if (r.state == DistrictState.BLACKOUT) {
			if (!enough) {
				return DecayChange.NONE;
			}
			r.state = DistrictState.RECLAIMED;
			r.restoring = true;
			r.flickerSinceDay = DistrictRecord.UNSET;
			return DecayChange.RESTORED;
		}
		if (r.state != DistrictState.RECLAIMED) {
			return DecayChange.NONE;
		}
		if (enough) {
			if (r.flickerSinceDay == DistrictRecord.UNSET) {
				return DecayChange.NONE;
			}
			r.flickerSinceDay = DistrictRecord.UNSET;
			return DecayChange.FLICKER_ENDED;
		}
		if (r.flickerSinceDay == DistrictRecord.UNSET) {
			r.flickerSinceDay = day;
			return d.flickerDays() > 0 ? DecayChange.FLICKER_STARTED : blackout(r);
		}
		return day - r.flickerSinceDay >= d.flickerDays() ? blackout(r) : DecayChange.NONE;
	}

	private static DecayChange blackout(DistrictRecord r) {
		r.state = DistrictState.BLACKOUT;
		r.flickerSinceDay = DistrictRecord.UNSET;
		return DecayChange.BLACKOUT;
	}

	/** Days left before a flickering district blacks out (0 if it is not flickering). */
	public static long flickerDaysLeft(DistrictRecord r, DecaySettings d, long day) {
		return r.flickerSinceDay == DistrictRecord.UNSET ? 0 : Math.max(0, r.flickerSinceDay + d.flickerDays() - day);
	}

	/**
	 * Repair one linked key relay of a won district back to full. WRONG_STATE before the district is won,
	 * NOT_KEY_RELAY for a relay that is not linked, FULL if it needs no repair.
	 */
	public static Result repairRelay(DistrictRecord r, long relayChunk, Set<Long> keyRelays) {
		if (r.state != DistrictState.RECLAIMED && r.state != DistrictState.BLACKOUT) {
			return Result.WRONG_STATE;
		}
		if (!keyRelays.contains(relayChunk) || !r.linked.contains(relayChunk)) {
			return Result.NOT_KEY_RELAY;
		}
		if (r.condition(relayChunk) >= DistrictRecord.FULL_CONDITION) {
			return Result.FULL;
		}
		r.setCondition(relayChunk, DistrictRecord.FULL_CONDITION);
		return Result.COMPLETED;
	}

	/** Grid %: 20 per reclaimed district, capped at 80 before the Black Start, plus its bonus (max 100). */
	public static int gridPercent(int reclaimed, int blackStartBonus) {
		return Math.min(100, Math.min(80, 20 * Math.max(0, reclaimed)) + Math.max(0, blackStartBonus));
	}
}
