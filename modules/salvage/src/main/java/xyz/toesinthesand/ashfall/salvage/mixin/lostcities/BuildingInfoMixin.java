package xyz.toesinthesand.ashfall.salvage.mixin.lostcities;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.salvage.city.CityZoneRules;
import xyz.toesinthesand.ashfall.salvage.city.lc.LostCitiesBridge;

/**
 * Per-zone ruins. Lost Cities only has a profile-wide {@code ruinChance}, and a city style's
 * {@code profile_overrides} can only set {@code openLotParkChance} in 26.3-12.0.0, so the plan's "light
 * ruins in the core, heavy in the outskirts" needs this hook.
 * <p>
 * It swaps the single read of {@code profile.RUIN_CHANCE} in the constructor's ruin roll for the zone's
 * chance. The surrounding {@code && !predefinedBuilding.preventRuins()} test is untouched, so landmarks
 * placed with {@code preventruins} stay intact. The profile keeps a non-zero ruin chance because Lost
 * Cities skips ruin generation entirely when the profile value is 0.
 */
@Mixin(value = BuildingInfo.class, remap = false)
public abstract class BuildingInfoMixin {
	@Shadow
	@Final
	public ChunkCoord coord;

	@Shadow
	@Final
	public LostCityProfile profile;

	@ModifyExpressionValue(method = "<init>",
			at = @At(value = "FIELD", target = "Lmcjty/lostcities/config/LostCityProfile;RUIN_CHANCE:F"))
	private float ashfall$zoneRuinChance(float original) {
		if (!LostCitiesBridge.isAshfall(profile)) {
			return original;
		}
		return CityZoneRules.ruinChance(coord.chunkX(), coord.chunkZ(), original);
	}
}
