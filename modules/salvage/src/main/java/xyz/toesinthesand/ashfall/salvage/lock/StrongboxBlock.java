package xyz.toesinthesand.ashfall.salvage.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiConsumer;

/**
 * A steel safe with better loot than the building's other containers. One is placed, locked, in some better buildings
 * when their population first spawns ({@link Strongboxes#tryPlace}); a placed or crafted-from-creative one starts
 * unlocked. Locked, it won't open by hand, can't be mined (destroy progress 0), shrugs off explosions and keeps
 * hoppers out ({@link StrongboxBlockEntity}). The matching Building Key opens it quietly; a Crowbar forces it loudly.
 * Either way it stays unlocked for good.
 */
public class StrongboxBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

	public StrongboxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOCKED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LOCKED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	/**
	 * Keys and pry tools go on to their own {@code useOn} (which knows what to do with a locked box); everything else
	 * falls through to {@link #useWithoutItem}, so a locked box rattles and an open one opens whatever is in hand.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hit) {
		if (state.getValue(LOCKED) && (stack.getItem() instanceof BuildingKeyItem || stack.is(Locks.PRY_TOOLS))) {
			return InteractionResult.PASS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel) || !(level.getBlockEntity(pos) instanceof StrongboxBlockEntity box)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(LOCKED)) {
			level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 0.8f);
			player.sendOverlayMessage(Component.translatable("message.ashfall.strongbox.locked"));
			return InteractionResult.SUCCESS;
		}
		player.openMenu(box);
		return InteractionResult.CONSUME;
	}

	/** No progress at all while locked; creative players still break it (they ignore progress). */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return state.getValue(LOCKED) ? 0.0f : super.getDestroyProgress(state, player, level, pos);
	}

	/** A locked box survives any blast: TNT is not a way around the key. */
	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
								  BiConsumer<ItemStack, BlockPos> onHit) {
		if (!state.getValue(LOCKED)) {
			super.onExplosionHit(state, level, pos, explosion, onHit);
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StrongboxBlockEntity(pos, state);
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
