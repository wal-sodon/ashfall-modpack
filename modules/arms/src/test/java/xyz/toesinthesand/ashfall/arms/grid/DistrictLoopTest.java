package xyz.toesinthesand.ashfall.arms.grid;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The district loop's state machine: repair, energize, link, siege, and what failing costs. */
class DistrictLoopTest {
	/** 4 couplers, 4 cells, 100-tick hold, 50 hp core, 30-tick warning, 11000-tick night, 40 presence ticks. */
	private static final LoopRules RULES = new LoopRules(4, 4, 100, 50, 30, 11_000, 40);
	private static final Set<Long> KEYS = Set.of(1L, 2L, 3L);

	private static DistrictRecord repaired() {
		DistrictRecord r = new DistrictRecord();
		for (int i = 0; i < RULES.couplers(); i++) {
			DistrictLoop.socket(r, RULES);
		}
		return r;
	}

	private static DistrictRecord holding() {
		DistrictRecord r = repaired();
		for (int i = 0; i < RULES.cells(); i++) {
			DistrictLoop.loadCell(r, RULES);
		}
		return r;
	}

	@Test
	void couplersRepairTheCore() {
		DistrictRecord r = new DistrictRecord();
		assertEquals(DistrictLoop.Result.WRONG_STATE, DistrictLoop.loadCell(r, RULES), "cells before repair");
		for (int i = 1; i < RULES.couplers(); i++) {
			assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.socket(r, RULES));
			assertEquals(DistrictState.DARK, r.state);
		}
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.socket(r, RULES));
		assertEquals(DistrictState.REPAIRED, r.state);
		assertEquals(DistrictLoop.Result.FULL, DistrictLoop.socket(r, RULES), "a fifth coupler is refused");
		assertEquals(4, r.couplers);
	}

	@Test
	void theLastCellStartsTheHold() {
		DistrictRecord r = repaired();
		for (int i = 1; i < RULES.cells(); i++) {
			assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.loadCell(r, RULES));
			assertFalse(r.energizing(RULES));
		}
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.loadCell(r, RULES));
		assertTrue(r.energizing(RULES));
		assertEquals(DistrictLoop.Result.FULL, DistrictLoop.loadCell(r, RULES));
	}

	@Test
	void holdOnlyRunsWithAPlayerNear() {
		DistrictRecord r = holding();
		assertFalse(DistrictLoop.tickHold(r, RULES, false, 60));
		assertEquals(0, r.holdTicks, "an unattended hold must not progress");
		assertFalse(DistrictLoop.tickHold(r, RULES, true, 60));
		assertEquals(60, r.holdTicks);
		assertFalse(DistrictLoop.tickHold(r, RULES, false, 60));
		assertEquals(60, r.holdTicks, "pausing keeps progress");
		assertTrue(DistrictLoop.tickHold(r, RULES, true, 60), "completes on the tick that reaches the hold");
		assertEquals(RULES.holdTicks(), r.holdTicks, "clamped");
		assertTrue(r.holdDone(RULES));
	}

	@Test
	void linksOnlyCountKeyRelays() {
		DistrictRecord r = holding();
		assertEquals(DistrictLoop.Result.NOT_KEY_RELAY, DistrictLoop.link(r, RULES, 9L, KEYS));
		assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.link(r, RULES, 1L, KEYS));
		assertEquals(DistrictLoop.Result.ALREADY_LINKED, DistrictLoop.link(r, RULES, 1L, KEYS));
		assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.link(r, RULES, 2L, KEYS));
		assertFalse(DistrictLoop.readyToEnergize(r, RULES, KEYS), "hold not done, one relay missing");
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.link(r, RULES, 3L, KEYS));
		assertFalse(DistrictLoop.readyToEnergize(r, RULES, KEYS), "all linked but the hold is not done");
		DistrictLoop.tickHold(r, RULES, true, RULES.holdTicks());
		assertTrue(DistrictLoop.readyToEnergize(r, RULES, KEYS));
	}

	@Test
	void relaysCanBeLinkedBeforeTheCells() {
		DistrictRecord r = repaired();
		assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.link(r, RULES, 1L, KEYS), "linking only needs a repaired core");
		DistrictLoop.link(r, RULES, 2L, KEYS);
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.link(r, RULES, 3L, KEYS));
		assertFalse(DistrictLoop.readyToEnergize(r, RULES, KEYS), "relays done, but no cells and no hold");
		for (int i = 0; i < RULES.cells(); i++) {
			DistrictLoop.loadCell(r, RULES);
		}
		DistrictLoop.tickHold(r, RULES, true, RULES.holdTicks());
		assertTrue(DistrictLoop.readyToEnergize(r, RULES, KEYS));
	}

	@Test
	void threeOfSixRelaysAreEnough() {
		Set<Long> six = Set.of(1L, 2L, 3L, 4L, 5L, 6L);
		DistrictRecord r = holding();
		DistrictLoop.tickHold(r, RULES, true, RULES.holdTicks());
		DistrictLoop.link(r, RULES, 4L, six);
		DistrictLoop.link(r, RULES, 6L, six);
		assertEquals(DistrictLoop.Result.NOT_KEY_RELAY, DistrictLoop.link(r, RULES, 9L, six));
		assertFalse(DistrictLoop.readyToEnergize(r, RULES, six), "two of three");
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.link(r, RULES, 1L, six), "any three of the six");
		assertTrue(DistrictLoop.readyToEnergize(r, RULES, six));
		assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.link(r, RULES, 2L, six), "extra links are harmless");
		assertTrue(DistrictLoop.readyToEnergize(r, RULES, six));
	}

	@Test
	void noKeyRelaysMeansTheHoldAlone() {
		DistrictRecord r = holding();
		DistrictLoop.tickHold(r, RULES, true, RULES.holdTicks());
		assertTrue(DistrictLoop.readyToEnergize(r, RULES, Set.of()));
	}

	@Test
	void energizedSchedulesTheComingNightfall() {
		assertEquals(12_000, DistrictLoop.nextNightfall(1_000), "morning: tonight");
		assertEquals(36_000, DistrictLoop.nextNightfall(13_000), "already night: tomorrow night");
		assertEquals(36_000, DistrictLoop.nextNightfall(24_000), "dawn of day 2: that evening");
		assertEquals(12_000, DistrictLoop.nextNightfall(11_999));
		DistrictRecord r = holding();
		DistrictLoop.energize(r, 5_000);
		assertEquals(DistrictState.ENERGIZED, r.state);
		assertEquals(12_000, r.siegeAt);
	}

	@Test
	void warningThenStart() {
		DistrictRecord r = holding();
		DistrictLoop.energize(r, 5_000);
		assertFalse(DistrictLoop.shouldWarn(r, RULES, 11_900), "before the warning window");
		assertTrue(DistrictLoop.shouldWarn(r, RULES, 11_975));
		r.warned = true;
		assertFalse(DistrictLoop.shouldWarn(r, RULES, 11_980), "warns once");
		assertFalse(DistrictLoop.shouldStart(r, RULES, 11_999));
		assertTrue(DistrictLoop.shouldStart(r, RULES, 12_000));
	}

	@Test
	void sleepingThroughTheNightPostponesTheSiege() {
		DistrictRecord r = holding();
		DistrictLoop.energize(r, 5_000);
		assertFalse(DistrictLoop.shouldStart(r, RULES, 24_100), "the night went by without a siege");
		assertEquals(36_000, r.siegeAt, "moved to the next nightfall");
		assertEquals(DistrictState.ENERGIZED, r.state);
	}

	@Test
	void destroyedCoreCostsTimeNotTheRun() {
		DistrictRecord r = holding();
		r.linked.addAll(KEYS);
		DistrictLoop.tickHold(r, RULES, true, RULES.holdTicks());
		DistrictLoop.energize(r, 5_000);
		DistrictLoop.startSiege(r, RULES);
		assertEquals(DistrictState.SIEGE, r.state);
		assertEquals(50, r.coreHp);
		assertFalse(DistrictLoop.damageCore(r, 30));
		assertTrue(DistrictLoop.damageCore(r, 30));
		assertEquals(DistrictState.REPAIRED, r.state, "back to repaired");
		assertEquals(RULES.couplers(), r.couplers, "couplers kept");
		assertEquals(KEYS, r.linked, "links kept");
		assertEquals(0, r.cells, "cells spent");
		assertEquals(0, r.holdTicks, "hold to redo");
		assertFalse(r.energizing(RULES));
	}

	@Test
	void siegeIsWonByClearingTheWavesOrHoldingUntilDawn() {
		DistrictRecord r = holding();
		DistrictLoop.energize(r, 5_000);
		DistrictLoop.startSiege(r, RULES);
		assertEquals(DistrictLoop.SiegeOutcome.CONTINUE, DistrictLoop.siegeOutcome(r, RULES, 13_000, 0, 3), "no wave spawned yet");
		r.wavesSpawned = 3;
		assertEquals(DistrictLoop.SiegeOutcome.CONTINUE, DistrictLoop.siegeOutcome(r, RULES, 13_000, 2, 3));
		assertEquals(DistrictLoop.SiegeOutcome.WON, DistrictLoop.siegeOutcome(r, RULES, 13_000, 0, 3), "every wave dead");

		DistrictRecord dawn = holding();
		DistrictLoop.energize(dawn, 5_000);
		DistrictLoop.startSiege(dawn, RULES);
		DistrictLoop.tickSiege(dawn, true, 50);
		assertEquals(DistrictLoop.SiegeOutcome.WON, DistrictLoop.siegeOutcome(dawn, RULES, 23_000, 5, 3), "held until dawn");
	}

	@Test
	void anUnattendedSiegeIsPostponedNotWon() {
		DistrictRecord r = holding();
		DistrictLoop.energize(r, 5_000);
		DistrictLoop.startSiege(r, RULES);
		DistrictLoop.tickSiege(r, false, 10_000);
		assertEquals(10_000, r.siegeTicks);
		assertEquals(0, r.presenceTicks);
		assertEquals(DistrictLoop.SiegeOutcome.ABANDONED, DistrictLoop.siegeOutcome(r, RULES, 23_000, 4, 3));
		DistrictLoop.abandon(r, 23_000);
		assertEquals(DistrictState.ENERGIZED, r.state);
		assertEquals(36_000, r.siegeAt);
	}

	@Test
	void reclaimRecordsTheDay() {
		DistrictRecord r = holding();
		DistrictLoop.reclaim(r, 9);
		assertEquals(DistrictState.RECLAIMED, r.state);
		assertEquals(9, r.reclaimedAtDay);
	}

	@Test
	void gridPercentIsTwentyPerDistrictCappedAtEighty() {
		assertEquals(0, DistrictLoop.gridPercent(0, 0));
		assertEquals(60, DistrictLoop.gridPercent(3, 0));
		assertEquals(80, DistrictLoop.gridPercent(4, 0));
		assertEquals(80, DistrictLoop.gridPercent(5, 0), "capped before the Black Start");
		assertEquals(100, DistrictLoop.gridPercent(4, 20), "Black Start bonus on top");
		assertEquals(100, DistrictLoop.gridPercent(4, 50), "never above 100");
	}

	@Test
	void resetKeepsTheCorePosition() {
		DistrictRecord r = holding();
		r.corePos = 1234L;
		r.linked.add(1L);
		r.reset();
		assertEquals(DistrictState.DARK, r.state);
		assertEquals(0, r.couplers);
		assertTrue(r.linked.isEmpty());
		assertEquals(1234L, r.corePos);
	}

	private static DistrictRecord reclaimed(long day) {
		DistrictRecord r = holding();
		r.linked.addAll(KEYS);
		DistrictLoop.reclaim(r, day);
		r.rewarded = true;
		return r;
	}

	@Test
	void aBreachOnlyHitsAReclaimedDistrict() {
		assertFalse(DistrictLoop.startBreach(holding(), RULES, 0), "not reclaimed: nothing to breach");
		DistrictRecord r = reclaimed(8);
		assertTrue(DistrictLoop.startBreach(r, RULES, 12_000));
		assertEquals(DistrictState.SIEGE, r.state);
		assertTrue(r.breach);
		assertEquals(RULES.coreHp(), r.coreHp, "a fresh core for the fight");
		assertEquals(12_000, r.siegeAt, "lasts from now until dawn");
	}

	@Test
	void aLostBreachBreaksTheDistrictAndReRepairCostsHalf() {
		DistrictRecord r = reclaimed(8);
		DistrictLoop.startBreach(r, RULES, 12_000);
		assertTrue(DistrictLoop.damageCore(r, RULES.coreHp()), "the core falls");
		assertEquals(DistrictState.BROKEN, r.state);
		assertFalse(r.breach);
		assertEquals(0, r.couplers, "the broken core holds no couplers");
		assertEquals(KEYS, r.linked, "the relays stay linked");
		assertTrue(r.rewarded, "no second reward cache later");
		assertEquals(DistrictLoop.Result.WRONG_STATE, DistrictLoop.loadCell(r, RULES), "no cells into a broken core");

		assertEquals(2, DistrictLoop.reRepairCouplers(RULES), "half of 4");
		assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.socket(r, RULES));
		assertEquals(DistrictState.BROKEN, r.state);
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.socket(r, RULES), "the second coupler re-repairs it");
		assertEquals(DistrictState.RECLAIMED, r.state, "straight back on the grid");
		assertTrue(r.restoring, "flagged so the Reclaimed effects skip the first-reclaim fanfare");
		assertEquals(8, r.reclaimedAtDay, "keeps the day it was first reclaimed");
	}

	@Test
	void reRepairRoundsHalfUp() {
		assertEquals(3, DistrictLoop.reRepairCouplers(new LoopRules(5, 4, 100, 50, 30, 11_000, 40)));
		assertEquals(1, DistrictLoop.reRepairCouplers(new LoopRules(1, 4, 100, 50, 30, 11_000, 40)));
	}

	@Test
	void aDefendedBreachStillStandingAtDawnIsHeld() {
		DistrictRecord r = reclaimed(8);
		DistrictLoop.startBreach(r, RULES, 12_000);
		r.wavesSpawned = 1;
		assertEquals(DistrictLoop.SiegeOutcome.CONTINUE, DistrictLoop.siegeOutcome(r, RULES, 12_500, 3, 2));
		DistrictLoop.tickSiege(r, true, RULES.minPresenceTicks());
		assertEquals(DistrictLoop.SiegeOutcome.WON, DistrictLoop.siegeOutcome(r, RULES, 23_000, 3, 2),
				"defended long enough and still standing");
		DistrictLoop.reclaim(r, 20);
		assertEquals(DistrictState.RECLAIMED, r.state);
		assertFalse(r.breach);
		assertEquals(8, r.reclaimedAtDay, "a held Breach keeps the original reclaim day");
	}

	/** Staying away no longer keeps a district: a Breach nobody defended is lost at dawn, like a fallen core. */
	@Test
	void anUndefendedBreachIsLostAtDawn() {
		DistrictRecord r = reclaimed(8);
		DistrictLoop.startBreach(r, RULES, 12_000);
		r.wavesSpawned = 1;
		DistrictLoop.tickSiege(r, false, 10_000);
		DistrictLoop.tickSiege(r, true, RULES.minPresenceTicks() - 1);
		assertEquals(DistrictLoop.SiegeOutcome.BREACH_LOST, DistrictLoop.siegeOutcome(r, RULES, 23_000, 3, 2),
				"one tick short of the defence time");
		DistrictLoop.loseBreach(r);
		assertEquals(DistrictState.BROKEN, r.state);
		assertFalse(r.breach);
		assertEquals(0, r.couplers, "re-repair from scratch, at half cost");
	}

	@Test
	void theDefendCountdownOnlyRunsWithAPlayerNear() {
		DistrictRecord r = reclaimed(8);
		DistrictLoop.startBreach(r, RULES, 12_000);
		assertEquals(40, DistrictLoop.defendTicksLeft(r, RULES, 12_000));
		DistrictLoop.tickSiege(r, false, 25);
		assertEquals(40, DistrictLoop.defendTicksLeft(r, RULES, 12_025), "nobody near: paused");
		DistrictLoop.tickSiege(r, true, 25);
		assertEquals(15, DistrictLoop.defendTicksLeft(r, RULES, 12_050));
		DistrictLoop.tickSiege(r, true, 25);
		assertEquals(0, DistrictLoop.defendTicksLeft(r, RULES, 12_075), "done, never negative");
		assertEquals(23_000 - 12_075, DistrictLoop.ticksToDawn(r, RULES, 12_075));

		DistrictRecord siege = holding();
		DistrictLoop.startSiege(siege, RULES);
		siege.siegeAt = 12_000;
		DistrictLoop.tickSiege(siege, true, 10);
		assertEquals(30, DistrictLoop.defendTicksLeft(siege, RULES, 12_010), "sieges count down the same way");
	}

	/** A Breach started with less night left than the defence time needs only the time there is. */
	@Test
	void aLateBreachNeedsNoMoreDefenceThanTheNightHasLeft() {
		DistrictRecord r = reclaimed(8);
		DistrictLoop.startBreach(r, RULES, 22_990);
		assertEquals(10, DistrictLoop.defendTicksLeft(r, RULES, 22_990), "10 ticks to dawn");
		DistrictLoop.tickSiege(r, true, 10);
		r.wavesSpawned = 1;
		assertEquals(DistrictLoop.SiegeOutcome.WON, DistrictLoop.siegeOutcome(r, RULES, 23_000, 3, 2));
	}

	/** Phase 4.5 playtest: an admin Breach at midnight ran until the next noon instead of ending at dawn. */
	@Test
	void aLateBreachStillEndsAtThisDawn() {
		long day3 = 3 * LoopRules.DAY_TICKS;
		DistrictRecord r = reclaimed(8);
		assertTrue(DistrictLoop.startBreach(r, RULES, day3 + 18_000));
		assertEquals(day3 + 12_000, r.siegeAt, "anchored to tonight's nightfall");
		r.wavesSpawned = 1;
		DistrictLoop.tickSiege(r, true, RULES.minPresenceTicks());
		assertEquals(DistrictLoop.SiegeOutcome.CONTINUE, DistrictLoop.siegeOutcome(r, RULES, day3 + 22_999, 3, 2));
		assertEquals(DistrictLoop.SiegeOutcome.WON, DistrictLoop.siegeOutcome(r, RULES, day3 + 23_000, 3, 2), "held at dawn");

		DistrictRecord noon = reclaimed(8);
		assertTrue(DistrictLoop.startBreach(noon, RULES, day3 + 6_000));
		assertEquals(day3 + 12_000, noon.siegeAt, "a daytime Breach lasts through the coming night");
		DistrictRecord early = reclaimed(8);
		assertTrue(DistrictLoop.startBreach(early, RULES, day3 + 23_500));
		assertEquals(day3 + LoopRules.DAY_TICKS + 12_000, early.siegeAt, "after dawn: the next night");
	}

	@Test
	void anOrdinarySiegeLossStillGoesBackToRepaired() {
		DistrictRecord r = holding();
		DistrictLoop.startSiege(r, RULES);
		DistrictLoop.damageCore(r, RULES.coreHp());
		assertEquals(DistrictState.REPAIRED, r.state, "only a Breach breaks a district");
	}

	/** balance-v1: a siege or Breach starts with its tier's core health; 0 falls back to the settings' core. */
	@Test
	void aSiegeStartsWithItsTiersCoreHealth() {
		DistrictRecord r = new DistrictRecord();
		DistrictLoop.startSiege(r, RULES, 1920);
		assertEquals(1920, r.coreHp);
		DistrictLoop.startSiege(r, RULES, 0);
		assertEquals(RULES.coreHp(), r.coreHp, "no tier health: the settings' core");
		DistrictRecord lit = new DistrictRecord();
		lit.state = DistrictState.RECLAIMED;
		assertTrue(DistrictLoop.startBreach(lit, RULES, 13_000, 3200));
		assertEquals(3200, lit.coreHp, "a Breach uses its tier's core too");
	}
}
