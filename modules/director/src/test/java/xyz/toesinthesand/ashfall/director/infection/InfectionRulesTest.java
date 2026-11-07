package xyz.toesinthesand.ashfall.director.infection;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The infection timeline: stage bands, pauses, treatments, the death timer and hit chances. */
class InfectionRulesTest {
	private static final InfectionRules R = InfectionRules.DEFAULT;
	private static final int S2 = R.stage(2).startsAtTicks();
	private static final int S3 = R.stage(3).startsAtTicks();
	private static final int S4 = R.stage(4).startsAtTicks();

	private static InfectionData at(long progress) {
		return new InfectionData(true, progress, 0);
	}

	@Test
	void stagesFollowProgressBands() {
		assertEquals(0, R.stageAt(InfectionData.HEALTHY));
		assertEquals(1, R.stageAt(at(0)));
		assertEquals(1, R.stageAt(at(S2 - 1)));
		assertEquals(2, R.stageAt(at(S2)));
		assertEquals(3, R.stageAt(at(S3)));
		assertEquals(4, R.stageAt(at(S4)));
		assertEquals(4, R.stageAt(at(R.deathAt() + 10_000)));
	}

	@Test
	void advanceMovesProgressAndLeavesHealthyAlone() {
		assertEquals(at(100), R.advance(at(0), 100));
		assertEquals(InfectionData.HEALTHY, R.advance(InfectionData.HEALTHY, 1000));
		assertEquals(at(5), R.advance(at(5), 0));
	}

	@Test
	void pauseIsSpentBeforeProgress() {
		InfectionData paused = new InfectionData(true, 50, 30);
		assertEquals(new InfectionData(true, 50, 10), R.advance(paused, 20));
		assertEquals(new InfectionData(true, 60, 0), R.advance(paused, 40));
		assertTrue(paused.paused());
		assertFalse(R.advance(paused, 30).paused());
	}

	@Test
	void deathComesAfterStageFourTimeout() {
		assertEquals((long) S4 + R.deathAfterTicks(), R.deathAt());
		assertFalse(R.isFatal(at(R.deathAt() - 1)));
		assertTrue(R.isFatal(at(R.deathAt())));
		assertFalse(R.isFatal(InfectionData.HEALTHY));
	}

	@Test
	void bandagePausesWithoutShorteningALongerPause() {
		int bandage = R.treatments().bandagePauseTicks();
		assertEquals(new InfectionData(true, 70, bandage), R.bandage(at(70)));
		InfectionData longer = new InfectionData(true, 70, bandage * 3);
		assertEquals(longer, R.bandage(longer));
		assertEquals(InfectionData.HEALTHY, R.bandage(InfectionData.HEALTHY));
	}

	@Test
	void antibioticsKnockBackOneStageAndPause() {
		int pause = R.treatments().antibioticPauseTicks();
		assertEquals(new InfectionData(true, S3, pause), R.antibiotics(at(S4 + 100), false));
		assertEquals(new InfectionData(true, S2, pause), R.antibiotics(at(S3 + 5), false));
		assertEquals(new InfectionData(true, 0, pause), R.antibiotics(at(S2), false));
		assertEquals(2, R.stageAt(R.antibiotics(at(S3), false)));
	}

	@Test
	void antibioticsCureStageOne() {
		assertEquals(InfectionData.HEALTHY, R.antibiotics(at(S2 - 1), false));
		assertEquals(InfectionData.HEALTHY, R.antibiotics(InfectionData.HEALTHY, false));
	}

	@Test
	void severEndingMakesAntibioticsCureAnyStage() {
		for (int start : new int[]{0, S2, S3, S4, (int) R.deathAt() - 1}) {
			assertEquals(InfectionData.HEALTHY, R.antibiotics(at(start), true), "progress " + start);
		}
	}

	@Test
	void atStageStartsAtTheBand() {
		for (int stage = 1; stage <= 4; stage++) {
			assertEquals(stage, R.stageAt(R.atStage(stage)));
			assertEquals(R.stage(stage).startsAtTicks(), R.atStage(stage).progress());
		}
		assertEquals(4, R.stageAt(R.atStage(99)));
	}

	@Test
	void hitChanceByVariantZoneAndArmour() {
		InfectionRules.Sources s = new InfectionRules.Sources(0.1f, Map.of("patient", 0.3f), Map.of("mission", 2f), 0.04f, 0.5f, 0.15f);
		assertEquals(0.1f, s.chance(null, "montford", 0), 1e-6);
		assertEquals(0.1f, s.chance("walker", "montford", 0), 1e-6, "unlisted variants use the base chance");
		assertEquals(0.3f, s.chance("patient", "montford", 0), 1e-6);
		assertEquals(0.2f, s.chance(null, "mission", 0), 1e-6);
		assertEquals(0.5f, s.chance("patient", "mission", 0), 1e-6, "capped at max_chance");
		assertEquals(0.1f * (1f - 12 * 0.04f), s.chance(null, "montford", 12), 1e-6, "12 armour points remove 48%");
		assertEquals(0f, s.chance(null, "montford", 30), 1e-6, "full armour never goes negative");
	}

	@Test
	void shippedFileMatchesBuiltInTimeline() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/data/ashfall/ashfall/infection/stages.json")) {
			JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			InfectionRules shipped = InfectionRules.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
			assertEquals(R.stages(), shipped.stages());
			assertEquals(R.deathAfterTicks(), shipped.deathAfterTicks());
			assertEquals(R.treatments(), shipped.treatments());
			assertEquals(0.2f, shipped.sources().variantChance().get("patient"), 1e-6);
			assertEquals(1.5f, shipped.sources().zoneMultiplier().get("mission"), 1e-6);
		}
	}

	@Test
	void invalidTimelinesAreRefused() {
		InfectionRules.Stage one = R.stage(1);
		List<InfectionRules.Stage> threeStages = List.of(one, R.stage(2), R.stage(3));
		assertTrue(InfectionRules.CODEC.encodeStart(JsonOps.INSTANCE,
				new InfectionRules(threeStages, 100, R.sources(), R.treatments())).isError());
		InfectionRules.Stage lateFour = new InfectionRules.Stage(4, S2, 0, 0, List.of());
		assertTrue(InfectionRules.CODEC.encodeStart(JsonOps.INSTANCE,
				new InfectionRules(List.of(one, R.stage(2), R.stage(3), lateFour), 100, R.sources(), R.treatments())).isError());
		assertFalse(InfectionRules.CODEC.encodeStart(JsonOps.INSTANCE, R).isError());
	}
}
