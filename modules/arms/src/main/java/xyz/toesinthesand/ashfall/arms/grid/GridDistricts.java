package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.atlas.RelayPlan;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which district a position belongs to, and each district's key relays and tuning.
 * <p>
 * District membership comes from the {@link ZoneMap} (plan section 3.2, M4.5 row): a district is a zone with a
 * {@code district_index}, and its id is the zone id ({@code river_arts}, {@code south_slope}, {@code downtown},
 * {@code mission}). Its key relays are the ones {@link RelayPlan} plans for that zone, identified by chunk (one
 * key relay per chunk), so the link step works on wherever the atlas actually stood the relay.
 * <p>
 * Gametests can register a small {@link TestDistrict} with its own box, relays and numbers. Gametests run in
 * parallel on one server, so each test gets its own district id and settings instead of changing the shared
 * zone layout or datapack numbers under the other tests.
 */
public final class GridDistricts {
	/**
	 * A district that exists only for a gametest.
	 *
	 * @param manual true: the server's grid tick leaves it alone and the test drives it with explicit clocks
	 */
	public record TestDistrict(String id, BoundingBox area, Set<Long> keyRelayChunks, GridSettings settings,
			SiegeSettings siege, int zoneTier, boolean manual) {
	}

	private static final Map<String, TestDistrict> TESTS = new ConcurrentHashMap<>();
	private static Map<String, Set<Long>> keyRelayCache = Map.of();
	private static ZoneMap cachedFor;
	private static BuildingSource cachedSource;

	private GridDistricts() {
	}

	/** The district at {@code pos} (overworld only), test districts first. */
	public static Optional<String> districtAt(Level level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return Optional.empty();
		}
		for (TestDistrict test : TESTS.values()) {
			if (test.area().isInside(pos)) {
				return Optional.of(test.id());
			}
		}
		Zone zone = ZoneMap.zoneAt(pos.getX(), pos.getZ());
		return zone.isDistrict() ? Optional.of(zone.id()) : Optional.empty();
	}

	/** Every scored district id: the ZoneMap's districts plus registered test districts, sorted. */
	public static Set<String> ids() {
		Set<String> out = new TreeSet<>();
		for (Zone zone : ZoneMap.current().zones()) {
			if (zone.isDistrict()) {
				out.add(zone.id());
			}
		}
		out.addAll(TESTS.keySet());
		return out;
	}

	public static boolean isDistrict(String id) {
		return ids().contains(id);
	}

	/** True for a test district the server tick must not drive. */
	public static boolean isManual(String id) {
		TestDistrict test = TESTS.get(id);
		return test != null && test.manual();
	}

	public static GridSettings settings(String id) {
		TestDistrict test = TESTS.get(id);
		return test != null ? test.settings() : GridSettings.get();
	}

	public static SiegeSettings siege(String id) {
		TestDistrict test = TESTS.get(id);
		return test != null ? test.siege() : SiegeSettings.get();
	}

	/** The district's zone tier (River Arts 2 ... Mission 4); 1 if unknown. */
	public static int zoneTier(String id) {
		TestDistrict test = TESTS.get(id);
		if (test != null) {
			return test.zoneTier();
		}
		return ZoneMap.current().byId(id).map(Zone::tier).orElse(1);
	}

	/** Display name: {@code district.ashfall.<id>} from the lang file. */
	public static Component name(String id) {
		return Component.translatableWithFallback("district.ashfall." + id, id);
	}

	/**
	 * The district's key relays as packed chunk positions. Planned from the zone layout and the Lost Cities chunk
	 * plans, cached until either changes. Empty where nothing could be planned (a world without Lost Cities):
	 * the link step then has nothing to do.
	 */
	public static synchronized Set<Long> keyRelayChunks(String id) {
		TestDistrict test = TESTS.get(id);
		if (test != null) {
			return test.keyRelayChunks();
		}
		ZoneMap zones = ZoneMap.current();
		BuildingSource source = AtlasProcessor.buildings();
		if (zones != cachedFor || source != cachedSource) {
			Map<String, Set<Long>> next = new HashMap<>();
			if (!source.isEmpty() && !zones.isEmpty()) {
				for (RelayPlan.KeyRelay relay : RelayPlan.keyRelays(zones, source::chunk)) {
					next.computeIfAbsent(relay.zone(), k -> new TreeSet<>())
							.add(net.minecraft.world.level.ChunkPos.pack(relay.chunkX(), relay.chunkZ()));
				}
			}
			keyRelayCache = next;
			cachedFor = zones;
			cachedSource = source;
		}
		return keyRelayCache.getOrDefault(id, Set.of());
	}

	/** The district whose key relay stands in chunk ({@code chunkX}, {@code chunkZ}), if any. */
	public static Optional<String> keyRelayDistrict(int chunkX, int chunkZ) {
		long key = net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ);
		for (String id : ids()) {
			if (keyRelayChunks(id).contains(key)) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	/** Drops the key relay cache (data reload: zones or plans may have changed; server start and stop). */
	public static synchronized void invalidate() {
		keyRelayCache = Map.of();
		cachedFor = null;
		cachedSource = null;
	}

	/** Test hook: adds a district for one gametest. */
	public static void registerTest(TestDistrict district) {
		TESTS.put(district.id(), district);
	}

	/** Test hook: removes a test district. */
	public static void unregisterTest(String id) {
		TESTS.remove(id);
	}

	/** Whether {@code id} is a gametest district. */
	public static boolean isTest(String id) {
		return TESTS.containsKey(id);
	}
}
