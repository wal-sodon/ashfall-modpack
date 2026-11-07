package xyz.toesinthesand.ashfall.director.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.director.fortify.WearCarry;

/**
 * Placing a worn block's item puts its wear back ({@link WearCarry#placed}), where vanilla copies the item's other
 * components onto the block (before the stack is used up).
 */
@Mixin(BlockItem.class)
abstract class BlockItemWearMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/BlockItem;updateBlockEntityComponents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
	private void ashfall$placeWear(Level level, BlockPos pos, ItemStack stack, Operation<Void> original) {
		original.call(level, pos, stack);
		if (level instanceof ServerLevel server) {
			WearCarry.placed(server, pos, stack);
		}
	}
}
