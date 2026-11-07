package xyz.toesinthesand.ashfall.arms.story;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ending's pure rules: the vote tally, Bind's capped growth, the aftershock clock and the shipped profiles. */
class EndingLogicTest {
	private static VoteTally.Outcome decide(int sever, int bind, int eligible, boolean over, boolean last) {
		return VoteTally.decide(sever, bind, eligible, over, last);
	}

	@Test
	void strictMajorityEndsTheVoteEarly() {
		assertEquals(VoteTally.Outcome.SEVER, decide(2, 0, 3, false, false));
		assertEquals(VoteTally.Outcome.BIND, decide(0, 3, 4, false, false));
		assertEquals(VoteTally.Outcome.PENDING, decide(2, 0, 4, false, false), "half is not a majority");
		assertEquals(VoteTally.Outcome.PENDING, decide(1, 1, 4, false, false));
	}

	@Test
	void soloVoteDecidesAtOnce() {
		assertEquals(VoteTally.Outcome.BIND, decide(0, 1, 1, false, false));
		assertEquals(VoteTally.Outcome.PENDING, decide(0, 0, 1, false, false));
	}

	@Test
	void everyoneVotedOrWindowOverTakesTheLeader() {
		assertEquals(VoteTally.Outcome.BIND, decide(1, 2, 4, true, false), "more votes wins once the window closes");
		assertEquals(VoteTally.Outcome.SEVER, decide(1, 0, 5, true, false));
	}

	@Test
	void tieOrSilenceRepromptsOnceThenSevers() {
		assertEquals(VoteTally.Outcome.REPROMPT, decide(1, 1, 2, false, false), "everyone voted, tied");
		assertEquals(VoteTally.Outcome.REPROMPT, decide(0, 0, 3, true, false), "nobody voted");
		assertEquals(VoteTally.Outcome.SEVER, decide(1, 1, 2, false, true), "second tie defaults to Sever");
		assertEquals(VoteTally.Outcome.SEVER, decide(0, 0, 3, true, true), "second silence defaults to Sever");
	}

	@Test
	void zeroEligibleCountsAsOneVoter() {
		assertEquals(VoteTally.Outcome.REPROMPT, decide(0, 0, 0, true, false));
		assertEquals(VoteTally.Outcome.SEVER, decide(1, 0, 0, false, false));
	}

	@Test
	void bindGrowthStopsAtTheCap() {
		EndingProfile.HordeGrowth g = new EndingProfile.HordeGrowth(7, 4, 1, 6, 0.1f, 1.5f);
		assertEquals(4, g.tier(0));
		assertEquals(4, g.tier(6));
		assertEquals(5, g.tier(7));
		assertEquals(6, g.tier(14));
		assertEquals(6, g.tier(10_000), "tier VI is the ceiling however long the world runs");
		assertEquals(1.0, g.size(0), 1e-9);
		assertEquals(1.2, g.size(14), 1e-6);
		assertEquals(1.5, g.size(10_000), 1e-9);
		assertEquals(4, g.tier(-5), "a clock set back never lowers below the start");
	}

	@Test
	void aftershocksFallEveryIntervalAfterTheEnding() {
		EndingProfile.Aftershocks a = new EndingProfile.Aftershocks(7, 0.5f);
		assertEquals(0, a.generations(0));
		assertEquals(0, a.generations(6));
		assertEquals(1, a.generations(7));
		assertEquals(3, a.generations(21));
		assertEquals(0, EndingProfile.Aftershocks.NONE.generations(100), "0 days turns them off");
	}

	@Test
	void endingIdsRoundTrip() {
		for (StoryEnding e : StoryEnding.values()) {
			assertEquals(e, StoryEnding.byId(e.id()));
		}
		assertEquals(StoryEnding.NONE, StoryEnding.byId("garbage"));
		assertFalse(StoryEnding.NONE.chosen());
	}

	@Test
	void shippedProfilesParseAndMatchTheDesign() throws IOException {
		EndingProfile sever = profile("sever");
		assertTrue(sever.antibioticsCure(), "Sever: Antibiotics fully cure (user decision 2026-10-25)");
		assertEquals(0.4f, sever.spawnMultiplier(), 1e-6);
		assertTrue(sever.hordeInterval() > 1 && sever.freeRelays());
		assertTrue(sever.hordeGrowth().isEmpty());
		EndingProfile bind = profile("bind");
		assertFalse(bind.antibioticsCure());
		assertTrue(bind.unlockAttachments() && bind.bunkerRadiusBonus() > 0 && !bind.rewards().isEmpty());
		EndingProfile.HordeGrowth growth = bind.hordeGrowth().orElseThrow();
		assertTrue(growth.tierCap() <= 6, "Bind cap: siege tier VI");
		assertTrue(bind.flags().contains("ashfall:bind"));
		for (EndingProfile p : new EndingProfile[]{sever, bind}) {
			assertFalse(p.closing().pages().isEmpty());
			for (String page : p.closing().pages()) {
				assertTrue(page.length() <= 230, "closing page too long for a book page: " + page);
			}
		}
	}

	private static EndingProfile profile(String id) throws IOException {
		String json = Files.readString(Path.of("src/main/resources/data/ashfall/ashfall/ending/" + id + ".json"));
		return EndingProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}
}
