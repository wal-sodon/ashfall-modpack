package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;

import java.util.function.Consumer;

/**
 * A blaster attachment item (Optic, Dampener, Heat Sink, Cell Regulator, Overcharger). Its attachment id
 * is its item path; what it does is datapack data ({@link AttachmentStats}). The item itself only
 * explains itself: what it does, where it is fitted, and which district must be Reclaimed first.
 */
public class AttachmentItem extends Item {
	public static final String TOOLTIP_EFFECT = "tooltip.ashfall.attachment.";
	public static final String TOOLTIP_FIT = "tooltip.ashfall.attachment.fit";
	public static final String TOOLTIP_SLOT = "tooltip.ashfall.attachment.slot";
	public static final String TOOLTIP_REQUIRES = "tooltip.ashfall.attachment.requires";

	public AttachmentItem(Properties properties) {
		super(properties);
	}

	/** The attachment id an item fits as, or null if it is not an attachment. */
	public static String attachmentId(ItemStack stack) {
		return stack.getItem() instanceof AttachmentItem ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() : null;
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		String id = attachmentId(stack);
		tooltip.accept(Component.translatable(TOOLTIP_EFFECT + id).withStyle(ChatFormatting.GRAY));
		// Tooltips are drawn client side; the client copy of the stats arrives with the datapack sync.
		AttachmentStats stats = AttachmentStatsManager.get(true, id);
		if (stats != null) {
			tooltip.accept(Component.translatable(TOOLTIP_SLOT, Component.translatable("tooltip.ashfall.attachment.slot." + stats.slot()))
					.withStyle(ChatFormatting.DARK_GRAY));
			stats.requiresDistrict().ifPresent(district -> tooltip.accept(
					Component.translatable(TOOLTIP_REQUIRES, GridDistricts.name(district)).withStyle(ChatFormatting.GOLD)));
		}
		tooltip.accept(Component.translatable(TOOLTIP_FIT).withStyle(ChatFormatting.DARK_GRAY));
	}
}
