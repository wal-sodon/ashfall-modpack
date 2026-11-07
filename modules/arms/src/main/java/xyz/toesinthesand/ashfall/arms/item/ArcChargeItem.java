package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.entity.ArcCharge;

import java.util.function.Consumer;

/** Thrown like a Bottle Lure, a little shorter: it sticks where it lands and goes off a second later. */
public class ArcChargeItem extends Item {
	public static final float THROW_POWER = 1.1f;
	public static final int COOLDOWN_TICKS = 10;

	public ArcChargeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
				0.4f, 0.6f);
		if (level instanceof ServerLevel server) {
			Projectile.spawnProjectileFromRotation(ArcCharge::new, server, stack, player, 0.0f, THROW_POWER, 1.0f);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.ashfall.arc_charge").withStyle(ChatFormatting.GRAY));
	}
}
