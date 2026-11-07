package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import xyz.toesinthesand.ashfall.arms.grid.GridBlocks;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

/**
 * The Uplink Socket on the Wreck's deck, where the city is plugged into the Heart. Like the Substation Core it is used
 * by hand, because every step is "put one in": the Grid Uplink starts the Black Start, Energy Cells fill its buffer,
 * Grid Couplers patch the Uplink, and an empty hand reads the run (or, after a replay reset left the Uplink here
 * in standby, powers it up again). Unbreakable and immovable: it is the finale.
 * <p>
 * No block entity: the run lives in {@link FinaleState} (or a gametest's own instance), so it keeps going while the
 * hull's chunk is unloaded and survives the block being replaced by an admin.
 */
public class UplinkSocketBlock extends Block {
	/** 0 empty, 1 Uplink seated but not drawing (paused), 2 running or complete: drives light and model. */
	public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 2);

	public UplinkSocketBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWER, 0));
	}

	public static int light(int power) {
		return switch (power) {
			case 1 -> 6;
			case 2 -> 15;
			default -> 2;
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		boolean uplink = stack.is(FinaleBlocks.GRID_UPLINK);
		boolean cell = stack.is(ArmsItems.ENERGY_CELL);
		boolean coupler = stack.is(GridBlocks.GRID_COUPLER);
		if (!uplink && !cell && !coupler) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (uplink) {
				FinaleActions.seat(server, pos, sp, stack);
			} else if (cell) {
				FinaleActions.feedCell(server, pos, sp, stack);
			} else {
				FinaleActions.patch(server, pos, sp, stack);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			FinaleRun run = FinaleController.forSocket(server, pos).run();
			if (run.status == FinaleRun.Status.IDLE && run.standby == pos.asLong()) {
				FinaleActions.restart(server, pos, sp);
			} else {
				FinaleActions.status(server, pos, sp);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
