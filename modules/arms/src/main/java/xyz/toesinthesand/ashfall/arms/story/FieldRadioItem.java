package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The Field Radio ({@code ashfall:field_radio}), found in the Montford safehouse cache. Used in the air it
 * reports the grid, the current objective and the way home ({@link RadioReport}); sneak-used it gives just the
 * objective and the way home; held against a Power Relay it tunes into that relay's Relay Log
 * (handled in {@link Story}, because the relay's own use action would otherwise open its charger menu first).
 * <p>
 * A short cooldown stops a held use button from flooding chat with reports.
 */
public class FieldRadioItem extends Item {
	public static final int COOLDOWN_TICKS = 40;

	public FieldRadioItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			RadioProgress.set(serverPlayer, RadioProgress.get(serverPlayer).withRadio());
			if (player.isShiftKeyDown()) {
				RadioReport.sendShort(serverPlayer);
			} else {
				RadioReport.send(serverPlayer);
			}
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4f, 0.6f);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.ashfall.field_radio").withStyle(ChatFormatting.GRAY));
	}
}
