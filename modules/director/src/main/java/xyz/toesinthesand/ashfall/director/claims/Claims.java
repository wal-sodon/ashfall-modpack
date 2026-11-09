package xyz.toesinthesand.ashfall.director.claims;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.haven.Havens;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ashfall's single claim adapter, used by every module: the Director's spawn exclusion, the ZB&B vetoes,
 * Bunker Cores, safe zones and arms' Field Chargers. It used to exist three times (director claims, director
 * spawn claims, arms chargers), each with its own copy of the OPAC lookup; one copy means one place to fix
 * when OPAC's beta API moves.
 * <p>
 * "Claimed" means: inside an OPAC claim (player, party or server), <em>or</em> inside one of Ashfall's own
 * haven regions (safe zones and Bunker Core areas, see {@link Havens}). The haven regions are always checked,
 * not only without OPAC: they are the fallback exclusion when OPAC is absent, and they also cover the case
 * where OPAC refused a Bunker Core's claim (claim limit reached, chunk already taken).
 */
public final class Claims {
	public static final String OPAC_MOD_ID = "openpartiesandclaims";

	private static ClaimBackend backend;
	private static ClaimChecker override;

	private Claims() {
	}

	/** The claims mod adapter: OPAC when installed, otherwise {@link ClaimBackend#NONE}. */
	public static ClaimBackend backend() {
		if (backend == null) {
			backend = FabricLoader.getInstance().isModLoaded(OPAC_MOD_ID) ? new OpacClaimBackend() : ClaimBackend.NONE;
		}
		return backend;
	}

	/** Whether a real claims mod (OPAC) is answering. */
	public static boolean claimsModPresent() {
		return backend().available();
	}

	/** Whether {@code pos} is inside any claim or haven region; see the class comment. */
	public static boolean isClaimed(ServerLevel level, BlockPos pos) {
		ClaimChecker test = override;
		if (test != null) {
			return test.isClaimed(level, pos);
		}
		return backend().isClaimed(level, pos) || Havens.protects(level, pos);
	}

	/**
	 * Claims every chunk of {@code area} for {@code owner} (null = server claim) through the claims mod and
	 * returns the packed chunk positions this call actually claimed. Empty without a claims mod.
	 */
	public static List<Long> claimArea(ServerLevel level, @Nullable UUID owner, ChunkArea area) {
		return claimArea(level, owner, area, false);
	}

	/** {@link #claimArea(ServerLevel, UUID, ChunkArea)}, with server claims on the open safe-zone settings when {@code open}. */
	public static List<Long> claimArea(ServerLevel level, @Nullable UUID owner, ChunkArea area, boolean open) {
		List<Long> claimed = new ArrayList<>();
		if (!claimsModPresent()) {
			return claimed;
		}
		for (long chunk : area.chunks()) {
			if (backend().claim(level, owner, ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), open)) {
				claimed.add(chunk);
			}
		}
		return claimed;
	}

	/**
	 * Moves server claims earlier returned by {@link #claimArea} onto the safe-zone claim settings (see
	 * {@link ClaimBackend#adoptServerClaim}). @return how many changed
	 */
	public static int adoptServerClaims(ServerLevel level, List<Long> chunks, boolean open) {
		if (!claimsModPresent()) {
			return 0;
		}
		int changed = 0;
		for (long chunk : chunks) {
			ChunkPos pos = ChunkPos.unpack(chunk);
			if (backend().adoptServerClaim(level, pos.x(), pos.z(), open)) {
				changed++;
			}
		}
		return changed;
	}

	/** Releases chunks earlier returned by {@link #claimArea}, skipping any that changed hands since. */
	public static void unclaim(ServerLevel level, @Nullable UUID owner, List<Long> chunks) {
		if (!claimsModPresent()) {
			return;
		}
		for (long chunk : chunks) {
			ChunkPos pos = ChunkPos.unpack(chunk);
			backend().unclaim(level, owner, pos.x(), pos.z());
		}
	}

	/** The party {@code player} belongs to, or null (always null without a claims mod). */
	public static @Nullable UUID partyId(MinecraftServer server, UUID player) {
		return backend().partyId(server, player);
	}

	/** Test hook: replaces the whole claimed/unclaimed answer (pass null to go back to the real one). */
	public static void setCheckerForTesting(@Nullable ClaimChecker replacement) {
		override = replacement;
	}
}
