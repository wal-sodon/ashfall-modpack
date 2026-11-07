package xyz.toesinthesand.ashfall.salvage.building;

import xyz.toesinthesand.ashfall.salvage.city.CityChunk;

import java.util.Optional;

/**
 * Where a {@link LostCitiesBuildingSource} gets chunk plans from. In game this is The Lost Cities' own
 * chunk API (through {@code LostCityContext}); tests pass a fake layout so the building logic can be
 * checked without a server. Plans must be deterministic and valid for chunks that do not exist yet.
 */
@FunctionalInterface
public interface ChunkPlans {
	Optional<CityChunk> at(int chunkX, int chunkZ);
}
