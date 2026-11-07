package xyz.toesinthesand.ashfall.salvage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A small cloth bag: {@link SatchelMenu#SLOTS} slots carried in the inventory, saved on the stack's
 * {@code minecraft:container} component (so the vanilla container tooltip lists what is inside). The big
 * bags are Traveler's Backpacks, which the pack makes loot-only; the satchel is the one a player can sew.
 */
public class SatchelItem extends Item {
	public SatchelItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			int slot = heldSlot(player.getInventory(), hand);
			ItemStack stack = player.getItemInHand(hand);
			serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SatchelMenu(id, inventory, slot),
					stack.getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}

	/** The inventory index of the stack in {@code hand}. */
	static int heldSlot(Inventory inventory, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND;
	}

	/** A bag inside a bag would let a player carry any amount; nothing that stores items goes in. */
	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.ashfall.satchel.tooltip", SatchelMenu.SLOTS)
				.withStyle(ChatFormatting.GRAY));
	}
}
