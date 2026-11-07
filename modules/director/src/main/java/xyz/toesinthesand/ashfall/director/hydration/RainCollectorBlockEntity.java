package xyz.toesinthesand.ashfall.director.hydration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The collector's bottles: {@link #empty} waiting for rain and {@link #filled} with clean water, {@link #CAPACITY} in
 * all. While rain falls on the block, one bottle fills every {@link #FILL_TICKS}.
 */
public class RainCollectorBlockEntity extends BlockEntity {
	public static final int CAPACITY = 4;
	/** One bottle per minute of rain. */
	public static final int FILL_TICKS = 60 * 20;
	private static final int UPDATE_TICKS = 20;

	private int empty;
	private int filled;
	private int progress;

	public RainCollectorBlockEntity(BlockPos pos, BlockState state) {
		super(RainCollectors.ENTITY, pos, state);
	}

	public int empty() {
		return empty;
	}

	public int filled() {
		return filled;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RainCollectorBlockEntity collector) {
		if (level instanceof ServerLevel server && (server.getGameTime() + pos.hashCode()) % UPDATE_TICKS == 0) {
			collector.collect(server.isRainingAt(pos.above()), UPDATE_TICKS);
		}
	}

	/** Rain for {@code ticks} more ticks (or none). @return whether a bottle filled */
	public boolean collect(boolean raining, int ticks) {
		if (!raining || empty <= 0) {
			return false;
		}
		progress += ticks;
		if (progress < FILL_TICKS) {
			setChanged();
			return false;
		}
		progress = 0;
		empty--;
		filled++;
		changed();
		return true;
	}

	/** Puts one empty bottle in. @return false when full */
	public boolean addBottle() {
		if (empty + filled >= CAPACITY) {
			return false;
		}
		empty++;
		changed();
		return true;
	}

	/** Takes out every filled bottle, or the empty ones when nothing is filled yet. */
	public ItemStack takeOut() {
		ItemStack out;
		if (filled > 0) {
			out = RainCollectorBlock.water(filled);
			filled = 0;
		} else if (empty > 0) {
			out = new ItemStack(Items.GLASS_BOTTLE, empty);
			empty = 0;
			progress = 0;
		} else {
			return ItemStack.EMPTY;
		}
		changed();
		return out;
	}

	private void changed() {
		setChanged();
		if (level != null) {
			BlockState state = getBlockState();
			if (state.hasProperty(RainCollectorBlock.FILLED) && state.getValue(RainCollectorBlock.FILLED) != (filled > 0)) {
				level.setBlock(worldPosition, state.setValue(RainCollectorBlock.FILLED, filled > 0), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null) {
			if (filled > 0) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), RainCollectorBlock.water(filled));
			}
			if (empty > 0) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.GLASS_BOTTLE, empty));
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		empty = input.getIntOr("empty", 0);
		filled = input.getIntOr("filled", 0);
		progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("empty", empty);
		output.putInt("filled", filled);
		output.putInt("progress", progress);
	}
}
