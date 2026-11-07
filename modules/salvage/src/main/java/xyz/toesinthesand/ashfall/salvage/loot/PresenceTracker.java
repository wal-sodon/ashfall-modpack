package xyz.toesinthesand.ashfall.salvage.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every {@link #INTERVAL} ticks, records which buildings and zones have a player near them.
 * <p>
 * Refill is decided lazily when a container is opened, but "has this place been left alone?" can
 * only be answered by watching continuously; this is the cheap watcher. It only touches the
 * building index chunks around each player, so cost scales with players, not with the map.
 */
public final class PresenceTracker {
	public static final int INTERVAL = 100;

	/** Per-building refill rules (widest radius, longest quiet time of its units' profiles). */
	private static final Map<Long, BuildingProfile.Refill> BUILDING_RULES = new HashMap<>();
	private static Object rulesProfiles;
	private static Object rulesIndex;
	private static int maxRadius;

	private PresenceTracker() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0 || server.getPlayerList().getPlayers().isEmpty()) {
			return;
		}
		ZoneManager manager = ZoneManager.get(server);
		long now = server.overworld().getGameTime();
		BuildingSource index = AshfallSalvage.buildings();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			BlockPos pos = player.blockPosition();
			ServerLevel level = player.level();
			if (level.dimension() == Level.OVERWORLD && !index.isEmpty()) {
				markBuildings(manager, index, pos, now);
			}
			for (LootZone zone : manager.zones()) {
				long reach = zone.radius() + zone.refill().noPlayerRadius();
				if (zone.dimension() == level.dimension() && zone.horizontalDistSq(pos) <= reach * reach) {
					manager.markSeen(zone.areaKey(), now, zone.refill().quietTicks());
				}
			}
		}
	}

	private static void markBuildings(ZoneManager manager, BuildingSource index, BlockPos pos, long now) {
		refreshRules(index);
		int r = maxRadius;
		Set<Long> done = new HashSet<>();
		for (int cx = (pos.getX() - r) >> 4; cx <= (pos.getX() + r) >> 4; cx++) {
			for (int cz = (pos.getZ() - r) >> 4; cz <= (pos.getZ() + r) >> 4; cz++) {
				for (Building building : index.inChunk(cx, cz)) {
					if (!done.add(building.id())) {
						continue;
					}
					BuildingProfile.Refill rules = rulesFor(building);
					if (distanceToBox(building.footprint(), pos) <= rules.noPlayerRadius()) {
						manager.markSeen(buildingKey(building), now, rules.quietTicks());
					}
				}
			}
		}
	}

	public static String buildingKey(Building building) {
		return "b:" + building.id();
	}

	/** Horizontal distance from a block to a footprint's bounding box (0 inside it). */
	static double distanceToBox(Footprint f, BlockPos pos) {
		return xyz.toesinthesand.ashfall.salvage.building.BuildingFinder.distanceToBox(f, pos);
	}

	/**
	 * The building's area rules: the widest no-player radius and the longest quiet time among the
	 * profiles of its units, so a mixed building errs towards refilling less, never more.
	 */
	public static synchronized BuildingProfile.Refill rulesFor(Building building) {
		refreshRules(AshfallSalvage.buildings());
		return BUILDING_RULES.computeIfAbsent(building.id(), id -> {
			int radius = 0;
			long quiet = 0;
			boolean any = false;
			for (Unit unit : building.units()) {
				BuildingProfile.Refill r = refillOf(BuildingProfiles.classify(building, unit));
				radius = Math.max(radius, r.noPlayerRadius());
				quiet = Math.max(quiet, r.quietTicks());
				any = true;
			}
			if (!any) {
				Identifier byTags = BuildingProfiles.classifyByTags(building);
				return refillOf(byTags != null ? byTags : BuildingProfiles.GENERIC);
			}
			return new BuildingProfile.Refill(true, 0, radius, quiet);
		});
	}

	private static BuildingProfile.Refill refillOf(Identifier profileId) {
		BuildingProfile profile = BuildingProfiles.get(profileId);
		return profile != null ? profile.refill() : BuildingProfile.Refill.DEFAULT;
	}

	/**
	 * Forgets the cached rules on server stop. Why: {@code rulesIndex} is the stopped world's building source, whose
	 * Lost Cities lookup captures that world's {@code ServerLevel}; in singleplayer the JVM lives on, and a next world
	 * without Lost Cities never refreshes the cache, so world A (chunks, entities and all) stayed reachable.
	 */
	public static synchronized void reset() {
		BUILDING_RULES.clear();
		rulesProfiles = null;
		rulesIndex = null;
		maxRadius = 0;
	}

	/** Drops the cache when profiles reload or another world's index is loaded. */
	private static synchronized void refreshRules(BuildingSource index) {
		Map<Identifier, BuildingProfile> profiles = BuildingProfiles.all();
		if (profiles == rulesProfiles && index == rulesIndex) {
			return;
		}
		BUILDING_RULES.clear();
		rulesProfiles = profiles;
		rulesIndex = index;
		int r = BuildingProfile.Refill.DEFAULT.noPlayerRadius();
		for (BuildingProfile p : profiles.values()) {
			r = Math.max(r, p.refill().noPlayerRadius());
		}
		maxRadius = r;
	}
}
