package xyz.toesinthesand.ashfall.salvage.mixin.lostcities;

import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.City;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistries;
import mcjty.lostcities.worldgen.lost.cityassets.CityStyle;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.city.CityZoneRules;
import xyz.toesinthesand.ashfall.salvage.city.lc.LostCitiesBridge;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer B of the plan (section 1.3): the zone layout drives Lost Cities' two raw decisions.
 * <ul>
 * <li>{@code City.getCityFactor}: 1 in the four scored districts, 0 in wilderness zones. Lost Cities derives "is this chunk city" from
 * this factor everywhere (characteristics, streets, highways, railways, scattered structures), so
 * zeroing it here keeps all of them consistent. Setting {@code isCity = false} later in a
 * {@code CHARACTERISTICS} listener would leave roads and rails that still think the chunk is city.</li>
 * <li>{@code City.getCityStyle}: the zone's city style. Building, multibuilding and street choices are all
 * made from this style before the {@code CHARACTERISTICS} event fires, so a listener that swapped the style
 * afterwards would get the style's name but not its buildings or floor limits.</li>
 * </ul>
 * Data-only Layer A could not do this: predefined cities are clipped to {@code cityMaxRadius} and the
 * world style can only select styles by biome. Both hooks only act for the {@code ashfall} profile.
 */
@Mixin(value = City.class, remap = false)
public abstract class CityMixin {
	@Unique
	private static final Set<String> ashfall$missingStyles = ConcurrentHashMap.newKeySet();

	@Inject(method = "getCityFactor", at = @At("HEAD"), cancellable = true)
	private static void ashfall$zoneCityFactor(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile,
			CallbackInfoReturnable<Float> cir) {
		// Lost Cities forces full city factor for a predefined building's own chunk and the three chunks
		// a 2x2 multibuilding covers, but not beyond: the third column of our 3x2 hospital fell outside
		// the city on low-factor terrain (water) and simply did not generate. Every chunk a predefined
		// building occupies must be city, for any profile, since our landmarks are predefined everywhere.
		if (provider.getWorld() != null && City.isChunkOccupied(provider, coord)) {
			cir.setReturnValue(1.0f);
			return;
		}
		if (!LostCitiesBridge.isAshfall(profile)) {
			return;
		}
		// Districts are always city (see CityZoneRules#forcesCity). This needs no world, so it also holds when
		// Lost Cities asks speculatively with a null world, where the occupied-chunk check above can't run.
		if (CityZoneRules.forcesCity(coord.chunkX(), coord.chunkZ())) {
			cir.setReturnValue(1.0f);
		} else if (!CityZoneRules.allowsCity(coord.chunkX(), coord.chunkZ())) {
			cir.setReturnValue(0.0f);
		}
	}

	@Inject(method = "getCityStyle", at = @At("HEAD"), cancellable = true)
	private static void ashfall$zoneCityStyle(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile,
			CallbackInfoReturnable<CityStyle> cir) {
		if (!LostCitiesBridge.isAshfall(profile)) {
			return;
		}
		Optional<String> style = CityZoneRules.cityStyle(coord.chunkX(), coord.chunkZ());
		WorldGenLevel world = provider.getWorld();
		if (style.isEmpty() || world == null) {
			return;
		}
		CityStyle resolved = AssetRegistries.CITYSTYLES.get(world, style.get());
		if (resolved != null) {
			cir.setReturnValue(resolved);
		} else if (ashfall$missingStyles.add(style.get())) {
			AshfallSalvage.LOGGER.error("Zone city style {} is not a Lost Cities asset; using Lost Cities' own choice", style.get());
		}
	}
}
