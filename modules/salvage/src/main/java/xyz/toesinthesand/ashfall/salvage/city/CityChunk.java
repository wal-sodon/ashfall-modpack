package xyz.toesinthesand.ashfall.salvage.city;

/**
 * What Lost Cities planned for one chunk, copied out of its API into a plain record so the rest of
 * Ashfall (commands, loot, Director and relays) never touches Lost Cities classes directly and keeps
 * working when Lost Cities is absent (tests, or a server without it).
 *
 * @param buildingId     Lost Cities building id, or null for streets, parks and non-city chunks
 * @param multiBuilding  Lost Cities multibuilding id when the chunk is part of one, else null
 * @param multiX         this chunk's column inside the multibuilding (0 = the west-most column); 0 if single
 * @param multiZ         this chunk's row inside the multibuilding (0 = the north-most row); 0 if single
 * @param multiW         multibuilding width in chunks; 1 if single
 * @param multiH         multibuilding depth in chunks; 1 if single
 * @param cityStyle      Lost Cities city style id, or null outside cities
 * @param ruinLevel      the Y above which the building is ruined, or -1 if it is intact
 */
public record CityChunk(int chunkX, int chunkZ, boolean isCity, String buildingId, String multiBuilding, int floors,
		int cellars, int cityLevel, int groundY, String cityStyle, int ruinLevel, int multiX, int multiZ, int multiW,
		int multiH) {

	/** A single-chunk plan (no multibuilding); the shape most tests and callers need. */
	public CityChunk(int chunkX, int chunkZ, boolean isCity, String buildingId, String multiBuilding, int floors,
			int cellars, int cityLevel, int groundY, String cityStyle, int ruinLevel) {
		this(chunkX, chunkZ, isCity, buildingId, multiBuilding, floors, cellars, cityLevel, groundY, cityStyle, ruinLevel,
				0, 0, 1, 1);
	}

	/** A city chunk with no building: streets, parks, fountains (Lost Cities treats them all as "street"). */
	public boolean isStreet() {
		return isCity && buildingId == null;
	}

	public boolean hasBuilding() {
		return isCity && buildingId != null;
	}

	/** Chunk X of the building's origin: the multibuilding's north-west chunk, or this chunk. */
	public int originX() {
		return chunkX - multiX;
	}

	/** Chunk Z of the building's origin: the multibuilding's north-west chunk, or this chunk. */
	public int originZ() {
		return chunkZ - multiZ;
	}
}
