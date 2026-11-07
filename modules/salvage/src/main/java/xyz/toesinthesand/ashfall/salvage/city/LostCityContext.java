package xyz.toesinthesand.ashfall.salvage.city;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.salvage.city.lc.LostCitiesBridge;

import java.util.Optional;

/**
 * Lost Cities-free entry point to The Lost Cities. Every call first checks the mod is loaded, and only
 * then touches {@link LostCitiesBridge}, so salvage (and director/arms on top of it) load fine without
 * Lost Cities: the JVM never resolves a Lost Cities class unless the bridge runs.
 */
public final class LostCityContext {
	public static final String MOD_ID = "lostcities";
	/** Name of our Lost Cities profile; the zone hooks only act on levels that use it. */
	public static final String PROFILE = "ashfall";

	private LostCityContext() {
	}

	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	/** The Lost Cities profile name of this level, or empty if Lost Cities is absent or inactive there. */
	public static Optional<String> profile(ServerLevel level) {
		return available() ? Optional.ofNullable(LostCitiesBridge.profile(level)) : Optional.empty();
	}

	/** True if this level generates with the Ashfall profile (so the zone layout applies to it). */
	public static boolean isAshfall(ServerLevel level) {
		return profile(level).filter(PROFILE::equals).isPresent();
	}

	/**
	 * Lost Cities' plan for a chunk. Deterministic and cached by Lost Cities, and valid for chunks that are
	 * not generated yet. Empty when Lost Cities is absent or inactive in this level.
	 */
	public static Optional<CityChunk> chunk(ServerLevel level, int chunkX, int chunkZ) {
		return available() ? Optional.ofNullable(LostCitiesBridge.chunk(level, chunkX, chunkZ)) : Optional.empty();
	}
}
