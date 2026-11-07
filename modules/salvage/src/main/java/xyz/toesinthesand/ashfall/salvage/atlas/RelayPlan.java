package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.ChunkPlans;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where Power Relays go in a Lost Cities world (plan section 1.7, Power Relays row). Positions are pure functions
 * of the zone layout, the Lost Cities chunk plans and the seed, so they are known before anyone visits a
 * chunk: the Grid Map and the Link step can show them in advance, and the order in which chunks generate
 * never changes the result.
 * <ul>
 * <li><b>Key relays</b>: each district lists {@code key_relay_targets}; each target gets one relay on the street
 * chunk nearest it (by distance from the chunk centre), never sharing a chunk with another key relay.</li>
 * <li><b>Ambient relays</b>: any other street chunk of a city zone carries one with chance {@link #AMBIENT_CHANCE},
 * decided by {@code hash(seed, chunk)}. About 12,000 street chunks lie within 1.6 km of Downtown, so this gives
 * the roughly 75 relays the Arnis map had.</li>
 * </ul>
 * A relay stands on the street surface at a hashed cell away from the chunk edge. {@link RelayPlacer} does the
 * placing when the chunk is processed.
 */
public final class RelayPlan {
	/** How far from a target to look for a street chunk, in chunks. */
	public static final int SEARCH_RADIUS_CHUNKS = 8;
	public static final double AMBIENT_CHANCE = 1.0 / 160;
	private static final long KEY_SALT = 0x4E1A7E7L, AMBIENT_SALT = 0xA4B1EL, CELL_SALT = 0xCE11L;

	private RelayPlan() {
	}

	/** One district key relay: the target it serves and where it goes. */
	public record KeyRelay(String zone, int targetX, int targetZ, int chunkX, int chunkZ, BlockPos pos) {
	}

	/** The key relays of every district in {@code zones}, in zone then target order. */
	public static List<KeyRelay> keyRelays(ZoneMap zones, ChunkPlans plans) {
		List<KeyRelay> out = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		for (Zone zone : zones.zones().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList()) {
			for (List<Integer> target : zone.keyRelayTargets()) {
				if (target.size() < 2) {
					continue;
				}
				int tx = target.get(0), tz = target.get(1);
				nearestStreet(plans, tx, tz, used).ifPresent(street -> {
					used.add(ChunkPos.pack(street.chunkX(), street.chunkZ()));
					out.add(new KeyRelay(zone.id(), tx, tz, street.chunkX(), street.chunkZ(), cell(street)));
				});
			}
		}
		return out;
	}

	/**
	 * The street chunk whose centre is nearest (tx, tz), skipping {@code used}. Searches square rings outward and
	 * stops once no farther ring can hold anything closer than the best found; ties go to the smaller x, then z.
	 */
	public static Optional<CityChunk> nearestStreet(ChunkPlans plans, int tx, int tz, Set<Long> used) {
		int ccx = tx >> 4, ccz = tz >> 4;
		CityChunk best = null;
		long bestD = Long.MAX_VALUE;
		for (int r = 0; r <= SEARCH_RADIUS_CHUNKS; r++) {
			// Every chunk in ring r is at least (r - 1) * 16 blocks from the target.
			long minD = (long) Math.max(0, r - 1) * 16;
			if (best != null && minD * minD > bestD) {
				break;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int cx = ccx + dx, cz = ccz + dz;
					if (used.contains(ChunkPos.pack(cx, cz))) {
						continue;
					}
					Optional<CityChunk> plan = plans.at(cx, cz);
					if (plan.isEmpty() || !plan.get().isStreet()) {
						continue;
					}
					long ex = (cx << 4) + 8 - tx, ez = (cz << 4) + 8 - tz;
					long d = ex * ex + ez * ez;
					if (d < bestD || (d == bestD && (cx < best.chunkX() || (cx == best.chunkX() && cz < best.chunkZ())))) {
						best = plan.get();
						bestD = d;
					}
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** True if this street chunk carries an ambient relay. */
	public static boolean isAmbient(long seed, CityChunk plan) {
		return plan.isStreet() && ZoneMap.zoneAtChunk(plan.chunkX(), plan.chunkZ()).city()
				&& AtlasHash.unit(AtlasHash.hash(seed, ChunkPos.pack(plan.chunkX(), plan.chunkZ()), AMBIENT_SALT)) < AMBIENT_CHANCE;
	}

	/** The relay cell of a street chunk: on the surface, 3..12 blocks in from the chunk edges, by hash. */
	public static BlockPos cell(CityChunk plan) {
		long h = AtlasHash.hash(ChunkPos.pack(plan.chunkX(), plan.chunkZ()), CELL_SALT);
		int dx = 3 + (int) Long.remainderUnsigned(h, 10), dz = 3 + (int) Long.remainderUnsigned(h >>> 20, 10);
		return new BlockPos((plan.chunkX() << 4) + dx, plan.groundY() + 1, (plan.chunkZ() << 4) + dz);
	}

	/**
	 * The id a street relay is tracked under in {@link AtlasData} (one relay per chunk). Hashed so it never meets
	 * a building id (packed chunk or OSM id) by accident.
	 */
	public static long relayKey(int chunkX, int chunkZ) {
		return AtlasHash.hash(KEY_SALT, ChunkPos.pack(chunkX, chunkZ));
	}

	/**
	 * Records the key relays of the loaded zone layout as pending candidates. Idempotent (keyed per chunk), so it
	 * runs at every server start and data reload; a relay already placed stays placed. @return relays recorded
	 */
	public static int recordKeyRelays(ServerLevel level, BuildingSource source) {
		if (source.chunk(0, 0).isEmpty() || ZoneMap.current().isEmpty()) {
			return 0;
		}
		AtlasData data = AtlasData.get(level.getServer());
		List<KeyRelay> relays = keyRelays(ZoneMap.current(), source::chunk);
		for (KeyRelay relay : relays) {
			data.addRelay(new AtlasData.RelayCandidate(level.dimension(), relay.pos(), relayKey(relay.chunkX(), relay.chunkZ())));
		}
		return relays.size();
	}

	/** Records this street chunk's ambient relay, if it has one. @return true if one was recorded */
	public static boolean recordAmbient(ServerLevel level, CityChunk plan) {
		if (!isAmbient(level.getSeed(), plan)) {
			return false;
		}
		AtlasData.get(level.getServer()).addRelay(new AtlasData.RelayCandidate(level.dimension(), cell(plan),
				relayKey(plan.chunkX(), plan.chunkZ())));
		return true;
	}
}
