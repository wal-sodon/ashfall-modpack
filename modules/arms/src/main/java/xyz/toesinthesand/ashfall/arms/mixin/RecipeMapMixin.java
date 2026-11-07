package xyz.toesinthesand.ashfall.arms.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.arms.item.DisabledRecipes;

import java.util.stream.Stream;

/**
 * Leaves {@link DisabledRecipes} out of the recipe map the server crafts from. Why not a datapack override: each
 * mod is its own data pack and a world keeps the pack order it was created with, so an Ashfall file of the same
 * id loses to Traveler's Backpack's own in most worlds.
 */
@Mixin(RecipeMap.class)
abstract class RecipeMapMixin {
	@ModifyExpressionValue(method = "create", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup;listElements()Ljava/util/stream/Stream;"))
	private static Stream<Holder.Reference<Recipe<?>>> ashfall$dropDisabled(Stream<Holder.Reference<Recipe<?>>> recipes) {
		return recipes.filter(holder -> !DisabledRecipes.isDisabled(holder.key().identifier()));
	}
}
