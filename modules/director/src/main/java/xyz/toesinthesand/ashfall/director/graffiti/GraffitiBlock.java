package xyz.toesinthesand.ashfall.director.graffiti;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sprayed symbol on a wall: a paint-thin decal with no collision that breaks instantly and drops nothing. Only the
 * Spray Can places it ({@link Graffiti#spray}). It is not in {@code #ashfall:fortified} and zombies ignore it.
 * <p>
 * Cleared marks tick randomly to catch up with their building while their chunk was unloaded (the Director updates
 * loaded ones the moment the building's state changes).
 */
public class GraffitiBlock extends Block {
	/** The way the paint faces: away from the wall it is on. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<GraffitiSymbol> SYMBOL = EnumProperty.create("symbol", GraffitiSymbol.class);
	public static final EnumProperty<MarkState> STATE = EnumProperty.create("state", MarkState.class);
	/** Shapes against the wall on the side opposite {@link #FACING}. */
	private static final VoxelShape NORTH = Block.box(0, 0, 15, 16, 16, 16);
	private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 1);
	private static final VoxelShape EAST = Block.box(0, 0, 0, 1, 16, 16);
	private static final VoxelShape WEST = Block.box(15, 0, 0, 16, 16, 16);

	public GraffitiBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SYMBOL, GraffitiSymbol.CLEARED)
				.setValue(STATE, MarkState.FRESH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SYMBOL, STATE);
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
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(SYMBOL) == GraffitiSymbol.CLEARED;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		Graffiti.refresh(level, pos, state);
	}

	/** Forgets the mark once the paint is gone (broken, wall removed); spraying over it keeps the block. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!level.getBlockState(pos).is(this)) {
			Graffiti.removed(level, pos);
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
