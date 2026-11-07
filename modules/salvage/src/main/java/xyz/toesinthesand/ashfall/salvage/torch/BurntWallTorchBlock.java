package xyz.toesinthesand.ashfall.salvage.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wall torch that has burnt out. Extends the wall torch for its facing, shape and support rules only: the flame is
 * gone (no {@link #animateTick} particles) and so is the light (the block properties give none).
 */
public class BurntWallTorchBlock extends WallTorchBlock {
	public BurntWallTorchBlock(Properties properties) {
		super(ParticleTypes.SMOKE, properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hit) {
		return TorchBurnout.useItemOn(stack, state, level, pos, player, hand);
	}
}
