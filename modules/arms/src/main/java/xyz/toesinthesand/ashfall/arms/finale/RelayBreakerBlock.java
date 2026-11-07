package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A Relay Breaker, raised on the ridge when the Feedback phase begins. Tripped, it glows red and needs a player
 * standing at it (within the settings' radius) to force it closed; the hold is counted by {@link FinaleController},
 * so the block itself is only a marker that shows its state. Unbreakable, so nobody "closes" one with a pickaxe.
 */
public class RelayBreakerBlock extends Block {
	public static final BooleanProperty TRIPPED = BlockStateProperties.TRIGGERED;

	public RelayBreakerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(TRIPPED, true));
	}

	public static int light(BlockState state) {
		return state.getValue(TRIPPED) ? 7 : 12;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TRIPPED);
	}
}
