package xyz.toesinthesand.ashfall.salvage.building;

import java.util.List;
import java.util.Map;

/**
 * One building instance, whichever {@link BuildingSource} produced it. The shape dates from the Arnis
 * sidecar and was kept for Lost Cities so the loot, refill and Director code needs no generator branch.
 *
 * @param id     stable id: the packed origin chunk for Lost Cities ({@link LostCitiesBuildingSource}), the OSM
 *               id for Arnis
 * @param tags   what profiles match on: {@code lc_*} keys for Lost Cities, OSM tags (building, amenity, shop,
 *               name, ...) for Arnis
 * @param floors absolute Y of each floor slab, lowest first
 * @param units  rooms or storeys containers and zombies go in; for Lost Cities one per storey (cellars
 *               included), for Arnis the furnished units (empty for unfurnished shells)
 */
public record Building(long id, Map<String, String> tags, int[] floors, int baseY, int topY,
					   Footprint footprint, List<Unit> units) {
	public String name() {
		return tags.getOrDefault("name", "");
	}

	/** The unit containing this block, or null (stairwells, walls, unfurnished space). */
	public Unit unitAt(int x, int y, int z) {
		for (Unit unit : units) {
			if (unit.containsY(y) && unit.footprint().contains(x, z)) {
				return unit;
			}
		}
		return null;
	}
}
