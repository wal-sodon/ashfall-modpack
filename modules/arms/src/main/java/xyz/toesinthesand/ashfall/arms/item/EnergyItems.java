package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import team.reborn.energy.api.base.SimpleEnergyItem;

/**
 * Shared helpers for Team Reborn Energy items (Cell Packs, Energy Cells): the durability-style charge
 * bar, the tooltip line, and reading/writing energy on the item templates guns keep their packs in.
 */
public final class EnergyItems {
	/** Width of a vanilla item bar in pixels. */
	public static final int BAR_WIDTH = 13;
	public static final int CHARGE_BAR_COLOR = 0x3CC8FF;
	public static final String TOOLTIP_ENERGY = "tooltip.ashfall.energy";

	private EnergyItems() {
	}

	public static int barWidth(long stored, long capacity) {
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.round(BAR_WIDTH * Math.min(1.0, (double) stored / capacity));
	}

	public static Component energyLine(long stored, long capacity) {
		return Component.translatable(TOOLTIP_ENERGY, stored, capacity).withStyle(ChatFormatting.AQUA);
	}

	/** A copy of {@code stack} holding {@code energy} units. */
	public static ItemStack withEnergy(ItemStack stack, long energy) {
		ItemStack copy = stack.copy();
		SimpleEnergyItem.setStoredEnergyUnchecked(copy, energy);
		return copy;
	}

	/** Energy stored in a pack template (as kept in a gun's {@code loaded_pack}). */
	public static long stored(ItemStackTemplate template) {
		return SimpleEnergyItem.getStoredEnergyUnchecked(template.create());
	}

	/** The same pack template holding {@code energy} units. */
	public static ItemStackTemplate withEnergy(ItemStackTemplate template, long energy) {
		ItemStack stack = template.create();
		SimpleEnergyItem.setStoredEnergyUnchecked(stack, energy);
		return ItemStackTemplate.fromNonEmptyStack(stack);
	}

	/** Capacity of a pack template, or 0 if it is not an energy item. */
	public static long capacity(ItemStackTemplate template) {
		ItemStack stack = template.create();
		return stack.getItem() instanceof SimpleEnergyItem energy ? energy.getEnergyCapacity(stack) : 0L;
	}
}
