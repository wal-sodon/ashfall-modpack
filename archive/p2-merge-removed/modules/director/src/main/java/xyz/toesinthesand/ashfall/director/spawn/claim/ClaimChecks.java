package xyz.toesinthesand.ashfall.director.spawn.claim;

import net.fabricmc.loader.api.FabricLoader;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

/**
 * Holds the active {@link ClaimCheck}. OPAC's adapter is chosen only when OPAC is loaded, and the
 * class is named only inside that branch, so OPAC's API classes are never touched (and never needed
 * on the classpath) otherwise.
 */
public final class ClaimChecks {
	public static final String OPAC_MOD_ID = "openpartiesandclaims";

	private static ClaimCheck active = ClaimCheck.NONE;
	private static ClaimCheck override;

	private ClaimChecks() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(OPAC_MOD_ID)) {
			active = new OpacClaimCheck();
			AshfallDirector.LOGGER.info("Director: OPAC claims block zombie spawns");
		}
	}

	public static ClaimCheck get() {
		ClaimCheck test = override;
		return test != null ? test : active;
	}

	/** Test hook: a fake claim check; pass null to restore the real one. */
	public static void setOverrideForTesting(ClaimCheck check) {
		override = check;
	}
}
