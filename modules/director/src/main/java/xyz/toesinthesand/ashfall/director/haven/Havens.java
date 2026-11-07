package xyz.toesinthesand.ashfall.director.haven;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import xyz.toesinthesand.ashfall.director.story.StoryHooks;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.claims.Claims;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Havens (plan M4): Bunker Cores, server safe zones, the respawn choice with respawn protection, and the horde
 * siren. This class wires them up and owns the region list the claim adapter checks.
 * <p>
 * <b>Regions.</b> Every safe zone and every Bunker Core area is kept in one immutable snapshot list, rebuilt
 * whenever one changes (server start, datapack reload, a core placed or broken, a safe-zone command). The
 * spawn loop and the ZB&B vetoes ask {@link #protects} for every candidate, so the read is a plain scan of a
 * short list with no saved-data or chunk access; there are a handful of safe zones and one core per party.
 * <p>
 * <b>Partial protection</b> (plan rule): safe zones and Bunker Core areas both stop Director spawns and ZB&B
 * building. Only safe zones also stop ZB&B breaking; in a Bunker Core's area a horde may still break normal
 * blocks, and only {@code #ashfall:fortified} blocks hold (the existing veto), so a base is as strong as its
 * walls.
 */
public final class Havens {
	/** One protected area. */
	public record Region(String id, ResourceKey<Level> dimension, ChunkArea area, boolean safeZone) {
		boolean contains(ResourceKey<Level> dim, BlockPos pos) {
			return dimension.equals(dim) && area.contains(pos.getX(), pos.getZ());
		}
	}

	private static volatile List<Region> regions = List.of();

	private Havens() {
	}

	public static void init() {
		HavenBlocks.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SafeZones.ID, new SafeZones());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(HavenSettings.Loader.ID, new HavenSettings.Loader());
		ServerLifecycleEvents.SERVER_STARTED.register(Havens::refresh);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> refresh(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> regions = List.of());
		RespawnChoice.init();
		HordeSiren.init();
		BunkerBeds.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			SafeZoneCommand.register(dispatcher);
			RespawnChoice.registerCommand(dispatcher);
		});
	}

	/** Rebuilds the region list and makes sure every active safe zone holds its OPAC server claim. */
	public static void refresh(MinecraftServer server) {
		syncServerClaims(server);
		rebuild(server);
	}

	/**
	 * Rebuilds the snapshot from the current safe zones and cores. A core's region grows by the story's
	 * {@link StoryHooks#bunkerRadiusBonus} (the Bind ending powers Bunker Cores from the Heart). Only the haven region
	 * grows, not the OPAC claim: claims are made once at placement in the placer's name, and re-claiming every core
	 * on an ending would need each owner online.
	 */
	public static void rebuild(MinecraftServer server) {
		List<Region> next = new ArrayList<>();
		SafeZones.active(server).forEach((id, zone) -> next.add(new Region(id, zone.dimension(), zone.area(), true)));
		int bonus = StoryHooks.bunkerRadiusBonus(server);
		for (BunkerCoreRecord core : HavenState.get(server).cores()) {
			ChunkArea area = core.area();
			if (bonus > 0) {
				area = new ChunkArea(area.chunkX(), area.chunkZ(), area.radius() + bonus);
			}
			next.add(new Region("bunker:" + core.ownerKey(), core.dimension(), area, false));
		}
		regions = List.copyOf(next);
	}

	/**
	 * Claims each active safe zone's chunks as OPAC server claims (chunks someone already claimed are left
	 * alone), and releases the server claims of zones that were removed. Does nothing without OPAC: the
	 * region list is then the whole protection.
	 */
	static void syncServerClaims(MinecraftServer server) {
		if (!Claims.claimsModPresent()) {
			return;
		}
		HavenState state = HavenState.get(server);
		Map<String, SafeZone> active = SafeZones.active(server);
		for (Map.Entry<String, SafeZone> e : active.entrySet()) {
			ServerLevel level = server.getLevel(e.getValue().dimension());
			if (level != null) {
				// Zones claimed before the safe-zone claim settings existed were locked to everyone; move them over.
				int adopted = Claims.adoptServerClaims(level, state.serverClaims(e.getKey()));
				if (adopted > 0) {
					AshfallDirector.LOGGER.info("Safe zone {}: moved {} claimed chunks to the safe-zone claim settings", e.getKey(), adopted);
				}
				List<Long> claimed = Claims.claimArea(level, null, e.getValue().area());
				state.addServerClaims(e.getKey(), claimed);
				if (!claimed.isEmpty()) {
					AshfallDirector.LOGGER.info("Safe zone {}: server-claimed {} chunks", e.getKey(), claimed.size());
				}
			}
		}
		for (String id : List.copyOf(state.serverClaimZoneIds())) {
			if (!active.containsKey(id)) {
				releaseServerClaims(server, id, null);
			}
		}
	}

	/** Releases the OPAC server claims recorded for a zone ({@code zone} may be null when it is already gone). */
	static void releaseServerClaims(MinecraftServer server, String id, SafeZone zone) {
		List<Long> chunks = HavenState.get(server).takeServerClaims(id);
		if (chunks.isEmpty()) {
			return;
		}
		ServerLevel level = zone == null ? server.overworld() : server.getLevel(zone.dimension());
		if (level != null) {
			Claims.unclaim(level, null, chunks);
		}
	}

	/** Whether {@code pos} lies in any haven: a safe zone or a Bunker Core's area. */
	public static boolean protects(ServerLevel level, BlockPos pos) {
		ResourceKey<Level> dim = level.dimension();
		for (Region region : regions) {
			if (region.contains(dim, pos)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code pos} lies in a Bunker Core's area (not a safe zone). */
	public static boolean inBunker(ServerLevel level, BlockPos pos) {
		ResourceKey<Level> dim = level.dimension();
		for (Region region : regions) {
			if (!region.safeZone && region.contains(dim, pos)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code pos} lies in a safe zone (stricter than a bunker: no breaking either). */
	public static boolean inSafeZone(ServerLevel level, BlockPos pos) {
		ResourceKey<Level> dim = level.dimension();
		for (Region region : regions) {
			if (region.safeZone && region.contains(dim, pos)) {
				return true;
			}
		}
		return false;
	}

	public static List<Region> regions() {
		return regions;
	}

	/** The active safe zone whose centre is nearest {@code pos}, preferring {@code dim}; empty when there are none. */
	public static Optional<Map.Entry<String, SafeZone>> nearestSafeZone(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
		Map.Entry<String, SafeZone> best = null;
		double bestDist = Double.MAX_VALUE;
		boolean bestSameDim = false;
		for (Map.Entry<String, SafeZone> e : SafeZones.active(server).entrySet()) {
			boolean sameDim = e.getValue().dimension().equals(dim);
			double dist = sameDim ? e.getValue().area().distanceSqTo(pos.getX(), pos.getZ()) : Double.MAX_VALUE;
			if (best == null || (sameDim && !bestSameDim) || (sameDim == bestSameDim && dist < bestDist)) {
				best = e;
				bestDist = dist;
				bestSameDim = sameDim;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Where players respawn in a safe zone: its explicit {@code respawn}, else the world spawn when the zone
	 * asks for it and the spawn lies inside, else the surface at the area's centre (this generates that
	 * chunk if needed, which is fine at respawn time).
	 */
	public static BlockPos respawnPoint(ServerLevel level, SafeZone zone) {
		if (zone.respawn().isPresent()) {
			return zone.respawn().get();
		}
		LevelData.RespawnData world = level.getServer().overworld().getRespawnData();
		if (zone.useWorldSpawn() && world != null && world.dimension().equals(zone.dimension())
				&& zone.area().contains(world.pos().getX(), world.pos().getZ())) {
			return world.pos();
		}
		int x = zone.area().centerBlockX(), z = zone.area().centerBlockZ();
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}
}
