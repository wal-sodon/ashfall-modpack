package xyz.toesinthesand.ashfall.salvage.lock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/**
 * The key one zombie of a safe building carries ({@link KeyData}): used on that building's locked Strongbox it opens it
 * without a sound zombies can hear and is used up. Any other box just says the key doesn't fit.
 */
public class BuildingKeyItem extends Item {
	public BuildingKeyItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!context.getLevel().getBlockState(context.getClickedPos()).is(Locks.STRONGBOX)) {
			return InteractionResult.PASS;
		}
		if (context.getLevel() instanceof ServerLevel level && context.getPlayer() != null) {
			Strongboxes.unlockWithKey(level, context.getClickedPos(), context.getPlayer(), context.getItemInHand());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
								TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		KeyData key = stack.get(Locks.KEY_DATA);
		tooltip.accept(key == null
				? Component.translatable("item.ashfall.building_key.blank").withStyle(ChatFormatting.GRAY)
				: Component.translatable("item.ashfall.building_key.for", key.name()).withStyle(ChatFormatting.GRAY));
	}
}
