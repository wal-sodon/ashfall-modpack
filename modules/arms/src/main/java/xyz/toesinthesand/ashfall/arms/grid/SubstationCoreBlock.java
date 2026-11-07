package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

/**
 * The Substation Core: the heart of a district's loop. Right-click with a Grid Coupler to socket it, with an
 * Energy Cell to load it, empty-handed to read its status. Direct use rather than a screen, because every step
 * is "put one in" and the status fits in a chat line; it keeps the core usable mid-siege.
 */
public class SubstationCoreBlock extends BaseEntityBlock {
	/** 0 dark, 1 repaired, 2 energizing to siege, 3 reclaimed; drives the light level and the model. */
	public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 3);

	public SubstationCoreBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWER, 0));
	}

	/** Light per {@link #POWER} step: dark, a standby glow, a working hum, a lit district. */
	public static int light(int power) {
		return switch (power) {
			case 1 -> 4;
			case 2 -> 10;
			case 3 -> 15;
			default -> 0;
		};
	}

	/** The {@link #POWER} value that shows a district in {@code state}. */
	public static int power(DistrictState state, boolean energizing) {
		return switch (state) {
			case DARK, BROKEN, BLACKOUT -> 0;
			case REPAIRED -> energizing ? 2 : 1;
			case ENERGIZED, SIEGE -> 2;
			case RECLAIMED -> 3;
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		boolean coupler = stack.is(GridBlocks.GRID_COUPLER);
		boolean cell = stack.is(ArmsItems.ENERGY_CELL);
		if (!coupler && !cell) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (coupler) {
				GridActions.socket(server, pos, sp, stack);
			} else {
				GridActions.loadCell(server, pos, sp, stack);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			GridActions.status(server, pos, sp);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SubstationCoreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, GridBlocks.SUBSTATION_CORE_ENTITY, SubstationCoreBlockEntity::serverTick);
	}
}
