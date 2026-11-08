package xyz.toesinthesand.ashfall.director.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;

/**
 * The only Ashfall class that touches Open Parties and Claims (every module goes through {@link Claims}).
 * {@link Claims} names this class only after checking OPAC is loaded, so the JVM never resolves an OPAC type
 * on a server without it.
 * <p>
 * Player claims go through {@code tryToClaim}, which applies OPAC's own rules (claim limits, other players'
 * claims, claimable dimensions), so a Bunker Core cannot be used to grab land beyond a player's allowance.
 * Server claims use the plain {@code claim} call on the server-claim id, since safe zones are admin
 * decisions and should not count against anyone's limit. Index -1 is OPAC's main sub-config (what
 * {@code IPlayerConfigAPI#getSubIndex()} returns for it). Safe-zone server claims use their own sub-config,
 * {@link SafeZoneClaimOptions#SUB_ID}, so survivors can open the doors and chests inside (the main server claim
 * config refuses everyone everything); it is created on first use and its options re-applied once per server.
 * Open safe zones use a second one, {@link SafeZoneClaimOptions#OPEN_SUB_ID}, set up the same way.
 * <p>
 * Any failure inside OPAC (API not ready during start-up, a future API change) is logged once and answered
 * as "not claimed / nothing done", so a claims problem never takes the server tick down.
 */
final class OpacClaimBackend implements ClaimBackend {
	private static final int MAIN_SUB_CONFIG = -1;
	private boolean warned;
	private boolean warnedOptions;
	/** Per sub-config (closed, open): the server it was set up for, and its index; redone for a new server. */
	private final SubConfig closedSub = new SubConfig(false);
	private final SubConfig openSub = new SubConfig(true);

	private static final class SubConfig {
		final boolean open;
		WeakReference<MinecraftServer> server = new WeakReference<>(null);
		int index = MAIN_SUB_CONFIG;

		SubConfig(boolean open) {
			this.open = open;
		}
	}

	@Override
	public boolean available() {
		return true;
	}

	private static IServerClaimsManagerAPI claims(MinecraftServer server) {
		return OpenPACServerAPI.get(server).getServerClaimsManager();
	}

	private static UUID serverClaimId(MinecraftServer server) {
		return OpenPACServerAPI.get(server).getPlayerConfigManager().getServerClaimConfig().getPlayerId();
	}

	@Override
	public boolean isClaimed(ServerLevel level, BlockPos pos) {
		try {
			return claims(level.getServer()).get(level.dimension().identifier(), pos.getX() >> 4, pos.getZ() >> 4) != null;
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return false;
		}
	}

	@Override
	public boolean claim(ServerLevel level, @Nullable UUID owner, int chunkX, int chunkZ, boolean open) {
		try {
			MinecraftServer server = level.getServer();
			IServerClaimsManagerAPI claims = claims(server);
			Identifier dim = level.dimension().identifier();
			if (claims.get(dim, chunkX, chunkZ) != null) {
				return false; // never take over anyone's claim, ours included (it is already protected)
			}
			if (owner == null) {
				return claims.claim(dim, serverClaimId(server), safeZoneSubIndex(server, open), chunkX, chunkZ, false) != null;
			}
			ClaimResult<IPlayerChunkClaimAPI> result = claims.tryToClaim(dim, owner, MAIN_SUB_CONFIG, chunkX, chunkZ, chunkX, chunkZ, false);
			return result.getResultType().success;
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return false;
		}
	}

	@Override
	public boolean adoptServerClaim(ServerLevel level, int chunkX, int chunkZ, boolean open) {
		try {
			MinecraftServer server = level.getServer();
			IServerClaimsManagerAPI claims = claims(server);
			Identifier dim = level.dimension().identifier();
			IPlayerChunkClaimAPI claim = claims.get(dim, chunkX, chunkZ);
			if (claim == null) {
				return false;
			}
			UUID serverId = serverClaimId(server);
			int sub = safeZoneSubIndex(server, open);
			if (!SafeZoneClaimOptions.shouldAdopt(true, serverId.equals(claim.getPlayerId()), claim.getSubConfigIndex(), sub)) {
				return false;
			}
			return claims.claim(dim, serverId, sub, chunkX, chunkZ, claim.isForceloadable()) != null;
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return false;
		}
	}

