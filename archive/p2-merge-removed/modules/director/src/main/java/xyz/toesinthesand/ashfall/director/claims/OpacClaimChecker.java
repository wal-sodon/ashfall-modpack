package xyz.toesinthesand.ashfall.director.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

/**
 * The only Director class that touches Open Parties and Claims. A claim lookup is a map read inside
 * OPAC, cheap enough for the per-action ZB&B veto without a cache of our own.
 */
final class OpacClaimChecker implements ClaimChecker {
	private boolean warned;

	@Override
	public boolean isClaimed(ServerLevel level, BlockPos pos) {
		try {
			return OpenPACServerAPI.get(level.getServer()).getServerClaimsManager()
					.get(level.dimension().identifier(), pos) != null;
		} catch (RuntimeException | LinkageError e) {
			// a future OPAC API change must not take the Director down: treat as unclaimed, warn once
			if (!warned) {
				warned = true;
				AshfallDirector.LOGGER.warn("Could not query Open Parties and Claims; claims are ignored", e);
			}
			return false;
		}
	}
}
