package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import team.reborn.energy.api.base.SimpleEnergyItem;

import java.util.function.Consumer;

/**
 * A rechargeable Cell Pack: gun ammunition as a Team Reborn Energy item, so any TR-compatible charger
 * (ours or another mod's) can refill it. Each weapon class takes its own pack size (see
 * {@link xyz.toesinthesand.ashfall.arms.gun.GunClass}); a newly crafted pack is empty.
 * <p>
 * Input and output are uncapped per transfer: the chargers apply their own per-tick rates.
 */
public class CellPackItem extends Item implements SimpleEnergyItem {
	private final long capacity;

	public CellPackItem(Properties properties, long capacity) {
		super(properties);
		this.capacity = capacity;
	}

	/** Capacity without a stack: loot modifiers run before item components are bound, so no ItemStack yet. */
	public long capacity() {
		return capacity;
	}

	@Override
	public long getEnergyCapacity(ItemStack stack) {
		return capacity;
	}

	@Override
	public long getEnergyMaxInput(ItemStack stack) {
		return capacity;
	}

	@Override
	public long getEnergyMaxOutput(ItemStack stack) {
		return capacity;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return EnergyItems.barWidth(getStoredEnergy(stack), capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyItems.CHARGE_BAR_COLOR;
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(EnergyItems.energyLine(getStoredEnergy(stack), capacity));
	}
}
