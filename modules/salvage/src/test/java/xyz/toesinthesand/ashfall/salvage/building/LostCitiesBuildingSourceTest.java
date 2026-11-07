package xyz.toesinthesand.ashfall.salvage.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.city.ZoneShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building instances from a fake Lost Cities layout: a 2x2 multibuilding, a one-chunk tower with cellars and a
 * ruin, a street and a wilderness chunk. Checks the instance key, footprint, storeys and zone tags every pass
 * relies on, without a server.
 */
class LostCitiesBuildingSourceTest {
	private static final int GROUND = 70;
	private final Map<Long, CityChunk> layout = new HashMap<>();
	private LostCitiesBuildingSource source;

	@BeforeEach
	void setUp() {
		// Multibuilding lcmt:townhall, 2 wide (x) by 2 deep (z), origin chunk (10, 20); each part has its own id.
		String[][] parts = {{"lcmt:townhall/town00", "lcmt:townhall/town01"}, {"lcmt:townhall/town10", "lcmt:townhall/town11"}};
		for (int x = 0; x < 2; x++) {
			for (int z = 0; z < 2; z++) {
				put(new CityChunk(10 + x, 20 + z, true, parts[x][z], "lcmt:townhall", 3 + x, 1, 0, GROUND, "ashfall:downtown", -1,
						x, z, 2, 2));
			}
		}
		put(new CityChunk(12, 20, true, "lcmt:building5", null, 4, 2, 0, GROUND, "ashfall:downtown", GROUND + 14));
		put(new CityChunk(13, 20, true, null, null, 0, 0, 0, GROUND, "ashfall:downtown", -1));
		put(new CityChunk(14, 20, false, null, null, 0, 0, 0, GROUND, null, -1));
		// Level.OVERWORLD would bootstrap the game's registries; the same key built by hand does not.
		ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
		source = new LostCitiesBuildingSource(overworld, (cx, cz) -> Optional.ofNullable(layout.get(ChunkPos.pack(cx, cz))));
		ZoneMap.setCurrent(new ZoneMap(List.of(new Zone("downtown", "district", 3, 3, 10, new ZoneShape.Disc(0, 0, 400), true,
				Optional.of("ashfall:downtown"), 0.1f, 0.6f, 1f, List.of(), List.of()))));
	}

	@AfterEach
	void tearDown() {
		ZoneMap.setCurrent(null);
	}

	private void put(CityChunk chunk) {
		layout.put(ChunkPos.pack(chunk.chunkX(), chunk.chunkZ()), chunk);
	}

	@Test
	void everyChunkOfAMultibuildingIsOneInstance() {
		Building origin = source.inChunk(10, 20).getFirst();
		for (int x = 10; x <= 11; x++) {
			for (int z = 20; z <= 21; z++) {
				assertSame(origin, source.inChunk(x, z).getFirst(), "chunk " + x + "," + z);
			}
		}
		assertEquals(ChunkPos.pack(10, 20), origin.id());
		assertEquals("minecraft:overworld|10,20|lcmt:townhall/town00", origin.tags().get(LostCitiesBuildingSource.TAG_KEY));
		assertEquals("lcmt:townhall/town00", origin.tags().get(LostCitiesBuildingSource.TAG_BUILDING));
		assertEquals("lcmt:townhall", origin.tags().get(LostCitiesBuildingSource.TAG_MULTI));
		assertEquals("downtown", origin.tags().get(LostCitiesBuildingSource.TAG_ZONE));
		assertEquals("ashfall:downtown", origin.tags().get(LostCitiesBuildingSource.TAG_STYLE));
		// The shell covers all four chunks; storeys come from the origin chunk's plan (3 floors + 1 cellar).
		assertEquals(160, origin.footprint().minX());
		assertEquals(191, origin.footprint().maxX());
		assertEquals(320, origin.footprint().minZ());
		assertEquals(351, origin.footprint().maxZ());
		assertEquals(4, origin.units().size());
		assertSame(origin, source.get(origin.id()));
	}

	@Test
	void storeysFollowLostCitiesFloorHeights() {
		Building tower = source.inChunk(12, 20).getFirst();
		assertFalse(tower.tags().containsKey(LostCitiesBuildingSource.TAG_MULTI));
		assertEquals(6, tower.units().size(), "4 floors + 2 cellars");
		assertEquals(GROUND - 12, tower.baseY());
		assertEquals(GROUND + 24, tower.topY());
		Unit cellar = tower.units().getFirst();
		assertEquals(-2, cellar.floor());
		assertEquals(GROUND - 12, cellar.floorY());
		assertEquals(LostCitiesBuildingSource.USE_CELLAR, cellar.arnisUse());
		Unit ground = tower.units().get(2);
		assertEquals(0, ground.floor());
		assertEquals(GROUND, ground.floorY());
		assertEquals(GROUND + 5, ground.topY());
		assertEquals(LostCitiesBuildingSource.USE_FLOOR, ground.arnisUse());
		// Interior footprint is inset from the walls on the chunk border.
		assertEquals(14 * 14, ground.footprint().area());
		assertTrue(ground.footprint().contains(193, 321));
		assertFalse(ground.footprint().contains(192, 321));
		assertEquals(GROUND + 14, LostCitiesBuildingSource.ruinY(tower));
	}

	@Test
	void streetsAndWildernessHaveNoBuildings() {
		assertTrue(source.inChunk(13, 20).isEmpty());
		assertTrue(source.chunk(13, 20).orElseThrow().isStreet());
		assertTrue(source.coversChunk(13, 20));
		assertTrue(source.inChunk(14, 20).isEmpty());
		assertFalse(source.coversChunk(14, 20));
		assertFalse(source.coversChunk(99, 99), "a chunk without a plan is not ours");
		assertNull(source.get(ChunkPos.pack(13, 20)));
		assertNull(source.get(ChunkPos.pack(11, 21)), "only the origin chunk's id names the multibuilding");
	}

	@Test
	void atChecksTheHeightRange() {
		assertNotNull(source.at(new BlockPos(200, GROUND + 3, 330)));
		assertNull(source.at(new BlockPos(200, GROUND + 40, 330)));
		assertNull(source.at(new BlockPos(13 * 16 + 4, GROUND + 1, 330)));
	}
}
