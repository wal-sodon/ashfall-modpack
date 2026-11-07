package xyz.toesinthesand.ashfall.director.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.director.fortify.WearCarry;

import java.util.List;
import java.util.Map;

/**
 * A piston carries the wear of the blocks it moves (push or pull, slime and honey chains alike): read for all of
 * them before the first one changes, then put on each moving block entity ({@link WearCarry#startMove}).
 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockWearMixin {
	@ModifyExpressionValue(method = "moveBlocks", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;"))
	private List<BlockPos> ashfall$readWear(List<BlockPos> toPush, @Local(argsOnly = true) Level level,
											@Share("wear") LocalRef<Map<BlockPos, Integer>> wear) {
		if (level instanceof ServerLevel server) {
			wear.set(WearCarry.beforeMove(server, toPush));
		}
		return toPush;
	}

	@WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/piston/MovingPistonBlock;newMovingBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;ZZ)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
	private BlockEntity ashfall$carryWear(BlockPos pos, BlockState moving, BlockState moved, Direction facing,
										  boolean extending, boolean isSourcePiston, Operation<BlockEntity> original,
										  @Share("wear") LocalRef<Map<BlockPos, Integer>> wear) {
		BlockEntity entity = original.call(pos, moving, moved, facing, extending, isSourcePiston);
		Map<BlockPos, Integer> before = wear.get();
		if (!isSourcePiston && before != null && !before.isEmpty()) {
			// the block came from one step back along its movement
			Direction movement = extending ? facing : facing.getOpposite();
			WearCarry.startMove(entity, pos.relative(movement.getOpposite()), before);
		}
		return entity;
	}
}
