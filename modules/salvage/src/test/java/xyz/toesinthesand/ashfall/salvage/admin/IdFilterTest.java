package xyz.toesinthesand.ashfall.salvage.admin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdFilterTest {
	private static final List<String> IDS = List.of("ashfall:buildings/police", "ashfall_cache:police_armory",
			"minecraft:chests/simple_dungeon", "minecraft:entities/zombie", "minecraft:entities/zombie_villager");

	@Test
	void pathPrefixRanksFirst() {
		assertEquals(List.of("ashfall_cache:police_armory", "ashfall:buildings/police"), IdFilter.filter(IDS, "police", 5));
	}

	@Test
	void namespacePrefixAndContains() {
		assertEquals(List.of("minecraft:chests/simple_dungeon", "minecraft:entities/zombie", "minecraft:entities/zombie_villager"),
				IdFilter.filter(IDS, "minecraft:", 5));
		assertEquals(List.of("minecraft:entities/zombie_villager"), IdFilter.filter(IDS, "minecraft:entities/zombie", 5));
	}

	@Test
	void blankAndCap() {
		assertTrue(IdFilter.filter(IDS, "  ", 5).isEmpty());
		assertEquals(1, IdFilter.filter(IDS, "a", 1).size());
	}
}
