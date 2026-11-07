package xyz.toesinthesand.ashfall.director.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.director.fortify.WearCarry;

import java.util.List;

/** A worn block's drop of itself carries its wear ({@link WearCarry#tagDrops}), whatever broke it. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateDropsWearMixin {
	@ModifyReturnValue(method = "getDrops", at = @At("RETURN"))
	private List<ItemStack> ashfall$carryWear(List<ItemStack> drops, LootParams.Builder params) {
		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin != null && !drops.isEmpty()) {
			WearCarry.tagDrops(params.getLevel(), origin, (BlockState) (Object) this, drops);
		}
		return drops;
	}
}
