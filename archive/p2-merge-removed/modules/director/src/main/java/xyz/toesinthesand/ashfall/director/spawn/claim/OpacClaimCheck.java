package xyz.toesinthesand.ashfall.director.spawn.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * OPAC adapter: any claim at all (a player's, a party's, or a server claim used for safe zones)
 * blocks spawning. Only loaded by {@link ClaimChecks} when OPAC is present; OPAC is compile-only.
 * <p>
 * A failure inside OPAC (API not ready during start-up, for example) answers "not claimed" rather
 * than throwing out of the server tick.
 */
final class OpacClaimCheck implements ClaimCheck {
	@Override
	public boolean isClaimed(ServerLevel level, BlockPos pos) {
		try {
			return OpenPACServerAPI.get(level.getServer()).getServerClaimsManager()
					.get(level.dimension().identifier(), pos.getX() >> 4, pos.getZ() >> 4) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
