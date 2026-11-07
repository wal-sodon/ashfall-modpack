package xyz.toesinthesand.ashfall.arms.grid;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** District decay: daily wear, horde damage, flicker, blackout and repair. */
class DistrictDecayTest {
	/** 3 of 6 key relays required. */
	private static final LoopRules RULES = new LoopRules(4, 4, 100, 50, 30, 11_000, 40, 3);
	private static final Set<Long> KEYS = Set.of(1L, 2L, 3L, 4L, 5L, 6L);
	private static final DecaySettings DECAY = DecaySettings.DEFAULT;

	/** Reclaimed on day 10 with relays 1-3 linked; decay first seen on day 10. */
	private static DistrictRecord reclaimed() {
		DistrictRecord r = new DistrictRecord();
		r.linked.addAll(Set.of(1L, 2L, 3L));
		DistrictLoop.reclaim(r, 10);
		DistrictLoop.wear(r, DECAY, KEYS, 10);
		return r;
	}

	@Test
	void relaysWearDailyAfterTheGraceDays() {
		DistrictRecord r = reclaimed();
		DistrictLoop.wear(r, DECAY, KEYS, 13);
		assertEquals(100, r.condition(1L), "the 3 grace days are free");
		DistrictLoop.wear(r, DECAY, KEYS, 15);
		assertEquals(100 - 2 * DECAY.wearPerDay(), r.condition(1L), "two days of wear after the grace");
		assertEquals(r.condition(1L), r.condition(3L), "every linked relay wears alike");
		assertEquals(100, r.condition(4L), "unlinked relays don't wear");
	}

	@Test
	void theFirstCheckOnlyStartsTheCount() {
		DistrictRecord r = new DistrictRecord();
		r.linked.addAll(Set.of(1L, 2L, 3L));
		DistrictLoop.reclaim(r, 1);
		DistrictLoop.wear(r, DECAY, KEYS, 40);
		assertEquals(100, r.condition(1L), "an old world isn't charged for days before decay existed");
	}

	@Test
	void tooFewWorkingRelaysFlickerThenBlackOut() {
		DistrictRecord r = reclaimed();
		r.setCondition(1L, 0);
		assertEquals(DistrictLoop.DecayChange.FLICKER_STARTED, DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 20));
		assertEquals(DistrictState.RECLAIMED, r.state, "still lit while it flickers");
		assertEquals(2, DistrictLoop.flickerDaysLeft(r, DECAY, 20));
		assertEquals(DistrictLoop.DecayChange.NONE, DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 21));
		assertEquals(DistrictLoop.DecayChange.BLACKOUT, DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 22));
		assertEquals(DistrictState.BLACKOUT, r.state);
		assertTrue(r.state.dark());
	}

	@Test
	void repairEndsTheFlickerOrTheBlackout() {
		DistrictRecord r = reclaimed();
		r.setCondition(1L, 0);
		DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 20);
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.repairRelay(r, 1L, KEYS));
		assertEquals(DistrictLoop.DecayChange.FLICKER_ENDED, DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 20));

		r.setCondition(2L, 0);
		DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 30);
		DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 32);
		assertEquals(DistrictState.BLACKOUT, r.state);
		DistrictLoop.wear(r, DECAY, KEYS, 40);
		assertEquals(0, r.condition(2L), "a blacked-out district doesn't wear further");
		assertEquals(DistrictLoop.Result.COMPLETED, DistrictLoop.repairRelay(r, 2L, KEYS));
		assertEquals(DistrictLoop.DecayChange.RESTORED, DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 40));
		assertEquals(DistrictState.RECLAIMED, r.state, "back without a siege");
		assertTrue(r.restoring, "no second fanfare or reward");
		assertEquals(10, r.reclaimedAtDay, "the reclaim day is kept");
	}

	@Test
	void repairNeedsAWonDistrictAndALinkedWornRelay() {
		DistrictRecord fresh = new DistrictRecord();
		assertEquals(DistrictLoop.Result.WRONG_STATE, DistrictLoop.repairRelay(fresh, 1L, KEYS));
		DistrictRecord r = reclaimed();
		assertEquals(DistrictLoop.Result.FULL, DistrictLoop.repairRelay(r, 1L, KEYS));
		assertEquals(DistrictLoop.Result.NOT_KEY_RELAY, DistrictLoop.repairRelay(r, 4L, KEYS), "not linked");
	}

	@Test
	void spareRelaysCanBeLinkedOnceWon() {
		DistrictRecord r = reclaimed();
		assertEquals(DistrictLoop.Result.ACCEPTED, DistrictLoop.link(r, RULES, 4L, KEYS));
		r.setCondition(1L, 0);
		assertEquals(DistrictLoop.DecayChange.NONE, DistrictLoop.updateDecay(r, RULES, DECAY, KEYS, 20),
				"a spare keeps three relays working");
	}

	@Test
	void hordeDamageHitsWorkingRelaysOnly() {
		DistrictRecord r = reclaimed();
		r.setCondition(1L, 0);
		assertEquals(2, DistrictLoop.damageRelays(r, KEYS, 5, 30, new Random(1)), "only the two working relays");
		assertEquals(70, r.condition(2L));
		assertEquals(70, r.condition(3L));
		assertEquals(0, r.condition(1L));
	}
}
