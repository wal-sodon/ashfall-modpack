package xyz.toesinthesand.ashfall.arms.charger;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;

/**
 * Charger menu: one row of five slots in the vanilla hopper layout (four charge slots, then the cell
 * slot), the player inventory below, and two synced values (buffer energy, status).
 * <p>
 * There is one menu type per charger kind so the client knows which items the slots accept (a Field
 * Charger refuses drained Energy Cells) without an extended opening packet.
 */
public class ChargerMenu extends AbstractContainerMenu {
	public static final int SLOT_X = 44, SLOT_Y = 20, INVENTORY_X = 8, INVENTORY_Y = 51;
	private static final int PLAYER_START = ChargerKind.SLOTS;
	private static final int PLAYER_END = PLAYER_START + 36;

	private final Container container;
	private final ContainerData data;
	private final ChargerKind kind;

	/** Client side: an empty mirror filled by the server's slot and data updates. */
	public ChargerMenu(int containerId, Inventory inventory, ChargerKind kind) {
		this(containerId, inventory, new SimpleContainer(ChargerKind.SLOTS), new SimpleContainerData(ChargerBlockEntity.DATA_COUNT), kind);
	}

	public ChargerMenu(int containerId, Inventory inventory, Container container, ContainerData data, ChargerKind kind) {
		super(ArmsBlocks.menuType(kind), containerId);
		checkContainerSize(container, ChargerKind.SLOTS);
		checkContainerDataCount(data, ChargerBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		this.kind = kind;
		container.startOpen(inventory.player);
		for (int i = 0; i < ChargerKind.SLOTS; i++) {
			addSlot(new ChargerSlot(container, i, SLOT_X + i * 18, SLOT_Y, kind));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
		addDataSlots(data);
	}

	public ChargerKind kind() {
		return kind;
	}

	public int energy() {
		return data.get(ChargerBlockEntity.DATA_ENERGY);
	}

	public int status() {
		return data.get(ChargerBlockEntity.DATA_STATUS);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	/**
	 * Shift-click: out of the charger into the inventory; into the charger, a charged source goes to an
	 * empty cell slot and anything else to the charge slots.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < PLAYER_START) {
			if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			EnergyStorage energy = ChargerBlockEntity.energyOf(stack);
			boolean fuel = energy != null && energy.supportsExtraction() && energy.getAmount() > 0;
			boolean moved = fuel && moveItemStackTo(stack, ChargerKind.CELL_SLOT, ChargerKind.SLOTS, false);
			if (!moved && !moveItemStackTo(stack, 0, ChargerKind.CHARGE_SLOTS, false)
					&& !moveItemStackTo(stack, ChargerKind.CELL_SLOT, ChargerKind.SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	/** A charger slot: energy items only, one at a time. */
	static final class ChargerSlot extends Slot {
		private final ChargerKind kind;

		ChargerSlot(Container container, int index, int x, int y, ChargerKind kind) {
			super(container, index, x, y);
			this.kind = kind;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return getContainerSlot() == ChargerKind.CELL_SLOT ? ChargerBlockEntity.canFuel(stack) : ChargerBlockEntity.canCharge(kind, stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
