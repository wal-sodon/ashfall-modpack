package xyz.toesinthesand.ashfall.director.boss;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LairRulesTest {
	private static final long DAY = LairRules.DAY_TICKS;
	private static final Optional<BossLair.Respawn> TEN_DAYS = Optional.of(new BossLair.Respawn(10));

	@Test
	void readySpawnsOnceThenStaysSpent() {
		assertTrue(LairRules.maySpawn(LairRules.Status.READY, false, 0, OptionalLong.empty(), Optional.empty(), 100));
		assertFalse(LairRules.maySpawn(LairRules.Status.ACTIVE, false, 0, OptionalLong.empty(), Optional.empty(), 100));
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 50, OptionalLong.empty(), Optional.empty(), 100 * DAY));
		// A respawn rule does nothing before the ending.
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 50, OptionalLong.empty(), TEN_DAYS, 100 * DAY));
	}

	@Test
	void sealedNeverSpawns() {
		assertFalse(LairRules.maySpawn(LairRules.Status.READY, true, 0, OptionalLong.empty(), Optional.empty(), 0));
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, true, 0, OptionalLong.of(0), TEN_DAYS, 1000 * DAY));
	}

	@Test
	void respawnWaitsForTheLaterOfEndingAndDefeat() {
		long ending = 40 * DAY;
		// Defeated before the ending: count from the ending.
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 20 * DAY, OptionalLong.of(ending), TEN_DAYS, 49 * DAY));
		assertTrue(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 20 * DAY, OptionalLong.of(ending), TEN_DAYS, 50 * DAY));
		// Killed again after the ending: count from that defeat.
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 55 * DAY, OptionalLong.of(ending), TEN_DAYS, 60 * DAY));
		assertTrue(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 55 * DAY, OptionalLong.of(ending), TEN_DAYS, 65 * DAY));
		// No respawn rule: one-time even after the ending.
		assertFalse(LairRules.maySpawn(LairRules.Status.DEFEATED, false, 20 * DAY, OptionalLong.of(ending), Optional.empty(), 500 * DAY));
	}

	@Test
	void shippedLairsParse() throws IOException {
		Path dir = Path.of("src/main/resources/data/ashfall/ashfall/boss_lair");
		int count = 0;
		try (var files = Files.list(dir)) {
			for (Path file : files.toList()) {
				BossLair lair = BossLair.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow();
				assertTrue(lair.drops().guaranteed().stream().anyMatch(d -> d.item().toString().equals("ashfall:resonance_crystal")),
						file + " must guarantee a Resonance Crystal");
				// The three story bosses; ending-gated lairs (the Bind ending's Ascendant) come on top.
				if (lair.requiresFlag().isEmpty()) {
					count++;
				}
			}
		}
		assertEquals(3, count);
		BossLair ascendant = BossLair.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(dir.resolve("ascendant.json")))).getOrThrow();
		assertEquals(Optional.of("ashfall:bind"), ascendant.requiresFlag(), "the Ascendant exists only in a Bind world");
		BossLair pz = BossLair.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(dir.resolve("patient_zero.json")))).getOrThrow();
		assertTrue(pz.sealed(), "the deep ward starts sealed");
		assertTrue(pz.summon().isPresent());
		assertEquals(44, pz.anchor().chunkX());
		assertEquals(39, pz.anchor().chunkZ());
	}

	@Test
	void anchorNeedsExactlyOneKind() {
		var none = BossLair.Anchor.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"));
		assertTrue(none.error().isPresent());
		var two = BossLair.Anchor.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"site\": \"a:b\", \"pos\": [1, 2, 3]}"));
		assertTrue(two.error().isPresent());
		var landmarkNoChunk = BossLair.Anchor.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"landmark\": \"ashfall:hospital\"}"));
		assertTrue(landmarkNoChunk.error().isPresent());
	}
}
