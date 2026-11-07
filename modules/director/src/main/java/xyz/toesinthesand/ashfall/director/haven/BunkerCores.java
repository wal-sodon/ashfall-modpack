package xyz.toesinthesand.ashfall.director.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.claims.Claims;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;

/**
 * Bunker Core rules, separate from the block so the gametests drive them directly.
 * <ul>
 *   <li><b>One core per party or player.</b> The owner key is the player's OPAC party id when they are in a
 *   party (so a party shares one core and one respawn), otherwise their own UUID.</li>
 *   <li><b>Claim.</b> Placing a core claims the chunks within {@link HavenSettings#bunkerRadius} through OPAC
 *   in the placer's name (party members then share it under OPAC's party permissions), and always adds the
 *   same square to the haven regions, which is the whole protection when OPAC is absent or refused some
 *   chunks.</li>
 *   <li><b>Shared storage</b> is OPAC's: chests inside the claim are open to the party and closed to others
 *   (OPAC's default container protection). The core has no inventory of its own; a second shared-storage
 *   system would duplicate OPAC and need its own access rules. Without OPAC nothing is locked anyway.</li>
 *   <li><b>Respawn.</b> The core is a respawn option for every member (see {@link RespawnChoice}).</li>
 * </ul>
 */
public final class BunkerCores {
	private BunkerCores() {
	}

	/** The key under which {@code player}'s core is stored: party id, else the player. */
	public static UUID ownerKey(MinecraftServer server, UUID player) {
		UUID party = Claims.partyId(server, player);
		return party != null ? party : player;
	}

	/** The core {@code player} may respawn at: their party's, else one they placed themselves. */
	public static Optional<BunkerCoreRecord> forPlayer(MinecraftServer server, UUID player) {
		HavenState state = HavenState.get(server);
		BunkerCoreRecord core = state.core(ownerKey(server, player));
		if (core == null) {
			for (BunkerCoreRecord c : state.cores()) {
				if (c.placedBy().equals(player)) {
					core = c;
					break;
				}
			}
		}
		if (core != null && isStale(server, core)) {
			forget(server, core);
			core = null;
		}
		return Optional.ofNullable(core);
	}

	/** Whether {@code player} may place a new core (no live core under their key). */
	public static boolean mayPlace(MinecraftServer server, UUID player) {
		BunkerCoreRecord existing = HavenState.get(server).core(ownerKey(server, player));
		if (existing == null) {
			return true;
		}
		if (isStale(server, existing)) {
			forget(server, existing);
			return true;
		}
		return false;
	}

	/**
	 * Registers a newly placed core: saves it, claims its chunks through OPAC and rebuilds the haven regions.
	 * Returns the record, or null if the placer already owns a live core elsewhere (the item checks first, so
	 * this only happens when something places the block directly).
	 */
	public static BunkerCoreRecord onPlaced(ServerLevel level, BlockPos pos, UUID placer) {
		MinecraftServer server = level.getServer();
		if (!mayPlace(server, placer)) {
			return null;
		}
		ChunkArea area = ChunkArea.around(pos.getX(), pos.getZ(), HavenSettings.get().bunkerRadius());
		List<Long> claimed = Claims.claimArea(level, placer, area);
		BunkerCoreRecord core = new BunkerCoreRecord(ownerKey(server, placer), placer, level.dimension(), pos.immutable(),
				area.radius(), List.copyOf(claimed));
		HavenState.get(server).putCore(core);
		Havens.rebuild(server);
		AshfallDirector.LOGGER.info("Bunker Core placed at {} by {} ({} chunks claimed through OPAC)", pos.toShortString(), placer, claimed.size());
		return core;
	}

	/** Forgets the core at {@code pos} (it was broken or replaced) and releases its OPAC claims. */
	public static void onRemoved(ServerLevel level, BlockPos pos) {
		BunkerCoreRecord core = HavenState.get(level.getServer()).coreAt(level.dimension(), pos);
		if (core != null) {
			forget(level.getServer(), core);
		}
	}

	private static void forget(MinecraftServer server, BunkerCoreRecord core) {
		HavenState.get(server).removeCore(core.ownerKey());
		ServerLevel level = server.getLevel(core.dimension());
		if (level != null) {
			Claims.unclaim(level, core.placedBy(), core.claimedChunks());
		}
		Havens.rebuild(server);
	}

	/**
	 * A record whose block is gone without the removal hook running (an admin's {@code /setblock} skips
	 * neighbour updates, and with them the hook). Only checked where the chunk is loaded; an unloaded core
	 * is assumed intact rather than loading its chunk.
	 */
	private static boolean isStale(MinecraftServer server, BunkerCoreRecord core) {
		ServerLevel level = server.getLevel(core.dimension());
		if (level == null) {
			return true;
		}
		return LoadedChunks.ready(level, core.pos()) && !level.getBlockState(core.pos()).is(HavenBlocks.BUNKER_CORE);
	}

	/**
	 * A standing spot next to the core: the first horizontal neighbour (at the core's height, then one up)
	 * with a floor and two blocks of room, else the block above the core.
	 */
	public static BlockPos respawnSpot(ServerLevel level, BlockPos core) {
		for (int dy = 0; dy <= 1; dy++) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos feet = core.relative(dir).above(dy);
				if (standable(level, feet)) {
					return feet;
				}
			}
		}
		return core.above();
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				&& level.noCollision(EntityTypes.PLAYER.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
	}
}
