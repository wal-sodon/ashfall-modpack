package xyz.toesinthesand.ashfall.arms.charger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import xyz.toesinthesand.ashfall.arms.grid.GridActions;
import xyz.toesinthesand.ashfall.arms.grid.GridBlocks;

/**
 * Field Charger and Power Relay. Behaves like a furnace: faces the player who placed it, opens its
 * menu on use, and shows an {@code active} variant while something is charging so players can tell at
 * a glance (and the art can glow) without opening it.
 */
public class ChargerBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	private final ChargerKind kind;

	public ChargerBlock(ChargerKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
	}

	public ChargerKind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/**
	 * A Grid Coupler on a Power Relay links it to its district's grid (the Link step) instead of opening the menu, and a
	 * Wire Spool repairs a worn relay of a reclaimed district ({@link xyz.toesinthesand.ashfall.arms.grid.GridDecay}).
	 * Handled here because the block's use runs before the item's, and the menu would otherwise swallow it.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		boolean repair = stack.is(xyz.toesinthesand.ashfall.arms.item.ArmsItems.WIRE_SPOOL);
		if (kind != ChargerKind.POWER_RELAY || !(repair || stack.is(GridBlocks.GRID_COUPLER))) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			if (repair) {
				GridActions.repairRelay(server, pos, sp, stack);
			} else {
				GridActions.link(server, pos, sp);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
			player.openMenu(charger);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChargerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ArmsBlocks.CHARGER_ENTITY, ChargerBlockEntity::serverTick);
	}
}
