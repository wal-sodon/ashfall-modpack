package xyz.toesinthesand.ashfall.arms.finale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The finale's saved data must never hand out a shared default object. {@code optionalFieldOf(name, default)} returns
 * the same instance for every decode that lacks the field, and on save omits the field while the value is still that
 * instance, so a mutable default leaks state between runs (or between worlds opened in one JVM) and is never written.
 */
class FinaleCodecDefaultsTest {
	@Test
	void runsDecodedWithoutClocksGetTheirOwnClocks() {
		FinaleRun a = FinaleRun.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		FinaleRun b = FinaleRun.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		assertNotSame(a.clocks, b.clocks, "two runs share one Clocks");
		a.clocks.wave = 123;
		assertEquals(0, b.clocks.wave, "a change to one run's clocks showed in another");
	}

	@Test
	void clocksOfARunDecodedWithoutThemAreSaved() {
		FinaleRun run = FinaleRun.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		run.clocks.wave = 77;
		run.clocks.idle = 5;
		JsonElement saved = FinaleRun.CODEC.encodeStart(JsonOps.INSTANCE, run).getOrThrow();
		FinaleRun back = FinaleRun.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		assertEquals(77, back.clocks.wave, "clocks were dropped on save: " + saved);
		assertEquals(5, back.clocks.idle, "clocks were dropped on save: " + saved);
	}

	@Test
	void finaleStatesDecodedWithoutARunGetTheirOwnRun() {
		FinaleState a = FinaleState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		FinaleState b = FinaleState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		assertNotSame(a.run(), b.run(), "two worlds share one FinaleRun");
		a.run().status = FinaleRun.Status.RUNNING;
		a.run().bandsLit = 2;
		assertEquals(FinaleRun.Status.IDLE, b.run().status, "world A's run showed in world B");

		JsonElement saved = FinaleState.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
		assertTrue(saved.getAsJsonObject().has("run"), "the run was not saved: " + saved);
		FinaleState back = FinaleState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		assertEquals(FinaleRun.Status.RUNNING, back.run().status);
		assertEquals(2, back.run().bandsLit);
	}
}
