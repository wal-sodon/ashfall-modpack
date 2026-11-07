package xyz.toesinthesand.ashfall.salvage.mixin.lostcities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.LostCityTerrainFeature;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.salvage.city.lc.LostCitiesBridge;
import xyz.toesinthesand.ashfall.salvage.loot.LootPlaceholder;

/**
 * Gives every container Lost Cities fills in an Ashfall world our placeholder table instead of its own (see
 * {@link LootPlaceholder} for why). {@code createLoot} is the single place Lost Cities assigns container loot,
 * for every condition (Lost Cities' {@code chestloot}, Modern Tweaks' {@code lcmt:*_loot}, our own), so one
 * hook covers them all. Other profiles keep Lost Cities' loot.
 */
@Mixin(value = LostCityTerrainFeature.class, remap = false)
public abstract class LostCityLootMixin {
	@WrapOperation(method = "createLoot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/entity/RandomizableContainerBlockEntity;setLootTable(Lnet/minecraft/resources/ResourceKey;)V"))
	private static void ashfall$placeholderLoot(RandomizableContainerBlockEntity container, ResourceKey<LootTable> table,
			Operation<Void> original, @Local(argsOnly = true) IDimensionInfo provider) {
		boolean ashfall = provider != null && LostCitiesBridge.isAshfall(provider.getProfile());
		original.call(container, ashfall ? LootPlaceholder.replace(table) : table);
	}
}
