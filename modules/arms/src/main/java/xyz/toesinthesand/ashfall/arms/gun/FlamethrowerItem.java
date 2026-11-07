package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The flamethrower: hold right-click to spray a short cone of fire, fed from a Lighter Fluid tank. The behaviour is in
 * {@link FlamethrowerLogic}; this class is the use loop (like an automatic blaster's), the draw time and the item bar.
 * It takes no Cell Pack and has no datapack stats: it is not one of {@link xyz.toesinthesand.ashfall.arms.item.ArmsItems#GUNS}.
 */
public class FlamethrowerItem extends Item {
	public static final String TOOLTIP_FUEL = "tooltip.ashfall.flamethrower.fuel";
	public static final String TOOLTIP_INFO = "tooltip.ashfall.flamethrower";
	public static final String TOOLTIP_REFUEL = "tooltip.ashfall.flamethrower.refuel";
	/** Burning orange. */
	public static final int FUEL_BAR_COLOR = 0xFF8A2A;

	public FlamethrowerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Main hand only, like the guns: the off hand would skip the draw time and the weight.
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (FlamethrowerLogic.fuel(stack) <= 0 && !player.hasInfiniteMaterials()) {
			if (level instanceof ServerLevel server) {
				FlamethrowerLogic.tick(server, player, stack, 0); // the dry click
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		int useTicks = getUseDuration(stack, entity) - remainingTicks;
		if (FlamethrowerLogic.tick(server, entity, stack, useTicks).stopsFiring()) {
			entity.stopUsingItem();
		}
	}

	/** Drawing it into the main hand takes {@link FlamethrowerLogic#DRAW_TICKS}; see {@link DrawTime}. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND && owner instanceof ServerPlayer player && !player.isSpectator()) {
			DrawTime.onMainHandTick(player, stack, FlamethrowerLogic.DRAW_TICKS);
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return APPROXIMATELY_INFINITE_USE_DURATION;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	/** Burning fuel rewrites the tank every tick; never bob the held flamethrower for that. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	/** The bar is the tank, so fuel is visible from the hotbar. */
	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13f * FlamethrowerLogic.fuel(stack) / FlamethrowerLogic.TANK_TICKS);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return FUEL_BAR_COLOR;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable(TOOLTIP_INFO).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable(TOOLTIP_FUEL, seconds(FlamethrowerLogic.fuel(stack)), seconds(FlamethrowerLogic.TANK_TICKS))
				.withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable(TOOLTIP_REFUEL, Component.keybind("key.ashfall.reload"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Fuel ticks as seconds, "12.5" or "16". */
	public static String seconds(int ticks) {
		return EnergyGunItem.number(ticks / 20f);
	}
}
