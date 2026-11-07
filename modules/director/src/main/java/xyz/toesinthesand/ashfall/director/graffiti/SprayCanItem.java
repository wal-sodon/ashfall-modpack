package xyz.toesinthesand.ashfall.director.graffiti;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;

import java.util.function.Consumer;

/**
 * A can of paint for Graffiti marks, {@link #USES} sprays. Use on the side of a block sprays the can's symbol on it;
 * sneak + use (on anything, or in the air) switches to the next symbol, shown on the action bar and in the tooltip.
 * Why sneak + use rather than scrolling: it needs no client code or key, and it works the same with a controller mod.
 */
public class SprayCanItem extends Item {
	public static final int USES = 32;
	/** The symbol the can sprays; absent means {@link GraffitiSymbol#CLEARED}. */
	public static final DataComponentType<GraffitiSymbol> SYMBOL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "spray_symbol"), DataComponentType.<GraffitiSymbol>builder()
					.persistent(GraffitiSymbol.CODEC).networkSynchronized(GraffitiSymbol.STREAM_CODEC).build());

	public SprayCanItem(Properties properties) {
		super(properties);
	}

	public static GraffitiSymbol symbol(ItemStack stack) {
		return stack.getOrDefault(SYMBOL, GraffitiSymbol.CLEARED);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSecondaryUseActive()) {
			cycle(level, player, stack);
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.ashfall.spray_can.symbol", Component.translatable(symbol(stack).translationKey())));
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (player != null && player.isSecondaryUseActive()) {
			cycle(context.getLevel(), player, stack);
			return InteractionResult.SUCCESS;
		}
		if (!context.getClickedFace().getAxis().isHorizontal() && !context.getLevel().getBlockState(context.getClickedPos()).is(DirectorBlocks.GRAFFITI)) {
			if (player != null && !context.getLevel().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.ashfall.spray_can.wall_only"));
			}
			return InteractionResult.FAIL;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (player == null || !Graffiti.spray(level, context.getClickedPos(), context.getClickedFace(), symbol(stack), player)) {
			return InteractionResult.FAIL;
		}
		stack.hurtAndBreak(1, player, context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		return InteractionResult.SUCCESS;
	}

	private static void cycle(Level level, Player player, ItemStack stack) {
		if (level.isClientSide()) {
			return;
		}
		GraffitiSymbol next = symbol(stack).next();
		stack.set(SYMBOL, next);
		player.sendOverlayMessage(Component.translatable("message.ashfall.spray_can.symbol", Component.translatable(next.translationKey())));
	}

	@Override
	@SuppressWarnings("deprecation") // still the only per-item tooltip hook short of a custom data component
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
								Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("tooltip.ashfall.spray_can.symbol", Component.translatable(symbol(stack).translationKey()))
				.withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable("tooltip.ashfall.spray_can").withStyle(ChatFormatting.GRAY));
	}
}
