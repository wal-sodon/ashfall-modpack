package xyz.toesinthesand.ashfall.salvage.city;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The fixed Asheville layout: which {@link Zone} owns each block column.
 * <p>
 * Every world gets the same districts in the same places (plan Decision 1), so the Grid Map, sieges,
 * landmarks and story can be authored. Building contents still vary with the seed because Lost Cities
 * picks them; only the zone, and therefore the city style, ruin rate and wilderness, is fixed here.
 * <p>
 * Lookups are a pure function of (x, z) over an immutable snapshot. That matters because the Lost
 * Cities hooks call {@link #zoneAt} speculatively from worldgen threads, where touching level state could
 * deadlock or recurse. The snapshot is swapped atomically on datapack reload.
 */
public final class ZoneMap {
	/** Origin of the layout: Downtown. Kept as a constant so callers do not hard-code it. */
	public static final int ORIGIN_X = 0;
	public static final int ORIGIN_Z = 0;

	private static final ZoneMap EMPTY = new ZoneMap(List.of());
	private static volatile ZoneMap current = EMPTY;

	private final List<Zone> ordered;
	private final Map<String, Zone> byId;

	public ZoneMap(Collection<Zone> zones) {
		List<Zone> list = new ArrayList<>(zones);
		// Highest priority first; ties broken by id so the order (and thus every lookup) is deterministic.
		list.sort(Comparator.comparingInt(Zone::priority).reversed().thenComparing(Zone::id));
		this.ordered = List.copyOf(list);
		this.byId = list.stream().collect(Collectors.toUnmodifiableMap(Zone::id, Function.identity(), (a, b) -> a));
	}

	/** The loaded layout (empty until the server's data packs load). */
	public static ZoneMap current() {
		return current;
	}

	/** Replaces the loaded layout; called by {@link ZoneLoader} and by tests. */
	public static void setCurrent(ZoneMap map) {
		current = map == null ? EMPTY : map;
	}

	/** The zone owning block column (x, z) in the loaded layout; {@link Zone#UNKNOWN} if none is loaded. */
	public static Zone zoneAt(int x, int z) {
		return current.find(x, z);
	}

	/** The zone owning a chunk, judged at its centre column so a chunk never straddles two zones. */
	public static Zone zoneAtChunk(int chunkX, int chunkZ) {
		return current.findChunk(chunkX, chunkZ);
	}

	public Zone find(int x, int z) {
		for (Zone zone : ordered) {
			if (zone.contains(x, z)) {
				return zone;
			}
		}
		return Zone.UNKNOWN;
	}

	public Zone findChunk(int chunkX, int chunkZ) {
		return find((chunkX << 4) + 8, (chunkZ << 4) + 8);
	}

	public Optional<Zone> byId(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public List<Zone> zones() {
		return ordered;
	}

	public boolean isEmpty() {
		return ordered.isEmpty();
	}
}
