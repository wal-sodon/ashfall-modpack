package xyz.toesinthesand.ashfall.director.spawn;

/**
 * Where a Director spawn happens, in the terms spawn-table conditions use (plan section 1.7, "Director spawn
 * context"): the zone, whether the spot is inside a building or out on a street, and which Lost Cities building.
 * This is the In Control idea done inside our own SpawnDirector, so In Control itself is not needed.
 * <p>
 * Plain Java so variant picks with conditions can be unit tested; {@link DirectorContexts} builds it from the
 * world.
 *
 * @param zone       zone id from the zone JSON ({@code "mission"}), or {@code "unknown"} when no zone data is loaded
 * @param inBuilding the spot is inside a building's footprint and height range
 * @param onStreet   the spot is in a city chunk with no building (Lost Cities streets, squares and parks)
 * @param building   Lost Cities building id at the spot ({@code "lostcities:building1"}), or null
 */
public record SpawnContext(String zone, boolean inBuilding, boolean onStreet, String building) {
	/** No context: no zone, not in a building, not on a street. Variants with conditions never match it. */
	public static final SpawnContext NONE = new SpawnContext("unknown", false, false, null);
}
