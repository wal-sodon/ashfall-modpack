package xyz.toesinthesand.ashfall.director.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * An Ashfall armour piece: a plain armour item plus a tooltip line naming its set's perk, since the perk
 * (quieter steps, infection resistance) is otherwise invisible. The line comes from the piece's set tag.
 */
public class ArmorPerkItem extends Item {
	public ArmorPerkItem(Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		String set = stack.is(DirectorArmor.SCAVENGER_SET) ? "scavenger"
				: stack.is(DirectorArmor.HAZMAT_SET) ? "hazmat"
				: stack.is(DirectorArmor.RIOT_SET) ? "riot" : null;
		if (set != null) {
			tooltip.accept(Component.translatable("tooltip.ashfall.armor." + set).withStyle(ChatFormatting.GOLD));
		}
	}
}
