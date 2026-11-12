package xyz.toesinthesand.ashfall.salvage.city;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;

/**
 * The decisions our Lost Cities hooks make, as plain functions of chunk coordinates. Separate from the
 * mixins so they can be unit-tested and so the mixins stay one-liners. Everything here must stay free of
 * level state: Lost Cities calls these from worldgen threads, speculatively, for chunks that do not exist.
 * <p>
 * With no zone data loaded every chunk maps to {@link Zone#UNKNOWN}, and every rule answers "leave Lost
 * Cities alone".
 */
public final class CityZoneRules {
	/** Structures that never start in or near a district ({@code ChunkGeneratorStructureMixin}). */
	public static final TagKey<Structure> KEPT_OUT_OF_DISTRICTS = TagKey.create(Registries.STRUCTURE,
			Identifier.fromNamespaceAndPath("ashfall", "kept_out_of_districts"));
	/**
	 * How far (chunks) outside a district a kept-out structure may not start either. A village's pieces reach about
	 * 5 chunks from its start, and Lost Cities drops any city chunk they touch, so the margin keeps a village that
	 * starts just outside the disc from eating its edge.
	 */
	public static final int STRUCTURE_MARGIN_CHUNKS = 6;

	private CityZoneRules() {
	}

	/**
	 * True if the chunk is in a district or within {@link #STRUCTURE_MARGIN_CHUNKS} of one, sampled at the chunk
	 * and at the eight points of a square that far out (district discs are hundreds of blocks wide, so the
	 * samples can't step over one).
	 */
	public static boolean nearDistrict(int chunkX, int chunkZ) {
		for (int dx = -STRUCTURE_MARGIN_CHUNKS; dx <= STRUCTURE_MARGIN_CHUNKS; dx += STRUCTURE_MARGIN_CHUNKS) {
			for (int dz = -STRUCTURE_MARGIN_CHUNKS; dz <= STRUCTURE_MARGIN_CHUNKS; dz += STRUCTURE_MARGIN_CHUNKS) {
				if (ZoneMap.zoneAtChunk(chunkX + dx, chunkZ + dz).isDistrict()) {
					return true;
				}
			}
		}
		return false;
	}

	/** False where the zone is wilderness (Ridge, Undiscovered): Lost Cities then sees a city factor of 0. */
	public static boolean allowsCity(int chunkX, int chunkZ) {
		return ZoneMap.zoneAtChunk(chunkX, chunkZ).city();
	}

	/**
	 * True inside a scored district (Downtown, River Arts, South Slope, Mission): the chunk is city whatever Lost
	 * Cities' own rarity noise, biome multipliers and height limits say.
	 * <p>
	 * Why: those districts are the end goal's map. Left to Lost Cities, a seed whose noise is low, or that puts a
	 * river biome (x0.3 in our world style) under a district, drops chunks out of the city. On seed
	 * {@code ashfall-p3} that left Downtown's centre as bare ground and, worse, the Downtown substation was never
	 * built, so the district could not be relit at all (Phase 3 playtest). The suburbs, wasteland and Montford keep
	 * Lost Cities' patchier mix on purpose.
	 */
	public static boolean forcesCity(int chunkX, int chunkZ) {
		Zone zone = ZoneMap.zoneAtChunk(chunkX, chunkZ);
		return zone.isDistrict() && zone.city();
	}

	/**
	 * True in every city zone (the districts, Montford, the suburbs and the wasteland), false in wilderness and where
	 * no zone data is loaded: the land there gets the plains biome ({@link DistrictGround}), so the whole city
	 * looks alike and only ever rains.
	 */
	public static boolean plainsBiome(int chunkX, int chunkZ) {
		Zone zone = ZoneMap.zoneAtChunk(chunkX, chunkZ);
		return zone != Zone.UNKNOWN && zone.city();
	}

	/** The Lost Cities city style the zone imposes on this chunk, if any. */
	public static Optional<String> cityStyle(int chunkX, int chunkZ) {
		return ZoneMap.zoneAtChunk(chunkX, chunkZ).cityStyle();
	}

	/** The zone's building ruin chance, or {@code profileChance} where the zone does not set one. */
	public static float ruinChance(int chunkX, int chunkZ, float profileChance) {
		float zoneChance = ZoneMap.zoneAtChunk(chunkX, chunkZ).ruinChance();
		return zoneChance >= 0 ? zoneChance : profileChance;
	}
}
