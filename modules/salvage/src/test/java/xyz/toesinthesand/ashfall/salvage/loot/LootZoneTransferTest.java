package xyz.toesinthesand.ashfall.salvage.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loot zone export JSON round-trips every field the admin screen edits, and junk is refused with a message. */
class LootZoneTransferTest {
	/** Not {@code Level.OVERWORLD}: touching {@code Level} bootstraps the game registries, which unit tests lack. */
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

	@Test
	void exportRoundTrips() {
		List<LootZone> zones = List.of(
				new LootZone("armory", OVERWORLD, new BlockPos(10, 70, -20), 12, ContainerFilter.tag(Identifier.parse("c:chests")),
						Identifier.parse("ashfall:buildings/police"), new BuildingProfile.Refill(false, 1200, 16, 600), LootZone.SHARED),
				new LootZone("clinic", OVERWORLD, new BlockPos(-5, 64, 3), 4, ContainerFilter.ANY,
						Identifier.parse("ashfall:buildings/clinic"), BuildingProfile.Refill.DEFAULT, LootZone.SHARED));
		String json = LootZoneTransfer.toJson(zones);
		assertTrue(json.contains("\"#c:chests\""), json);
		assertEquals(zones, LootZoneTransfer.fromJson(json).getOrThrow());
	}

	@Test
	void junkIsRefused() {
		assertTrue(LootZoneTransfer.fromJson("{not json").isError());
		assertTrue(LootZoneTransfer.fromJson("{\"zones\":[{\"id\":\"x\"}]}").isError());
	}
}
