package xyz.toesinthesand.ashfall.salvage.building;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fixed, in-memory {@link BuildingSource}: a known list of buildings with a per-chunk lookup.
 * <p>
 * This is what is left of the retired Arnis sidecar index once its {@code buildings.json} reader went to
 * {@code archive/arnis/} (plan section 2.5). Gametests use it as the "fake source" the plan asks for, to run the
 * atlas passes on a hand-built room without generating a Lost Cities city, and a future real-map mode could
 * feed it again from another importer.
 */
public final class StaticBuildingSource implements BuildingSource {
	private final String name;
	private final Map<Long, Building> byId = new LinkedHashMap<>();
	private final Long2ObjectMap<List<Building>> byChunk = new Long2ObjectOpenHashMap<>();

	public StaticBuildingSource(String name, Collection<Building> buildings) {
		this.name = name;
		for (Building building : buildings) {
			byId.put(building.id(), building);
			Footprint f = building.footprint();
			for (int cx = f.minX() >> 4; cx <= f.maxX() >> 4; cx++) {
				for (int cz = f.minZ() >> 4; cz <= f.maxZ() >> 4; cz++) {
					byChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(building);
				}
			}
		}
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public boolean isEmpty() {
		return byId.isEmpty();
	}

	@Override
	public int size() {
		return byId.size();
	}

	/** Every chunk a building's bounding box overlaps, sorted; the radius is ignored (the list is already finite). */
	@Override
	public LongList chunks(int radiusBlocks) {
		LongSortedSet set = new LongRBTreeSet();
		for (Building building : byId.values()) {
			Footprint f = building.footprint();
			for (int cx = f.minX() >> 4; cx <= f.maxX() >> 4; cx++) {
				for (int cz = f.minZ() >> 4; cz <= f.maxZ() >> 4; cz++) {
					set.add(ChunkPos.pack(cx, cz));
				}
			}
		}
		return new LongArrayList(set);
	}

	@Override
	public Building get(long id) {
		return byId.get(id);
	}

	/** Buildings whose bounding box overlaps this chunk. */
	@Override
	public List<Building> inChunk(int chunkX, int chunkZ) {
		return byChunk.getOrDefault(ChunkPos.pack(chunkX, chunkZ), List.of());
	}

	/** The building whose footprint contains this column (and whose height range contains y), or null. */
	@Override
	public Building at(BlockPos pos) {
		for (Building building : inChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			if (pos.getY() >= building.baseY() && pos.getY() <= building.topY() + 1
					&& building.footprint().contains(pos.getX(), pos.getZ())) {
				return building;
			}
		}
		return null;
	}
}
