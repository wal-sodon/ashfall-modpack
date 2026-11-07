package xyz.toesinthesand.ashfall.director.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;

/**
 * Low thirst (3 droplets or less) switches off natural regeneration. Done by narrowing the
 * {@code naturalRegeneration} game rule as FoodData reads it, so both healing branches (saturation
 * burst and slow heal) stop while starvation keeps working untouched.
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Ljava/lang/Boolean;booleanValue()Z", ordinal = 0))
	private boolean ashfall$noRegenWhenThirsty(boolean naturalRegeneration, @Local(argsOnly = true) ServerPlayer player) {
		return naturalRegeneration && !Hydration.get(player).blocksRegeneration();
	}
}
