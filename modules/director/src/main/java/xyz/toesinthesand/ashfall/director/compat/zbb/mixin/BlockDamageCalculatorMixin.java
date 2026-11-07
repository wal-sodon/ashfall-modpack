package xyz.toesinthesand.ashfall.director.compat.zbb.mixin;

import com.tik.zbb.config.ConfigSnapshot;
import com.tik.zbb.utilities.BlockDamageCalculator;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.special.Brute;

/**
 * The brute breaks in faster. {@code getDamageToBlocks} is ZB&B's per-hit block damage (it already grows with
 * hitbox size, which the brute's scale feeds); the brute's {@code block_break_multiplier} scales the result.
 * Fortified blocks are not affected: {@link BreakActionMixin} refuses them before any damage is computed (they wear
 * at a flat rate per zombie instead).
 */
@Mixin(BlockDamageCalculator.class)
abstract class BlockDamageCalculatorMixin {
	@Inject(method = "getDamageToBlocks(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/level/block/state/BlockState;Lcom/tik/zbb/config/ConfigSnapshot;)I",
			at = @At("RETURN"), cancellable = true)
	private static void ashfall$bruteMultiplier(PathfinderMob mob, BlockState state, ConfigSnapshot config, CallbackInfoReturnable<Integer> cir) {
		float multiplier = Brute.blockBreakMultiplier(mob);
		if (multiplier != 1f) {
			cir.setReturnValue(Math.max(0, Math.round(cir.getReturnValue() * multiplier)));
		}
	}
}
