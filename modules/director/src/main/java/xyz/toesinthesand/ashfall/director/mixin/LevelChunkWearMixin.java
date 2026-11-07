package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;

/**
 * A worn fortified block that turns into another block (broken, replaced, downgraded, pushed) loses its wear, so
 * a new block placed in the same spot starts fresh. A state change of the same block (a door opening) keeps it. A
 * block that leaves as its own item or on a piston takes the wear along ({@code WearCarry}).
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkWearMixin {
	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void ashfall$clearWear(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null && !old.is(state.getBlock())) {
			FortifyWear.blockChanged((LevelChunk) (Object) this, pos, old);
		}
	}
}
