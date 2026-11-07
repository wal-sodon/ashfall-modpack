package xyz.toesinthesand.ashfall.salvage.building;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Buildings of a Lost Cities world, derived on demand from its chunk plans (plan section 1.7).
 * <p>
 * A building <em>instance</em> is identified by {@code dimension | origin chunk | building id}: the origin is the
 * multibuilding's north-west chunk, or the chunk itself for a one-chunk building, so every chunk of a
 * multibuilding resolves to the same instance and its loot quota, population and refill timer are shared.
 * The numeric {@link Building#id()} is the packed origin chunk, which is unique per dimension (one building per
 * origin) and reversible, so {@link #get(long)} works for ids stored in saved data (Director records).
 * <p>
 * Each instance is turned into the same {@link Building} shape the Arnis sidecar produced, so the passes need no
 * Lost Cities branch: one {@link Unit} per storey (cellars have negative indices), footprints inset one block
 * from the chunk border where Lost Cities puts its outer walls, and the facts profiles match on are in
 * {@link Building#tags()} under {@code lc_*} keys ({@link #TAG_BUILDING}, {@link #TAG_MULTI}, {@link #TAG_ZONE},
 * ...). Lost Cities floors are always {@value #FLOOR_HEIGHT} blocks.
 * <p>
 * Plans are deterministic and valid before generation, so this source answers for chunks nobody has visited;
 * nothing here touches the level.
 */
public final class LostCitiesBuildingSource implements BuildingSource {
	/** Lost Cities' storey height in blocks (getRealHeight is {@code groundLevel + 6 * level}). */
	public static final int FLOOR_HEIGHT = 6;
	/** Building id of the chunk at the origin (for a multibuilding, the north-west part's id). */
	public static final String TAG_BUILDING = "lc_building";
	/** Multibuilding id; absent for one-chunk buildings. */
	public static final String TAG_MULTI = "lc_multibuilding";
	/** Our zone at the origin chunk (zone file name, e.g. "downtown"). */
	public static final String TAG_ZONE = "lc_zone";
	/** Lost Cities city style id of the origin chunk. */
	public static final String TAG_STYLE = "lc_style";
	/** {@code dimension|originX,originZ|buildingId}: the instance key, for logs and commands. */
	public static final String TAG_KEY = "lc_key";
	public static final String TAG_FLOORS = "lc_floors";
	public static final String TAG_CELLARS = "lc_cellars";
	/** Y above which Lost Cities ruined the building; absent when intact. */
	public static final String TAG_RUIN_Y = "lc_ruin_y";
	/** Unit use names: storeys above ground and cellars. Profiles may list them under {@code arnis_uses}. */
	public static final String USE_FLOOR = "Floor";
	public static final String USE_CELLAR = "Cellar";

	private static final int CACHE_SIZE = 4096;
	/**
	 * Chunk plans kept (about 2.5 MB at most): a 16-chunk view radius plus the Director's 7x7 lookups around a
	 * few players fit with room to spare.
	 */
	private static final int PLAN_CACHE_SIZE = 16384;

	private final ResourceKey<Level> dimension;
	private final ChunkPlans plans;
	/** Origin chunk (packed) -> instance; access-ordered so the oldest entries go first. */
	private final Map<Long, Building> cache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Building> eldest) {
			return size() > CACHE_SIZE;
		}
	};

	/**
	 * Packed chunk -> plan (null value = Lost Cities has no plan there), least recently used first. Lost Cities'
	 * own plan cache is timed, so a lookup that misses it rebuilds the city plan, takes its locks and copies the
	 * answer into a new {@link CityChunk}; profiling showed that rebuilding under every building zombie's
	 * per-tick despawn check and the Director's per-player building scan. Plans never change for a seed, so
	 * caching our copy is always safe; {@link #invalidate()} still drops it with the building cache.
	 */
	private final Long2ObjectLinkedOpenHashMap<Optional<CityChunk>> planCache = new Long2ObjectLinkedOpenHashMap<>(1024);

	public LostCitiesBuildingSource(ResourceKey<Level> dimension, ChunkPlans plans) {
		this.dimension = dimension;
		this.plans = plans;
	}

	/** The plan for a chunk, from our cache when we have looked it up before. */
	private Optional<CityChunk> plan(int chunkX, int chunkZ) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		Optional<CityChunk> cached = planCache.getAndMoveToLast(key);
		if (cached != null) {
			return cached;
		}
		Optional<CityChunk> plan = plans.at(chunkX, chunkZ);
		if (plan.isEmpty()) {
			// Not cached: an empty answer may only mean Lost Cities is not ready for this level yet.
			return plan;
		}
		planCache.putAndMoveToLast(key, plan);
		if (planCache.size() > PLAN_CACHE_SIZE) {
			planCache.removeFirst();
		}
		return plan;
	}

	/** The source for a level, reading Lost Cities' plans for it; empty if Lost Cities is inactive there. */
	public static Optional<BuildingSource> forLevel(ServerLevel level) {
		if (LostCityContext.chunk(level, 0, 0).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new LostCitiesBuildingSource(level.dimension(), (cx, cz) -> LostCityContext.chunk(level, cx, cz)));
	}

	@Override
	public String name() {
		return "lostcities";
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public int size() {
		return -1;
	}

	@Override
	public Optional<CityChunk> chunk(int chunkX, int chunkZ) {
		return plan(chunkX, chunkZ);
	}

	@Override
	public boolean coversChunk(int chunkX, int chunkZ) {
		return plan(chunkX, chunkZ).map(CityChunk::isCity).orElse(false);
	}

	@Override
	public boolean generatorDamage() {
		return true;
	}

	@Override
	public void invalidate() {
		cache.clear();
		planCache.clear();
	}

	@Override
	public List<Building> inChunk(int chunkX, int chunkZ) {
		Optional<CityChunk> plan = plan(chunkX, chunkZ);
		if (plan.isEmpty() || !plan.get().hasBuilding()) {
			return List.of();
		}
		Building building = instance(plan.get());
		return building == null ? List.of() : List.of(building);
	}

	@Override
	public Building get(long id) {
		int ox = ChunkPos.getX(id), oz = ChunkPos.getZ(id);
		for (Building building : inChunk(ox, oz)) {
			if (building.id() == id) {
				return building;
			}
		}
		return null;
	}

	@Override
	public Building at(BlockPos pos) {
		for (Building building : inChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			if (pos.getY() >= building.baseY() && pos.getY() <= building.topY() + 1) {
				return building;
			}
		}
		return null;
	}

	@Override
	public LongList chunks(int radiusBlocks) {
		int ocx = ZoneMap.ORIGIN_X >> 4, ocz = ZoneMap.ORIGIN_Z >> 4, r = Math.max(0, radiusBlocks) >> 4;
		LongList out = new LongArrayList();
		for (int cx = ocx - r; cx <= ocx + r; cx++) {
			for (int cz = ocz - r; cz <= ocz + r; cz++) {
				long dx = cx - ocx, dz = cz - ocz;
				if (dx * dx + dz * dz <= (long) r * r && coversChunk(cx, cz)) {
					out.add(ChunkPos.pack(cx, cz));
				}
			}
		}
		return out;
	}

	/** The instance a building chunk belongs to, built from its origin chunk's plan and cached. */
	private Building instance(CityChunk plan) {
		int ox = plan.originX(), oz = plan.originZ();
		long id = ChunkPos.pack(ox, oz);
		Building cached = cache.get(id);
		if (cached != null) {
			return cached;
		}
		CityChunk origin = ox == plan.chunkX() && oz == plan.chunkZ() ? plan : plan(ox, oz).filter(CityChunk::hasBuilding).orElse(plan);
		Building building = build(dimension, id, ox, oz, Math.max(1, plan.multiW()), Math.max(1, plan.multiH()), plan.multiBuilding(), origin);
		cache.put(id, building);
		return building;
	}

	/** The instance key string, {@code dimension|originX,originZ|buildingId}. */
	public static String key(ResourceKey<Level> dimension, int originX, int originZ, String buildingId) {
		return dimension.identifier() + "|" + originX + "," + originZ + "|" + buildingId;
	}

	static Building build(ResourceKey<Level> dimension, long id, int ox, int oz, int w, int h, String multi, CityChunk origin) {
		int minX = ox << 4, minZ = oz << 4, maxX = ((ox + w) << 4) - 1, maxZ = ((oz + h) << 4) - 1;
		Footprint shell = rect(minX, minZ, maxX, maxZ);
		Footprint inside = rect(minX + 1, minZ + 1, maxX - 1, maxZ - 1);
		int ground = origin.groundY();
		int floors = Math.max(0, origin.floors()), cellars = Math.max(0, origin.cellars());

		int[] floorYs = new int[floors + cellars];
		List<Unit> units = new ArrayList<>(floors + cellars);
		for (int f = -cellars; f < floors; f++) {
			int floorY = ground + FLOOR_HEIGHT * f;
			floorYs[f + cellars] = floorY;
			// Air runs from floorY + 1 up to the next slab at floorY + 6.
			units.add(new Unit(f, floorY, floorY + FLOOR_HEIGHT - 1, f < 0 ? USE_CELLAR : USE_FLOOR, inside));
		}

		Zone zone = ZoneMap.zoneAtChunk(ox, oz);
		Map<String, String> tags = new HashMap<>();
		tags.put("name", origin.buildingId());
		tags.put(TAG_BUILDING, origin.buildingId());
		if (multi != null) {
			tags.put(TAG_MULTI, multi);
		}
		tags.put(TAG_ZONE, zone.id());
		if (origin.cityStyle() != null) {
			tags.put(TAG_STYLE, origin.cityStyle());
		}
		tags.put(TAG_KEY, key(dimension, ox, oz, origin.buildingId()));
		tags.put(TAG_FLOORS, Integer.toString(floors));
		tags.put(TAG_CELLARS, Integer.toString(cellars));
		if (origin.ruinLevel() >= 0) {
			tags.put(TAG_RUIN_Y, Integer.toString(origin.ruinLevel()));
		}
		return new Building(id, Map.copyOf(tags), floorYs, ground - FLOOR_HEIGHT * cellars, ground + FLOOR_HEIGHT * floors,
				shell, List.copyOf(units));
	}

	private static Footprint rect(int minX, int minZ, int maxX, int maxZ) {
		List<int[]> rows = new ArrayList<>(maxZ - minZ + 1);
		for (int z = minZ; z <= maxZ; z++) {
			rows.add(new int[]{z, minX, maxX});
		}
		return Footprint.of(rows);
	}

	/**
	 * The Y above which a building is ruined (Lost Cities removed or broke what was there), or
	 * {@link Integer#MAX_VALUE} when it is intact or not a Lost Cities building.
	 */
	public static int ruinY(Building building) {
		String ruin = building.tags().get(TAG_RUIN_Y);
		if (ruin == null) {
			return Integer.MAX_VALUE;
		}
		try {
			return Integer.parseInt(ruin);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	/** True for buildings this source produced (they carry an instance key). */
	public static boolean isLostCities(Building building) {
		return building.tags().containsKey(TAG_KEY);
	}
}
