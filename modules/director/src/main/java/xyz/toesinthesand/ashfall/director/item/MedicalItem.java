package xyz.toesinthesand.ashfall.director.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A treatment (Bandage, Antibiotics). The item is a plain vanilla consumable (its {@code consumable}
 * component gives the use time, animation and sound); what it does happens in the consume hook
 * ({@code Infection.onConsume}), which is where every consumable already passes. The class only adds a
 * tooltip line saying what the treatment is for, since the effect is invisible until it matters.
 */
public class MedicalItem extends Item {
	private final String tooltipKey;

	public MedicalItem(Properties properties, String tooltipKey) {
		super(properties);
		this.tooltipKey = tooltipKey;
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
	}
}
