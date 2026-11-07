package xyz.toesinthesand.ashfall.salvage.city.lc;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.api.LostCityEvents;
import mcjty.lostcities.api.ILostChunkInfo;
import mcjty.lostcities.api.ILostCityCityStyle;
import mcjty.lostcities.api.ILostCityInformation;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.config.ProfileSetup;
import mcjty.lostcities.setup.Config;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Landmarks;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The only class (besides the mixins) that touches The Lost Cities. Reached through
 * {@link LostCityContext}, which checks the mod is loaded first.
 * <p>
 * Lost Cities API used here: {@code LostCities.lostCitiesImp.getLostInfo(level).getChunkInfo(cx, cz)}
 * for chunk plans, {@code Config.getProfileForDimension} to see which profile a level uses, and
 * {@code ProfileSetup.STANDARD_PROFILES} to register our profile, and the {@code PRE_EXPLOSION} event to
 * keep landmarks intact.
 */
public final class LostCitiesBridge {
	private static final String PROFILE_RESOURCE = "/ashfall/lostcities/ashfall_profile.json";

	private LostCitiesBridge() {
	}

	/**
	 * Registers the {@code ashfall} profile from our jar. We own the profile in code rather than shipping a
	 * file in config/ because Lost Cities rewrites every registered profile into
	 * {@code config/lostcities/profiles/} at start and then reads that folder back: putting ours in the map
	 * means the written file always matches this jar, whichever of the two mods initialises first, and the
	 * profile also exists in gametest and dedicated-server runs that have no pack overrides.
	 */
	public static void registerProfile() {
		try (InputStream in = LostCitiesBridge.class.getResourceAsStream(PROFILE_RESOURCE)) {
			if (in == null) {
				AshfallSalvage.LOGGER.error("Missing {} in the salvage jar; the Ashfall Lost Cities profile is off", PROFILE_RESOURCE);
				return;
			}
			String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			ProfileSetup.STANDARD_PROFILES.put(LostCityContext.PROFILE, new LostCityProfile(LostCityContext.PROFILE, json));
			AshfallSalvage.LOGGER.info("Registered the '{}' Lost Cities profile", LostCityContext.PROFILE);
		} catch (IOException | RuntimeException e) {
			AshfallSalvage.LOGGER.error("Could not register the Ashfall Lost Cities profile", e);
		}
	}

	/**
	 * Cancels Lost Cities' explosion damage and debris in every chunk of a landmark. Predefined buildings
	 * with {@code preventruins} are only spared the ruin pass; the separate explosion pass (city styles
	 * set explosion chances up to 1.0 in the wasteland) would still blow holes in the substations,
	 * hospital wards and stairs that progression depends on. The event fires once per chunk during
	 * generation, after the chunk plan exists, so asking for the chunk's building is safe there.
	 */
	public static void registerLandmarkProtection() {
		LostCityEvents.PRE_EXPLOSION.register(event -> {
			ILostCityInformation info = event.getLostCities().getLostInfo(event.getWorld().getLevel());
			ILostChunkInfo ci = info == null ? null : info.getChunkInfo(event.getChunkX(), event.getChunkZ());
			if (ci != null && ci.getBuildingId() != null && Landmarks.isLandmarkBuilding(ci.getBuildingId().toString())) {
				event.setCanceled(true);
			}
		});
	}

	public static String profile(ServerLevel level) {
		return Config.getProfileForDimension(level, level.dimension());
	}

	public static CityChunk chunk(ServerLevel level, int chunkX, int chunkZ) {
		ILostCityInformation info = LostCities.lostCitiesImp.getLostInfo(level);
		if (info == null) {
			return null;
		}
		ILostChunkInfo ci = info.getChunkInfo(chunkX, chunkZ);
		if (ci == null) {
			return null;
		}
		boolean city = ci.isCity();
		ILostChunkInfo.MultiBuildingInfo multi = city ? ci.getMultiBuildingInfo() : null;
		ILostCityCityStyle style = city ? ci.getCityStyle() : null;
		String building = city && ci.getBuildingId() != null ? ci.getBuildingId().toString() : null;
		// MultiBuildingInfo's offsets are this chunk's MultiPos inside the multibuilding (0, 0 = north-west), and
		// w/h its size in chunks; together they locate the origin chunk every part of the building shares.
		boolean isMulti = multi != null && building != null;
		return new CityChunk(chunkX, chunkZ, city, building,
				isMulti && multi.buildingType() != null ? multi.buildingType().toString() : null,
				building != null ? ci.getNumFloors() : 0, building != null ? ci.getNumCellars() : 0,
				ci.getCityLevel(), info.getRealHeight(ci.getCityLevel()),
				style != null && style.getId() != null ? style.getId().toString() : null,
				building != null ? ci.getRuinLevel() : -1,
				isMulti ? multi.offsetX() : 0, isMulti ? multi.offsetZ() : 0,
				isMulti ? Math.max(1, multi.w()) : 1, isMulti ? Math.max(1, multi.h()) : 1);
	}

	/** True if a Lost Cities profile is one whose generation our zone hooks should shape. */
	public static boolean isAshfall(LostCityProfile profile) {
		return profile != null && LostCityContext.PROFILE.equals(profile.getName());
	}
}
