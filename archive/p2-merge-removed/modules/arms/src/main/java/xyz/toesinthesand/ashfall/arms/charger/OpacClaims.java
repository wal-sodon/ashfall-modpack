package xyz.toesinthesand.ashfall.arms.charger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

/**
 * The only class that touches Open Parties and Claims. Kept separate so the JVM never resolves an OPAC
 * class unless {@link ChargerClaims} has already checked that OPAC is loaded.
 */
final class OpacClaims {
	private static boolean warned;

	private OpacClaims() {
	}

	/** Whether any player or party claims the chunk holding {@code pos}. */
	static boolean isClaimed(ServerLevel level, BlockPos pos) {
		try {
			return OpenPACServerAPI.get(level.getServer()).getServerClaimsManager()
					.get(level.dimension().identifier(), pos) != null;
		} catch (RuntimeException | LinkageError e) {
			// An API change in a future OPAC must not take chargers down with it: fail closed, warn once.
			if (!warned) {
				warned = true;
				AshfallArms.LOGGER.warn("Could not query Open Parties and Claims; Field Chargers will stay off", e);
			}
			return false;
		}
	}
}
