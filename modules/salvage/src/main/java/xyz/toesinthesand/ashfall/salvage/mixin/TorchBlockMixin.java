package xyz.toesinthesand.ashfall.salvage.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import xyz.toesinthesand.ashfall.salvage.torch.TorchBurnout;

/**
 * Gives the torch the three hooks torch burnout needs ({@link TorchBurnout}): {@code setPlacedBy}, which only block
 * items call, starts the timer when a player placed it, the scheduled {@code tick} burns it out, and
 * {@code affectNeighborsAfterRemoval} drops the timer when the torch goes. Vanilla's torch overrides none (all are
 * no-ops in {@code Block} or {@code BlockBehaviour}), so they are added here rather than injected; the wall torch
 * inherits them. {@link TorchBurnout} checks the block, so soul and copper torches (also {@code TorchBlock}s) and the
 * burnt-out wall torch pass straight through.
 */
@Mixin(TorchBlock.class)
public abstract class TorchBlockMixin extends BaseTorchBlock {
	protected TorchBlockMixin(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		TorchBurnout.onPlaced(level, pos, state, placer);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		TorchBurnout.onTick(state, level, pos);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		TorchBurnout.onRemoved(state, level, pos);
	}
}