	/**
	 * The closed or open safe-zone sub-config's index in the server claim config, creating it and applying
	 * {@link SafeZoneClaimOptions#options(boolean)} the first time per server. Falls back to the main sub-config (the
	 * old, fully locked behaviour) if OPAC refuses to create it, so a safe zone is still claimed.
	 */
	private int safeZoneSubIndex(MinecraftServer server, boolean open) {
		SubConfig cache = open ? openSub : closedSub;
		if (cache.server.get() == server) {
			return cache.index;
		}
		String id = SafeZoneClaimOptions.subId(open);
		// Remembered only once settled, so an OPAC exception here (API not ready yet) is retried on the next claim.
		IPlayerConfigManagerAPI configs = OpenPACServerAPI.get(server).getPlayerConfigManager();
		IPlayerConfigAPI main = configs.getServerClaimConfig();
		IPlayerConfigAPI sub = main.getSubConfig(id);
		if (sub == null) {
			sub = main.createSubConfig(id);
		}
		if (sub == null) {
			AshfallDirector.LOGGER.warn("Open Parties and Claims refused the {} sub-config; safe zones use the locked main server claim config", id);
			cache.server = new WeakReference<>(server);
			cache.index = MAIN_SUB_CONFIG;
			return cache.index;
		}
		for (Map.Entry<String, String> option : SafeZoneClaimOptions.options(cache.open).entrySet()) {
			apply(configs, sub, option.getKey(), option.getValue());
		}
		cache.index = sub.getSubIndex();
		cache.server = new WeakReference<>(server);
		return cache.index;
	}

	/** Sets one String option on the sub-config; options missing on this server (a removed group) are skipped. */
	@SuppressWarnings("unchecked")
	private void apply(IPlayerConfigManagerAPI configs, IPlayerConfigAPI sub, String id, String value) {
		IPlayerConfigOptionSpecAPI<?> spec = configs.getOptionForId(id);
		if (spec == null || spec.getType() != String.class) {
			return;
		}
		IPlayerConfigAPI.SetResult result = sub.tryToSet((IPlayerConfigOptionSpecAPI<String>) spec, value);
		if (result != IPlayerConfigAPI.SetResult.SUCCESS && result != IPlayerConfigAPI.SetResult.DEFAULTED && !warnedOptions) {
			warnedOptions = true;
			AshfallDirector.LOGGER.warn("Open Parties and Claims answered {} setting {}={} on the safe-zone claim config", result, id, value);
		}
	}

	@Override
	public void unclaim(ServerLevel level, @Nullable UUID owner, int chunkX, int chunkZ) {
		try {
			MinecraftServer server = level.getServer();
			IServerClaimsManagerAPI claims = claims(server);
			Identifier dim = level.dimension().identifier();
			IPlayerChunkClaimAPI claim = claims.get(dim, chunkX, chunkZ);
			UUID expected = owner == null ? serverClaimId(server) : owner;
			if (claim != null && expected.equals(claim.getPlayerId())) {
				claims.unclaim(dim, chunkX, chunkZ);
			}
		} catch (RuntimeException | LinkageError e) {
			warn(e);
		}
	}

	@Override
	public @Nullable UUID partyId(MinecraftServer server, UUID player) {
		try {
			IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(player);
			return party == null ? null : party.getId();
		} catch (RuntimeException | LinkageError e) {
			warn(e);
			return null;
		}
	}

	private void warn(Throwable e) {
		if (!warned) {
			warned = true;
			AshfallDirector.LOGGER.warn("Could not use Open Parties and Claims; falling back to Ashfall's own haven regions", e);
		}
	}
}
