package xyz.toesinthesand.ashfall.director.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

/**
 * Reads the world into the Director's terms: which {@link Zone} a spot or building belongs to and the
 * {@link SpawnContext} variant conditions are checked against.
 * <p>
 * Everything comes from salvage's {@link BuildingSource} and {@link ZoneMap}, which answer from Lost Cities'
 * deterministic plans and the zone JSON, so none of these lookups reads blocks or loads chunks; they are cheap
 * enough for every spawn attempt.
 */
public final class DirectorContexts {
	private DirectorContexts() {
	}

	/** The zone owning a block column. */
	public static Zone zoneAt(BlockPos pos) {
		return ZoneMap.zoneAt(pos.getX(), pos.getZ());
	}

	/**
	 * The zone a building's population is scaled by. Lost Cities buildings carry the zone of their origin
	 * chunk, so every chunk of a multibuilding straddling a zone edge agrees; other buildings use the zone at
	 * the centre of their footprint.
	 */
	public static Zone zoneOf(Building building) {
		String tagged = building.tags().get(LostCitiesBuildingSource.TAG_ZONE);
		if (tagged != null) {
			Zone zone = ZoneMap.current().byId(tagged).orElse(null);
			if (zone != null) {
				return zone;
			}
		}
		var f = building.footprint();
		return ZoneMap.zoneAt((f.minX() + f.maxX()) >> 1, (f.minZ() + f.maxZ()) >> 1);
	}

	/** The Lost Cities building id of a building (its origin chunk's id), or null for other sources. */
	public static String buildingId(Building building) {
		return building == null ? null : building.tags().get(LostCitiesBuildingSource.TAG_BUILDING);
	}

	/** Context for an ambient spot: zone, building (if the spot is inside one) or street. */
	public static SpawnContext at(ServerLevel level, BlockPos pos) {
		BuildingSource source = AshfallSalvage.buildings();
		Building building = source.isEmpty() ? null : source.at(pos);
		boolean street = building == null && source.chunk(pos.getX() >> 4, pos.getZ() >> 4)
				.map(CityChunk::isStreet).orElse(false);
		return new SpawnContext(zoneAt(pos).id(), building != null, street, buildingId(building));
	}

	/** Context for a member of a building's population: always inside that building. */
	public static SpawnContext inBuilding(Building building) {
		return new SpawnContext(zoneOf(building).id(), true, false, buildingId(building));
	}
}
