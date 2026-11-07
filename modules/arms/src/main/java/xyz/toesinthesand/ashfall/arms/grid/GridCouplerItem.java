package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The Grid Coupler. Socketing one into a Substation Core consumes it (Repair). Touching a key Power Relay with
 * one costs nothing (Link): the district already paid its couplers to repair, and linking is a walk across the
 * district under the hum, not a second resource sink. The use itself lives on the blocks
 * ({@link SubstationCoreBlock}, the Power Relay's {@code ChargerBlock}), because they decide what a use means.
 */
public class GridCouplerItem extends Item {
	public GridCouplerItem(Properties properties) {
		super(properties);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out,
			TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.ashfall.grid_coupler").withStyle(ChatFormatting.GRAY));
	}
}
