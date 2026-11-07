package xyz.toesinthesand.ashfall.salvage.item;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Satchel menu: one row of {@link #SLOTS} slots in the vanilla chest layout, then the player inventory.
 * <p>
 * The contents live on the satchel stack itself; every change writes them back to its {@code container}
 * component. Why the held slot is locked: if the open satchel could be picked up, dropped or swapped away,
 * the menu would keep writing into a stack the player no longer has, which duplicates items. So that slot
 * ignores clicks and number-key swaps, and the menu closes if the stack in it is no longer this satchel.
 */
public class SatchelMenu extends AbstractContainerMenu {
	public static final int SLOTS = 9;
	public static final int SLOT_X = 8, SLOT_Y = 18, INVENTORY_X = 8, INVENTORY_Y = 49;
	private static final int PLAYER_START = SLOTS;
	private static final int PLAYER_END = PLAYER_START + 36;

	/** Items that may not go in a satchel beyond those that refuse container items (Traveler's Backpacks). */
	public static final TagKey<Item> BLOCKED = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ashfall", "satchel_blocked"));

	public static final MenuType<SatchelMenu> MENU = Registry.register(BuiltInRegistries.MENU,
			Identifier.fromNamespaceAndPath("ashfall", "satchel"), new MenuType<>(SatchelMenu::new, FeatureFlags.VANILLA_SET));

	private final Inventory inventory;
	/** Inventory index of the open satchel (hotbar slot or offhand). */
	private final int heldSlot;
	/** The open satchel on the server; empty on the client, which only mirrors the slots. */
	private final ItemStack satchel;
	private final SimpleContainer container;

	/** Client side: guesses the held slot the same way the server picked it, for click prediction. */
	public SatchelMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, inventory.getSelectedItem().getItem() instanceof SatchelItem
				? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND, false);
	}

	public SatchelMenu(int containerId, Inventory inventory, int heldSlot) {
		this(containerId, inventory, heldSlot, true);
	}

	private SatchelMenu(int containerId, Inventory inventory, int heldSlot, boolean server) {
		super(MENU, containerId);
		this.inventory = inventory;
		this.heldSlot = heldSlot;
		this.satchel = server ? inventory.getItem(heldSlot) : ItemStack.EMPTY;
		this.container = new SimpleContainer(SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				save();
			}
		};
		satchel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(container.getItems());
		for (int i = 0; i < SLOTS; i++) {
			addSlot(new SatchelSlot(container, i, SLOT_X + i * 18, SLOT_Y));
		}
		addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	private void save() {
		if (!satchel.isEmpty()) {
			satchel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(container.getItems()));
		}
	}

	/** Whether {@code stack} may be put in a satchel. */
	public static boolean fits(ItemStack stack) {
		return stack.getItem().canFitInsideContainerItems() && !stack.is(BLOCKED);
	}

	private boolean isHeld(Slot slot) {
		return slot.container == inventory && slot.getContainerSlot() == heldSlot;
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
		if (slotIndex >= 0 && slotIndex < slots.size() && isHeld(slots.get(slotIndex))) {
			return;
		}
		if (input == ContainerInput.SWAP && button == heldSlot) {
			return;
		}
		super.clicked(slotIndex, button, input, player);
	}

	@Override
	public boolean stillValid(Player player) {
		return satchel.isEmpty() || (player.isAlive() && inventory.getItem(heldSlot) == satchel);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		save();
	}

	/** Shift-click: out of the satchel into the inventory, or from the inventory into the satchel. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem() || isHeld(slot)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < PLAYER_START) {
			if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	/** A satchel slot: anything that may be carried in a container item. */
	static final class SatchelSlot extends Slot {
		SatchelSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return fits(stack);
		}
	}
}
