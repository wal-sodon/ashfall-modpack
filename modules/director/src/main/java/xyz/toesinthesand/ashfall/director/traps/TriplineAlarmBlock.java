package xyz.toesinthesand.ashfall.director.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;

/**
 * A bell-and-tripwire box hung on a wall, facing out. It watches a 5x3x5 box in front of it for zombies and, when
 * one walks in, rings for its owner and nearby players only (a client-side sound, so it makes no stimulus) and
 * gives a redstone signal while the zombie is there. The logic lives in {@link TriplineAlarmBlockEntity}.
 */
public class TriplineAlarmBlock extends BaseEntityBlock {
	/** The way the alarm faces: away from the wall it hangs on. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Shapes against the wall on the side opposite {@link #FACING}. */
	private static final VoxelShape NORTH = Block.box(4, 3, 12, 12, 13, 16);
	private static final VoxelShape SOUTH = Block.box(4, 3, 0, 12, 13, 4);
	private static final VoxelShape EAST = Block.box(0, 3, 4, 4, 13, 12);
	private static final VoxelShape WEST = Block.box(12, 3, 4, 16, 13, 12);

	public TriplineAlarmBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		for (Direction looking : context.getNearestLookingDirections()) {
			if (looking.getAxis().isHorizontal()) {
				BlockState facing = state.setValue(FACING, looking.getOpposite());
				if (facing.canSurvive(context.getLevel(), context.getClickedPos())) {
					return facing;
				}
			}
		}
		return null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return WallTorchBlock.canSurvive(level, pos, state.getValue(FACING));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TriplineAlarmBlockEntity alarm) {
			alarm.setOwner(player.getUUID());
		}
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	/** Strongly powers the wall it hangs on, like a lever. */
	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWERED) && direction == state.getValue(FACING) ? 15 : 0;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!movedByPiston && state.getValue(POWERED)) {
			updateNeighbours(state, level, pos);
		}
	}

	/** Tells the neighbours and the wall behind that the signal changed. */
	static void updateNeighbours(BlockState state, Level level, BlockPos pos) {
		Direction back = state.getValue(FACING).getOpposite();
		var orientation = ExperimentalRedstoneUtils.initialOrientation(level, back, Direction.UP);
		Block block = state.getBlock();
		level.updateNeighborsAt(pos, block, orientation);
		level.updateNeighborsAt(pos.relative(back), block, orientation);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TriplineAlarmBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DirectorBlocks.TRIPLINE_ALARM_ENTITY, TriplineAlarmBlockEntity::serverTick);
	}
}
