package xyz.toesinthesand.ashfall.arms.client.grid;

import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridSyncPayload;
import xyz.toesinthesand.ashfall.arms.story.GridMapPayload;
import xyz.toesinthesand.ashfall.arms.story.MapMarker;

import java.util.List;
import java.util.Map;

/**
 * The client's copy of the grid: the last {@code ashfall:grid_sync} (district states and percent) and the last
 * {@code ashfall:grid_map} (zone shapes and markers). Same shape as the server data so the HUD and the map can be
 * drawn without asking the server anything. Cleared on disconnect so one server's grid never shows on another.
 */
public final class GridClientState {
	/** Map order of the scored districts, used until the zone data says otherwise. */
	public static final List<String> DEFAULT_ORDER = List.of("river_arts", "south_slope", "downtown", "mission");

	private static volatile Map<String, DistrictState> states = Map.of();
	private static volatile int percent;
	private static volatile List<GridMapPayload.MapZone> zones = List.of();
	private static volatile List<MapMarker> markers = List.of();

	private GridClientState() {
	}

	public static void accept(GridSyncPayload payload) {
		states = payload.states();
		percent = payload.percent();
	}

	public static void accept(GridMapPayload payload) {
		zones = payload.zones();
		markers = payload.markers();
	}

	public static void clear() {
		states = Map.of();
		percent = 0;
		zones = List.of();
		markers = List.of();
	}

	public static DistrictState state(String district) {
		return states.getOrDefault(district, DistrictState.DARK);
	}

	public static int percent() {
		return percent;
	}

	/** Zones, highest priority first (the order the server's ZoneMap resolves overlaps in). */
	public static List<GridMapPayload.MapZone> zones() {
		return zones;
	}

	public static List<MapMarker> markers() {
		return markers;
	}

	/** The scored districts in map order: by district index from the zone data, else {@link #DEFAULT_ORDER}. */
	public static List<String> districts() {
		List<GridMapPayload.MapZone> loaded = zones;
		List<String> fromZones = loaded.stream().filter(GridMapPayload.MapZone::isDistrict)
				.sorted(java.util.Comparator.comparingInt(GridMapPayload.MapZone::districtIndex))
				.map(GridMapPayload.MapZone::id).toList();
		return fromZones.isEmpty() ? DEFAULT_ORDER : fromZones;
	}
}
