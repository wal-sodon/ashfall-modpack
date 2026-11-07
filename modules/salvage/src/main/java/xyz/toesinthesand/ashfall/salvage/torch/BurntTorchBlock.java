package xyz.toesinthesand.ashfall.salvage.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A standing torch that has burnt out: no light, no flame. A fire starter lights it again ({@link TorchBurnout}). */
public class BurntTorchBlock extends BaseTorchBlock {
	public BurntTorchBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hit) {
		return TorchBurnout.useItemOn(stack, state, level, pos, player, hand);
	}
}
