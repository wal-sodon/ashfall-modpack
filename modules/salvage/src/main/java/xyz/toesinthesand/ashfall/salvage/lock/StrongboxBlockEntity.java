package xyz.toesinthesand.ashfall.salvage.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.IntStream;

/**
 * The Strongbox's two rows of storage and the building it belongs to. A {@link RandomizableContainerBlockEntity}, so
 * its loot table unpacks on first open like any chest and salvage's loot code ({@code RandomizableContainer},
 * {@code LOOT_STATE}) treats it as a normal container. It gets no {@code LOOT_STATE} when placed, so it never refills.
 * <p>
 * Locked, it refuses players ({@link #canOpen}) and offers hoppers no slots, so nothing gets the loot out but the key or
 * the crowbar.
 */
public class StrongboxBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
	public static final int SIZE = 18;
	private static final int[] SLOTS = IntStream.range(0, SIZE).toArray();
	private static final int[] NO_SLOTS = new int[0];

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** The building whose key opens this box; 0 for one placed by a player. */
	private long building;

	public StrongboxBlockEntity(BlockPos pos, BlockState state) {
		super(Locks.STRONGBOX_ENTITY, pos, state);
	}

	public long building() {
		return building;
	}

	public void setBuilding(long building) {
		this.building = building;
		setChanged();
	}

	public boolean locked() {
		BlockState state = getBlockState();
		return state.hasProperty(StrongboxBlock.LOCKED) && state.getValue(StrongboxBlock.LOCKED);
	}

	@Override
	public boolean canOpen(Player player) {
		return !locked() && super.canOpen(player);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.ashfall.strongbox");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x2, id, inventory, this, 2);
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return locked() ? NO_SLOTS : SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return !locked();
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return !locked();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
		building = input.getLongOr("building", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items);
		}
		output.putLong("building", building);
	}
}
