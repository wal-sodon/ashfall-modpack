package xyz.toesinthesand.ashfall.salvage.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The retired Arnis {@link BuildingSource}: every building from {@code <world>/ashfall/buildings.json}, the
 * sidecar our Arnis patch writes, with a per-chunk lookup. Records repeated across Arnis tiles are merged by
 * OSM id.
 * <p>
 * Kept only behind {@link BuildingSource} so old Arnis worlds still load while Lost Cities replaces the map;
 * nothing outside this class depends on the sidecar format, so it can be archived whole.
 */
public final class BuildingIndex implements BuildingSource {
	public static final String SIDECAR = "ashfall/buildings.json";
	private static final BuildingIndex EMPTY = new BuildingIndex(Map.of());

	private final Map<Long, Building> byId;
	private final Long2ObjectMap<List<Building>> byChunk = new Long2ObjectOpenHashMap<>();

	private BuildingIndex(Map<Long, Building> byId) {
		this.byId = byId;
		for (Building building : byId.values()) {
			Footprint f = building.footprint();
			for (int cx = f.minX() >> 4; cx <= f.maxX() >> 4; cx++) {
				for (int cz = f.minZ() >> 4; cz <= f.maxZ() >> 4; cz++) {
					byChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(building);
				}
			}
		}
	}

	public static BuildingIndex empty() {
		return EMPTY;
	}

	/** Loads the sidecar in a world folder; an empty index if the world has none (not an Arnis map). */
	public static BuildingIndex load(Path worldDir) throws IOException {
		Path file = worldDir.resolve(SIDECAR);
		if (!Files.exists(file)) {
			return EMPTY;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			return parse(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	static BuildingIndex parse(JsonObject json) {
		Map<Long, Map<String, String>> tags = new HashMap<>();
		Map<Long, int[]> floors = new HashMap<>();
		Map<Long, int[]> heights = new HashMap<>();
		Map<Long, List<int[]>> shellRows = new LinkedHashMap<>();
		for (JsonElement e : json.getAsJsonArray("shells")) {
			JsonObject shell = e.getAsJsonObject();
			long id = shell.get("id").getAsLong();
			tags.computeIfAbsent(id, k -> {
				Map<String, String> t = new HashMap<>();
				shell.getAsJsonObject("tags").entrySet().forEach(en -> t.put(en.getKey(), en.getValue().getAsString()));
				return t;
			});
			floors.putIfAbsent(id, ints(shell.getAsJsonArray("floors")));
			int base = shell.get("base_y").getAsInt(), top = shell.get("top_y").getAsInt();
			heights.merge(id, new int[]{base, top}, (a, b) -> new int[]{Math.min(a[0], b[0]), Math.max(a[1], b[1])});
			shellRows.computeIfAbsent(id, k -> new ArrayList<>()).addAll(rows(shell.getAsJsonArray("rows")));
		}

		// Units: merge tiles by (building, floor, use).
		Map<Long, Map<String, List<int[]>>> unitRows = new HashMap<>();
		Map<String, int[]> unitHeights = new HashMap<>();
		for (JsonElement e : json.getAsJsonArray("units")) {
			JsonObject unit = e.getAsJsonObject();
			long id = unit.get("id").getAsLong();
			String key = unit.get("floor").getAsInt() + "|" + unit.get("use").getAsString();
			unitRows.computeIfAbsent(id, k -> new LinkedHashMap<>()).computeIfAbsent(key, k -> new ArrayList<>())
					.addAll(rows(unit.getAsJsonArray("rows")));
			unitHeights.putIfAbsent(id + "|" + key, new int[]{unit.get("floor_y").getAsInt(), unit.get("top_y").getAsInt()});
		}

		Map<Long, Building> byId = new HashMap<>();
		for (Map.Entry<Long, List<int[]>> shell : shellRows.entrySet()) {
			long id = shell.getKey();
			List<Unit> units = new ArrayList<>();
			for (Map.Entry<String, List<int[]>> u : unitRows.getOrDefault(id, Map.of()).entrySet()) {
				String[] parts = u.getKey().split("\\|", 2);
				int[] h = unitHeights.get(id + "|" + u.getKey());
				units.add(new Unit(Integer.parseInt(parts[0]), h[0], h[1], parts[1], Footprint.of(u.getValue())));
			}
			int[] h = heights.get(id);
			byId.put(id, new Building(id, Map.copyOf(tags.get(id)), floors.get(id), h[0], h[1],
					Footprint.of(shell.getValue()), List.copyOf(units)));
		}
		return new BuildingIndex(byId);
	}

	private static int[] ints(JsonArray array) {
		int[] out = new int[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsInt();
		}
		return out;
	}

	private static List<int[]> rows(JsonArray array) {
		List<int[]> out = new ArrayList<>(array.size());
		for (JsonElement row : array) {
			out.add(ints(row.getAsJsonArray()));
		}
		return out;
	}

	@Override
	public String name() {
		return "arnis";
	}

	@Override
	public boolean isEmpty() {
		return byId.isEmpty();
	}

	@Override
	public int size() {
		return byId.size();
	}

	/** Every chunk whose area overlaps a building footprint's bounding box, sorted; the radius is ignored. */
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

	public Collection<Building> all() {
		return byId.values();
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
