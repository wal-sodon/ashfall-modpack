package xyz.toesinthesand.ashfall.arms.charger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.director.claims.Claims;

/**
 * Where claim-bound chargers may run. Goes through the Director's single claim adapter ({@link Claims}), so
 * a Field Charger works in an OPAC claim, a safe zone or a Bunker Core's area alike.
 * <p>
 * Open Parties and Claims is optional: without it every position counts as allowed, so a pack without a
 * claims mod still has working Field Chargers.
 */
public final class ChargerClaims {
	private ChargerClaims() {
	}

	/** Whether a claim-bound charger at {@code pos} may run. */
	public static boolean allowed(ServerLevel level, BlockPos pos) {
		return !Claims.claimsModPresent() || Claims.isClaimed(level, pos);
	}
}
