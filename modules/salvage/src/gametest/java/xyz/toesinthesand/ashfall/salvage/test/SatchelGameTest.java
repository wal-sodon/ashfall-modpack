package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;
import xyz.toesinthesand.ashfall.salvage.item.SatchelMenu;

/** The satchel keeps what is put in it on the stack, refuses bags, and cannot be moved while open. */
public class SatchelGameTest {
	/** Menu index of hotbar slot 0: the satchel's nine slots, then the 27 main inventory slots. */
	private static final int HOTBAR_0 = SatchelMenu.SLOTS + 27;

	@GameTest
	public void satchelStoresAndLocks(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().setSelectedSlot(0);
		ItemStack satchel = new ItemStack(SalvageItems.SATCHEL);
		player.getInventory().setItem(0, satchel);

		SatchelMenu menu = new SatchelMenu(1, player.getInventory(), 0);
		menu.setCarried(new ItemStack(SalvageItems.CLOTH, 5));
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().isEmpty(), "the cloth was not put in the satchel");
		helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(SalvageItems.SATCHEL)), "a satchel fits inside a satchel");

		menu.clicked(HOTBAR_0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().getItem(0) == satchel,
				"the open satchel could be picked up");
		menu.clicked(0, 0, ContainerInput.SWAP, player);
		helper.assertTrue(player.getInventory().getItem(0) == satchel, "the open satchel could be swapped away");
		menu.removed(player);

		long stored = satchel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
				.filter(s -> s.is(SalvageItems.CLOTH)).mapToLong(ItemStack::getCount).sum();
		helper.assertTrue(stored == 5, "the satchel holds " + stored + " cloth, expected 5");

		SatchelMenu reopened = new SatchelMenu(2, player.getInventory(), 0);
		helper.assertTrue(reopened.getSlot(0).getItem().getCount() == 5, "the cloth was not there on reopening");
		player.getInventory().setItem(0, ItemStack.EMPTY);
		helper.assertFalse(reopened.stillValid(player), "the menu stays open after the satchel left the hand");
		helper.succeed();
	}
}
