package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The Uplink and its parts. They do nothing in the hand; the tooltip ({@code tooltip.ashfall.<id>}) says where each
 * goes, because the recipe chain is three crafts deep and a player holding a Capacitor Bank should know what it is for.
 */
public class FinaleItem extends Item {
	public FinaleItem(Properties properties) {
		super(properties);
	}

	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out,
			TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.ashfall." + BuiltInRegistries.ITEM.getKey(this).getPath())
				.withStyle(ChatFormatting.GRAY));
	}
}
