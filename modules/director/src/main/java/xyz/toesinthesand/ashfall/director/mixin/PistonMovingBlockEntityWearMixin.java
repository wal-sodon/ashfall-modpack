package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.director.fortify.WearCarry;

/** A pushed block lands with the wear it left with ({@link WearCarry#landed}), on time or cut short. */
@Mixin(PistonMovingBlockEntity.class)
abstract class PistonMovingBlockEntityWearMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private static void ashfall$landWear(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity entity,
										 CallbackInfo ci) {
		if (level instanceof ServerLevel server) {
			WearCarry.landed(server, pos, entity);
		}
	}

	@Inject(method = "finalTick", at = @At("RETURN"))
	private void ashfall$landWearEarly(CallbackInfo ci) {
		PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
		if (self.getLevel() instanceof ServerLevel server) {
			WearCarry.landed(server, self.getBlockPos(), self);
		}
	}
}
