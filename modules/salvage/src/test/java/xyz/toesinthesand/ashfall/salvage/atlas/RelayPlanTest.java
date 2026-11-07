package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.salvage.building.ChunkPlans;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.city.ZoneShape;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayPlanTest {
	/** Streets on every chunk with x divisible by 4 (a grid of north-south avenues); buildings elsewhere. */
	private static final ChunkPlans AVENUES = (cx, cz) -> Optional.of(Math.floorMod(cx, 4) == 0
			? new CityChunk(cx, cz, true, null, null, 0, 0, 0, 70, "ashfall:downtown", -1)
			: new CityChunk(cx, cz, true, "lcmt:building1", null, 3, 0, 0, 70, "ashfall:downtown", -1));

	@Test
	void keyRelayGoesOnTheNearestStreetChunk() {
		// Target in chunk x=1 (blocks 16..31): avenue chunk 0 is nearer than avenue chunk 4.
		CityChunk street = RelayPlan.nearestStreet(AVENUES, 20, 100, new HashSet<>()).orElseThrow();
		assertEquals(0, street.chunkX());
		assertEquals(100 >> 4, street.chunkZ());
	}

	@Test
	void twoTargetsNeverShareAChunk() {
		Zone zone = new Zone("downtown", "district", 3, 3, 10, new ZoneShape.Disc(0, 0, 400), true, Optional.of("ashfall:downtown"),
				0.1f, 0.6f, 1f, List.of(), List.of(List.of(20, 100), List.of(21, 101), List.of(-300, 50)));
		List<RelayPlan.KeyRelay> relays = RelayPlan.keyRelays(new ZoneMap(List.of(zone)), AVENUES);
		assertEquals(3, relays.size());
		Set<Long> chunks = new HashSet<>();
		for (RelayPlan.KeyRelay relay : relays) {
			assertTrue(chunks.add(ChunkPos.pack(relay.chunkX(), relay.chunkZ())), "two key relays in one chunk: " + relays);
			assertEquals(0, Math.floorMod(relay.chunkX(), 4), "relay off the street grid: " + relay);
			assertEquals(71, relay.pos().getY(), "relay not on the street surface");
			assertEquals(relay.chunkX(), relay.pos().getX() >> 4);
			assertEquals(relay.chunkZ(), relay.pos().getZ() >> 4);
		}
		// Deterministic: the same layout gives the same plan.
		assertEquals(relays, RelayPlan.keyRelays(new ZoneMap(List.of(zone)), AVENUES));
	}

	@Test
	void relayKeysDoNotCollideWithBuildingIds() {
		assertNotEquals(ChunkPos.pack(3, 4), RelayPlan.relayKey(3, 4));
		assertNotEquals(RelayPlan.relayKey(3, 4), RelayPlan.relayKey(4, 3));
	}
}
