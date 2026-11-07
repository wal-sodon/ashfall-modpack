package xyz.toesinthesand.ashfall.salvage.loot;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefillRuleTest {
	private static final BuildingProfile.Refill RULES = new BuildingProfile.Refill(true, 1_000, 32, 200);

	@Test
	void neverOpenedNeverRefills() {
		assertFalse(RefillRule.shouldRefill(RULES, -1, 50_000, 40_000));
	}

	@Test
	void tooEarly() {
		assertFalse(RefillRule.shouldRefill(RULES, 10_000, 10_999, 10_500));
	}

	@Test
	void noQuietGapSinceLastOpen() {
		assertFalse(RefillRule.shouldRefill(RULES, 10_000, 20_000, 9_000));
		assertFalse(RefillRule.shouldRefill(RULES, 10_000, 20_000, 10_000));
		assertFalse(RefillRule.shouldRefill(RULES, 10_000, 20_000, -1));
	}

	@Test
	void eligible() {
		assertTrue(RefillRule.shouldRefill(RULES, 10_000, 11_000, 10_900));
	}

	@Test
	void disabled() {
		BuildingProfile.Refill off = new BuildingProfile.Refill(false, 1_000, 32, 200);
		assertFalse(RefillRule.shouldRefill(off, 10_000, 20_000, 15_000));
	}

	@Test
	void seedVariesByRollAndPosition() {
		assertEquals(RefillRule.seed(42, 1), RefillRule.seed(42, 1));
		assertNotEquals(RefillRule.seed(42, 1), RefillRule.seed(42, 2));
		assertNotEquals(RefillRule.seed(42, 1), RefillRule.seed(43, 1));
	}

	@Test
	void zoneCodecRoundTripsAnyAndBlockTypes() {
		String json = """
				{"id":"bunker","dimension":"minecraft:overworld","center":[1,64,-3],"radius":12,
				 "container_type":"any","table":"ashfall:chests/military"}""";
		LootZone zone = LootZone.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
		assertTrue(zone.containerType().isAny());
		assertEquals(LootZone.SHARED, zone.mode());
		assertEquals(BuildingProfile.Refill.DEFAULT, zone.refill());
		LootZone typed = zone.withContainerType(java.util.Optional.of(net.minecraft.resources.Identifier.parse("minecraft:barrel")));
		var encoded = LootZone.CODEC.encodeStart(JsonOps.INSTANCE, typed).getOrThrow();
		assertEquals("minecraft:barrel", encoded.getAsJsonObject().get("container_type").getAsString());
		assertEquals(typed, LootZone.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}

	@Test
	void containerFilterParsesTagsAndBareIds() {
		assertEquals(ContainerFilter.ANY, ContainerFilter.parse(" any ").getOrThrow());
		assertEquals(ContainerFilter.ANY, ContainerFilter.parse("").getOrThrow());
		ContainerFilter barrel = ContainerFilter.parse("barrel").getOrThrow();
		assertEquals("minecraft:barrel", barrel.spec());
		ContainerFilter tag = ContainerFilter.parse("#c:chests").getOrThrow();
		assertTrue(tag.isTag());
		assertEquals("c:chests", tag.id().orElseThrow().toString());
		assertTrue(ContainerFilter.parse("#Bad Tag").isError());
		String json = """
				{"id":"armory","dimension":"minecraft:overworld","center":[0,64,0],"radius":4,
				 "container_type":"#c:chests","table":"ashfall:chests/military"}""";
		LootZone zone = LootZone.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
		assertEquals(tag, zone.containerType());
		assertEquals("#c:chests", LootZone.CODEC.encodeStart(JsonOps.INSTANCE, zone).getOrThrow()
				.getAsJsonObject().get("container_type").getAsString());
	}

	@Test
	void presenceQuietGap() {
		ZoneManager m = new ZoneManager();
		m.markSeen("b:1", 100, 200);
		assertEquals(100, m.presence("b:1").lastQuietGapEnd());
		m.markSeen("b:1", 200, 200);
		assertEquals(100, m.presence("b:1").lastQuietGapEnd());
		m.markSeen("b:1", 450, 200);
		assertEquals(450, m.presence("b:1").lastQuietGapEnd());
		assertEquals(450, m.presence("b:1").lastSeen());
	}
}
