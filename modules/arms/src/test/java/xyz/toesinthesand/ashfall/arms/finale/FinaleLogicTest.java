package xyz.toesinthesand.ashfall.arms.finale;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.arms.finale.FinaleLogic.Result;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.PauseReason;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Phase;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Status;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Black Start's state machine with no world: seating, the buffer pausing the Surge, breaker holds and their grace,
 * failing into PAUSED and coming back, pylon counting, phase ends and the Grid % steps, party scaling, and the shipped
 * numbers adding up to a 12-15 minute finale.
 */
class FinaleLogicTest {
	private static final FinaleSettings S = FinaleSettings.DEFAULT;

	private static FinaleRun seated() {
		FinaleRun run = new FinaleRun();
		assertEquals(Result.ACCEPTED, FinaleLogic.seat(run, S, 42L));
		return run;
	}

	private static JsonElement json(String path) throws IOException {
		try (InputStream in = FinaleLogicTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing resource " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	@Test
	void seatingStartsTheSurgeWithTheUplinksOwnCharge() {
		FinaleRun run = seated();
		assertEquals(Status.RUNNING, run.status);
		assertEquals(Phase.SURGE, run.phase);
		assertEquals(42L, run.socket);
		assertEquals(S.buffer().startSeconds() * 20L, run.bufferTicks);
		assertEquals(S.uplink().hp(), run.uplinkHp);
		assertEquals(Result.WRONG_STATE, FinaleLogic.seat(run, S, 7L), "one Uplink at a time");
	}

	@Test
	void anEmptyBufferPausesSurgeProgressAndACellBuysCharge() {
		FinaleRun run = seated();
		run.bufferTicks = 40;
		assertFalse(FinaleLogic.tickSurge(run, S, 100));
		assertEquals(40, run.phaseTicks, "only the powered ticks count");
		assertEquals(0, run.bufferTicks);
		assertTrue(FinaleLogic.starved(run));
		FinaleLogic.tickSurge(run, S, 100);
		assertEquals(40, run.phaseTicks, "no progress on an empty buffer");
		assertEquals(Result.ACCEPTED, FinaleLogic.feedCell(run, S));
		assertEquals(S.buffer().cellSeconds() * 20L, run.bufferTicks, "each Energy Cell buys cell_seconds of Surge");
		FinaleLogic.tickSurge(run, S, 200);
		assertEquals(240, run.phaseTicks);
		assertFalse(FinaleLogic.starved(run));
	}

	@Test
	void theBufferHasACeiling() {
		FinaleRun run = seated();
		run.bufferTicks = S.maxBufferTicks() - 100;
		assertEquals(Result.ACCEPTED, FinaleLogic.feedCell(run, S));
		assertEquals(S.maxBufferTicks(), run.bufferTicks);
		assertEquals(Result.FULL, FinaleLogic.feedCell(run, S));
	}

	@Test
	void theSurgeEndsAfterItsPoweredSeconds() {
		FinaleRun run = seated();
		run.bufferTicks = S.maxBufferTicks();
		assertFalse(FinaleLogic.tickSurge(run, S, S.surgeTicks() - 1));
		assertTrue(FinaleLogic.tickSurge(run, S, 1));
		assertEquals(7, FinaleLogic.endPhase(run, S), "80 -> 87");
		assertEquals(Phase.FEEDBACK, run.phase);
		assertEquals(1, run.bandsLit);
		assertEquals(0, run.phaseTicks);
	}

	@Test
	void phaseEndsStepTheGridTo87Then94Then100AndComplete() {
		FinaleRun run = seated();
		assertEquals(7, FinaleLogic.endPhase(run, S));
		assertEquals(14, FinaleLogic.endPhase(run, S));
		assertEquals(Phase.HEART, run.phase);
		assertEquals(Status.RUNNING, run.status);
		assertEquals(20, FinaleLogic.endPhase(run, S));
		assertEquals(Status.COMPLETE, run.status);
		assertEquals(3, run.bandsLit);
	}

	@Test
	void aBreakerClosesOnlyWhileHeldAndSlipsAfterTheGrace() {
		FinaleRun.Breaker b = new FinaleRun.Breaker();
		long hold = S.holdTicks();
		assertFalse(FinaleLogic.tickBreaker(b, S, true, 2, hold / 2));
		assertEquals(hold / 2, b.holdTicks);
		// two players: grace 10 s x 1.5 = 300 ticks; inside it nothing is lost
		FinaleLogic.tickBreaker(b, S, false, 2, 300);
		assertEquals(hold / 2, b.holdTicks, "progress kept through the grace");
		FinaleLogic.tickBreaker(b, S, false, 2, 100);
		assertEquals(hold / 2 - 100, b.holdTicks, "then it slips back");
		assertTrue(FinaleLogic.tickBreaker(b, S, true, 2, hold));
		assertTrue(b.closed);
		assertFalse(FinaleLogic.tickBreaker(b, S, false, 2, 10_000), "a closed breaker stays closed");
		assertTrue(b.closed);
	}

	@Test
	void soloGetsALongerBreakerGraceAndFewerWaves() {
		FinaleRun.Breaker solo = new FinaleRun.Breaker(), squad = new FinaleRun.Breaker();
		solo.holdTicks = squad.holdTicks = 200;
		FinaleLogic.tickBreaker(solo, S, false, 1, 350);
		FinaleLogic.tickBreaker(squad, S, false, 3, 350);
		assertEquals(200, solo.holdTicks, "solo grace is 20 s");
		assertTrue(squad.holdTicks < 200, "a squad's grace is 10 s");
		assertEquals(13, FinaleLogic.waveSize(S, 1), "about 60% of 22 (balance-v1)");
		assertEquals(22, FinaleLogic.waveSize(S, 3));
		assertEquals(29, FinaleLogic.waveSize(S, 5));
		assertEquals(29, FinaleLogic.waveSize(S, 12), "the last entry covers bigger parties");
		assertEquals(FinaleLogic.waveSize(S, 1), FinaleLogic.waveSize(S, 0), "nobody counts as solo");
	}

	@Test
	void theMobCapLimitsEverySpawn() {
		assertEquals(14, FinaleLogic.room(S, 0, 14));
		assertEquals(6, FinaleLogic.room(S, 114, 14));
		assertEquals(0, FinaleLogic.room(S, 130, 14));
	}

	@Test
	void theHordeCapAtTheUplinkGrowsWithThePartyUpToTheFinaleCap() {
		assertEquals(60, FinaleLogic.hordeCap(S, 60, 1), "solo keeps the scheduler's per-player cap");
		assertEquals(60, FinaleLogic.hordeCap(S, 60, 0), "nobody counts as solo");
		assertEquals(120, FinaleLogic.hordeCap(S, 60, 2), "two players reach the finale's 120");
		assertEquals(120, FinaleLogic.hordeCap(S, 60, 4), "never above max_mobs");
	}

	@Test
	void uplinkAtZeroFailsIntoPausedAndCostsSurgeTimeOnly() {
		FinaleRun run = seated();
		run.phaseTicks = 3000;
		run.bandsLit = 0;
		assertFalse(FinaleLogic.damage(run, S, S.uplink().hp() - 1));
		assertTrue(FinaleLogic.damage(run, S, 5));
		assertEquals(Status.PAUSED, run.status);
		assertEquals(PauseReason.UPLINK_DOWN, run.pause);
		assertEquals(0, run.uplinkHp);
		assertEquals(3000 - S.failSetbackSeconds() * 20L, run.phaseTicks, "failing costs time");
		assertEquals(42L, run.socket, "the Uplink (and its crystals) stays seated");
		assertFalse(FinaleLogic.damage(run, S, 10), "a paused run takes no more damage");
	}

	@Test
	void aDownedUplinkResumesOncePatchedToHalf() {
		FinaleRun run = seated();
		FinaleLogic.damage(run, S, S.uplink().hp());
		assertEquals(Result.ACCEPTED, FinaleLogic.patch(run, S));
		assertEquals(Status.PAUSED, run.status, "40 of 200 is not enough");
		FinaleLogic.patch(run, S);
		assertEquals(Result.RESUMED, FinaleLogic.patch(run, S), "120 of 200 is past half");
		assertEquals(Status.RUNNING, run.status);
		assertEquals(PauseReason.NONE, run.pause);
	}

	@Test
	void aDownedUplinkWithAnEmptyBufferNeedsACellToo() {
		FinaleRun run = seated();
		run.bufferTicks = 0;
		FinaleLogic.damage(run, S, S.uplink().hp());
		for (int i = 0; i < 5; i++) {
			FinaleLogic.patch(run, S);
		}
		assertEquals(Status.PAUSED, run.status, "patched, but nothing to draw on");
		assertEquals(Result.RESUMED, FinaleLogic.feedCell(run, S));
		assertEquals(Status.RUNNING, run.status);
	}

	@Test
	void everyoneLeavingPausesAndACellResumes() {
		FinaleRun run = seated();
		long abandon = S.abandonSeconds() * 20L;
		assertFalse(FinaleLogic.presence(run, S, 0, abandon - 20));
		assertFalse(FinaleLogic.presence(run, S, 1, 20), "someone came back in time");
		assertFalse(FinaleLogic.presence(run, S, 0, abandon - 20));
		assertTrue(FinaleLogic.presence(run, S, 0, 20));
		assertEquals(Status.PAUSED, run.status);
		assertEquals(PauseReason.ABANDONED, run.pause);
		assertEquals(Result.RESUMED, FinaleLogic.feedCell(run, S));
	}

	@Test
	void failingInFeedbackTripsTheBreakersAgain() {
		FinaleRun run = seated();
		FinaleLogic.endPhase(run, S);
		FinaleRun.Breaker a = new FinaleRun.Breaker(), b = new FinaleRun.Breaker();
		a.closed = true;
		b.holdTicks = 500;
		run.breakers.addAll(List.of(a, b));
		FinaleLogic.fail(run, S, PauseReason.UPLINK_DOWN);
		assertFalse(a.closed);
		assertEquals(0, b.holdTicks);
		assertEquals(1, run.bandsLit, "the Surge's band stays lit");
	}

	@Test
	void brokenPylonsStayBrokenAndAllFourEndTheHeart() {
		FinaleRun run = seated();
		assertFalse(FinaleLogic.pylons(run, 4, 3));
		assertEquals(1, run.pylonsBroken);
		assertFalse(FinaleLogic.pylons(run, 4, 4), "a count never goes down");
		assertEquals(1, run.pylonsBroken);
		run.phase = Phase.HEART;
		FinaleLogic.fail(run, S, PauseReason.ABANDONED);
		assertEquals(1, run.pylonsBroken, "pausing keeps the broken pylons");
		assertTrue(FinaleLogic.pylons(run, 4, 0));
		assertFalse(FinaleLogic.pylons(new FinaleRun(), 0, 0), "no pylons placed is not a win");
	}

	@Test
	void jumpingCountsEarlierPhasesAsWon() {
		FinaleRun run = seated();
		assertEquals(14, FinaleLogic.jump(run, S, Phase.HEART));
		assertEquals(Phase.HEART, run.phase);
		assertEquals(2, run.bandsLit);
		assertEquals(0, FinaleLogic.jump(run, S, Phase.SURGE));
	}

	/** The replay hand-back: the story reset leaves a seated Uplink in its socket, and it powers up with no item. */
	@Test
	void theReplayResetKeepsTheUplinkInItsSocket() {
		FinaleRun run = seated();
		FinaleLogic.jump(run, S, Phase.HEART);
		run.status = Status.COMPLETE;
		run.participants.add(java.util.UUID.randomUUID());
		assertTrue(FinaleLogic.rearm(run), "a completed run keeps its Uplink");
		assertEquals(Status.IDLE, run.status);
		assertEquals(FinaleRun.NONE, run.socket, "IDLE: not seated, so nothing draws or spawns");
		assertEquals(42L, run.standby, "the Uplink waits in socket 42");
		assertEquals(0, run.bandsLit);
		assertTrue(run.participants.isEmpty(), "a replay has fresh participants");
		assertTrue(FinaleLogic.rearm(run), "a second reset keeps the same standby");
		assertEquals(42L, run.standby);

		assertEquals(Result.WRONG_STATE, FinaleLogic.restart(run, S, 7L), "only the socket that holds it");
		assertEquals(Result.ACCEPTED, FinaleLogic.restart(run, S, 42L));
		assertEquals(Status.RUNNING, run.status);
		assertEquals(Phase.SURGE, run.phase);
		assertEquals(42L, run.socket);
		assertEquals(S.uplink().hp(), run.uplinkHp, "a fresh Uplink's health");
		assertEquals(S.buffer().startSeconds() * 20L, run.bufferTicks, "and its own charge");
		assertEquals(FinaleRun.NONE, run.standby, "powered up: no longer in standby");
		assertEquals(Result.WRONG_STATE, FinaleLogic.restart(run, S, 42L), "one run at a time");
	}

	@Test
	void aResetWithNoUplinkSeatedHandsNothingBack() {
		FinaleRun idle = new FinaleRun();
		assertFalse(FinaleLogic.rearm(idle));
		assertEquals(FinaleRun.NONE, idle.standby);
		assertEquals(Result.WRONG_STATE, FinaleLogic.restart(idle, S, FinaleRun.NONE), "NONE is never a socket");
		FinaleRun paused = seated();
		FinaleLogic.damage(paused, S, S.uplink().hp());
		assertTrue(FinaleLogic.rearm(paused), "an interrupted (paused) run keeps its Uplink too");
		FinaleRun standby = new FinaleRun();
		FinaleLogic.rearm(seated());
		standby.standby = 42L;
		assertEquals(Result.ACCEPTED, FinaleLogic.seat(standby, S, 9L), "an admin start elsewhere still works");
		assertEquals(FinaleRun.NONE, standby.standby, "and uses the waiting Uplink up");
	}

	@Test
	void theStandbySocketIsSaved() {
		FinaleRun run = new FinaleRun();
		run.standby = 1234L;
		var json = FinaleRun.CODEC.encodeStart(JsonOps.INSTANCE, run).getOrThrow();
		assertEquals(1234L, FinaleRun.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().standby);
		assertEquals(FinaleRun.NONE, FinaleRun.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow().standby,
				"an old save has no standby");
	}

	@Test
	void periodicClocksFireOnTheirInterval() {
		assertFalse(FinaleLogic.due(399, 20));
		assertTrue(FinaleLogic.due(400, 20));
		assertFalse(FinaleLogic.due(1_000_000, 0), "0 is never");
	}

	@Test
	void shippedSettingsParseAndMatchTheDesign() throws IOException {
		FinaleSettings s = FinaleSettings.CODEC.parse(JsonOps.INSTANCE, json("/data/ashfall/ashfall/finale/settings.json")).getOrThrow();
		assertEquals(S, s, "the shipped file repeats the code defaults");
		assertEquals(45, s.buffer().cellSeconds(), "each Energy Cell buys 45 seconds (balance-v1: the design's 60 left a solo run 8 cells)");
		assertEquals(120, s.waves().maxMobs(), "the finale is capped at about 120 mobs");
		assertEquals(2, s.breakers().offsets().size(), "two Relay Breakers");
		int dx = Math.abs(s.breakers().offsets().get(0).getX() - s.breakers().offsets().get(1).getX());
		assertEquals(40, dx, "about 40 blocks apart");
		assertEquals(3.0, s.breakers().radius(), 1e-9, "held within 3 blocks");
		assertEquals(4, s.heart().pylons().size(), "four Heart pylons");
		assertEquals(List.of(7, 14, 20), s.bands().stream().map(FinaleSettings.Band::bonus).toList(), "80 -> 87 -> 94 -> 100");
		assertEquals(0.6, s.scale().waves(1), 1e-9, "solo gets about 60% of the waves");
		assertEquals(s.uplink().hp() / 2, FinaleLogic.resumeHp(s));
	}

	/**
	 * The design asks for 12-15 minutes. The Surge's powered clock is fixed; the Feedback is at least one breaker hold
	 * (both held together) plus the walk between them; the Heart is four pylons at about 10 s each with a lit blade,
	 * fought under the Choir. The fixed parts alone must leave room for the fighting inside that window.
	 */
	@Test
	void theShippedNumbersMakeATwelveToFifteenMinuteFinale() {
		double surge = S.surgeSeconds();
		double feedback = S.breakers().holdSeconds() + 40 / 4.3; // both held at once, plus a 40-block sprint
		double heart = 4 * (80.0 * 30 / 12) / 20; // 4 pylons, hardness 80, blade speed 12, /30 (no tool needed), in seconds
		double fixed = (surge + feedback + heart) / 60.0;
		assertTrue(fixed >= 7.5 && fixed <= 10.0, "fixed minutes " + fixed);
		// solo: the two breakers one after another, plus the Choir fight; still under 15 minutes of fixed time
		double soloFixed = (surge + 2 * S.breakers().holdSeconds() + 40 / 4.3 + heart) / 60.0;
		assertTrue(soloFixed <= 12.0, "solo fixed minutes " + soloFixed);
	}
}
