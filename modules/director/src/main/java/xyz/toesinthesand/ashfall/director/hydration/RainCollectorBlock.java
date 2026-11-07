package xyz.toesinthesand.ashfall.director.hydration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/**
 * A scrap-metal basin that fills empty glass bottles with clean water while it rains on it. Use a glass bottle on it
 * to put one in, an empty hand to take the water out (or the empty bottles, when nothing is filled yet).
 */
public class RainCollectorBlock extends BaseEntityBlock {
	/** Shows water in the basin when at least one bottle is filled. */
	public static final BooleanProperty FILLED = BooleanProperty.create("filled");

	public RainCollectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FILLED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FILLED);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.GLASS_BOTTLE)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof RainCollectorBlockEntity collector) {
			if (collector.addBottle()) {
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.6f, 1.2f);
				player.sendOverlayMessage(Component.translatable("message.ashfall.rain_collector.status",
						collector.empty(), collector.filled(), RainCollectorBlockEntity.CAPACITY));
			} else {
				player.sendOverlayMessage(Component.translatable("message.ashfall.rain_collector.full"));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof RainCollectorBlockEntity collector) {
			ItemStack out = collector.takeOut();
			if (out.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.ashfall.rain_collector.status",
						collector.empty(), collector.filled(), RainCollectorBlockEntity.CAPACITY));
			} else {
				level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6f, 1.0f);
				if (!player.addItem(out)) {
					Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), out);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** The item a filled slot gives: clean bottled water. */
	static ItemStack water(int count) {
		return new ItemStack(SalvageItems.BOTTLED_WATER, count);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RainCollectorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, RainCollectors.ENTITY, RainCollectorBlockEntity::serverTick);
	}
}
