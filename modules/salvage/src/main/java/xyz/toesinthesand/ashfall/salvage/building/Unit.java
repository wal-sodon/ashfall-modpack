package xyz.toesinthesand.ashfall.salvage.building;

/**
 * One unit on one storey: an Arnis furnished unit, or a whole Lost Cities storey.
 *
 * @param floor    storey index, 0 = ground, negative = cellar
 * @param floorY   absolute Y of the floor slab (the unit's air starts at floorY + 1)
 * @param topY     absolute Y of the highest air row under the ceiling
 * @param arnisUse what the unit is used as: Arnis's {@code Use} ("Home", "Shop(Pharmacy)", "Food(Cafe)"), or
 *                 "Floor" / "Cellar" for Lost Cities storeys
 */
public record Unit(int floor, int floorY, int topY, String arnisUse, Footprint footprint) {
	public boolean containsY(int y) {
		return y > floorY && y <= topY + 1;
	}
}
