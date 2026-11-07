package xyz.toesinthesand.ashfall.salvage.loot;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootPlaceholderTest {
	@Test
	void onlyOurTablesSurviveGeneration() {
		assertTrue(LootPlaceholder.isOurs(Identifier.fromNamespaceAndPath("ashfall", "buildings/office")));
		assertTrue(LootPlaceholder.isOurs(Identifier.fromNamespaceAndPath("ashfall_cache", "police_armory")));
		assertFalse(LootPlaceholder.isOurs(Identifier.fromNamespaceAndPath("lostcities", "chests/lostcitychest")));
		assertFalse(LootPlaceholder.isOurs(Identifier.fromNamespaceAndPath("lcmt", "chests/town_valuables")));
		assertFalse(LootPlaceholder.isOurs(Identifier.withDefaultNamespace("chests/end_city_treasure")));
		// The placeholder itself must be a table Lootr is told to leave alone (namespace "ashfall").
		assertEquals("ashfall", LootPlaceholder.TABLE.getNamespace());
	}
}
