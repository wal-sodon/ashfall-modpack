package xyz.toesinthesand.ashfall.salvage.building;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;

import java.util.List;
import java.util.Optional;

/**
 * Where the salvage passes, loot refill and the Director learn which building stands where.
 * <p>
 * The live implementation is {@link LostCitiesBuildingSource}: every answer is derived from Lost Cities'
 * deterministic chunk plans. {@link StaticBuildingSource} holds a fixed list (gametests' fake source). The retired
 * Arnis sidecar reader that used to sit here is in {@code archive/arnis/}; everything that once read it reads this
 * interface, so the passes never care which generator built the city.
 * <p>
 * Every method is server-thread only and never loads or generates chunks.
 */
public interface BuildingSource {
	/** No buildings anywhere: worlds with neither an Arnis sidecar nor Lost Cities. */
	BuildingSource NONE = new BuildingSource() {
		@Override
		public String name() {
			return "none";
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public List<Building> inChunk(int chunkX, int chunkZ) {
			return List.of();
		}

		@Override
		public Building get(long id) {
			return null;
		}

		@Override
		public Building at(BlockPos pos) {
			return null;
		}

		@Override
		public LongList chunks(int radiusBlocks) {
			return new LongArrayList();
		}
	};

	/** Short name for status output ("lostcities", "arnis", "none"). */
	String name();

	/** True when there is nothing to work from; the atlas pauses and building features switch off. */
	boolean isEmpty();

	/** Number of known buildings, or -1 when the source is unbounded (Lost Cities plans on demand). */
	int size();

	/** Buildings whose footprint overlaps this chunk. */
	List<Building> inChunk(int chunkX, int chunkZ);

	/** The building with this id, or null. Ids are stable for the life of the world. */
	Building get(long id);

	/** The building whose footprint and height range contain this block, or null. */
	Building at(BlockPos pos);

	/**
	 * Chunks a full pre-processing run should visit, in a stable order. Arnis lists every chunk a building
	 * touches; Lost Cities lists the city chunks within {@code radiusBlocks} of the layout origin.
	 */
	LongList chunks(int radiusBlocks);

	/**
	 * The generator's plan for a chunk (street, building, floors, style), when the source has one. Only
	 * Lost Cities does; it is how street-based features (relays, cars, street loot) find streets.
	 */
	default Optional<CityChunk> chunk(int chunkX, int chunkZ) {
		return Optional.empty();
	}

	/**
	 * Whether the atlas should touch this chunk at all. Arnis maps are city everywhere; in a Lost Cities
	 * world only city chunks are ours, and the wilderness keeps its vanilla structures (ocean monuments
	 * keep their sea lanterns, trial chambers their loot).
	 */
	default boolean coversChunk(int chunkX, int chunkZ) {
		return true;
	}

	/**
	 * True when the generator already makes ruins, rubble and vines itself (Lost Cities), so our damage pass
	 * must not add its own weathering on top.
	 */
	default boolean generatorDamage() {
		return false;
	}

	/** Drops anything derived from data that just reloaded (zones feed the {@code lc_zone} tag). */
	default void invalidate() {
	}
}
