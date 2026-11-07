package xyz.toesinthesand.ashfall.director.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;

/**
 * A wind-up noise lure: a placeable {@code BottleLure}. Right-click to wind it; it rattles for 30 s, calling every
 * zombie within 32 blocks, then rings once and needs 10 s before it can be wound again. Not fortified, so a zombie
 * that reaches it can tear it down.
 */
public class RattleBoxBlock extends BaseEntityBlock {
	/** True while the spring runs (the key on top turns red in the model). */
	public static final BooleanProperty WOUND = BooleanProperty.create("wound");
	/** A squat tin box with a winding key on top. */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 9, 13), Block.box(7, 9, 7, 9, 12, 9));

	public RattleBoxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(WOUND, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WOUND);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof RattleBoxBlockEntity box) {
			if (box.wind(server)) {
				player.sendOverlayMessage(Component.translatable("message.ashfall.rattle_box.wound", RattleBoxBlockEntity.RUN_TICKS / 20));
			} else if (box.running()) {
				player.sendOverlayMessage(Component.translatable("message.ashfall.rattle_box.running"));
			} else {
				player.sendOverlayMessage(Component.translatable("message.ashfall.rattle_box.cooldown", (box.cooldown() + 19) / 20));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RattleBoxBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DirectorBlocks.RATTLE_BOX_ENTITY, RattleBoxBlockEntity::serverTick);
	}
}
