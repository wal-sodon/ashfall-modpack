package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.entity.Nail;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;

import java.util.function.Consumer;

/**
 * A pneumatic nail gun rebuilt as a weapon: one Nail per shot from the inventory, weak, and so quiet that only
 * zombies within {@link #NOISE_RADIUS} blocks hear it (a Spark pistol carries several times as far).
 */
public class NailLauncherItem extends Item {
	public static final float NOISE_RADIUS = 6f;
	public static final float SPEED = 2.6f;
	public static final int COOLDOWN_TICKS = 12;

	public NailLauncherItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack launcher = player.getItemInHand(hand);
		ItemStack nails = findNails(player);
		if (nails.isEmpty() && !player.getAbilities().instabuild) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4f, 1.6f);
			return InteractionResult.FAIL;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.35f, 1.8f);
		if (level instanceof ServerLevel server) {
			ItemStack shot = new ItemStack(ArmsItems.NAILS);
			Projectile.spawnProjectileFromRotation(Nail::new, server, shot, player, 0.0f, SPEED, 0.5f);
			ArmsNoise.emit(server, player.blockPosition(), NOISE_RADIUS);
			if (!player.getAbilities().instabuild) {
				nails.shrink(1);
			}
			launcher.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		player.getCooldowns().addCooldown(launcher, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	/** The first stack of Nails in the inventory, or empty. */
	static ItemStack findNails(Player player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(ArmsItems.NAILS)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.ashfall.nail_launcher").withStyle(ChatFormatting.GRAY));
	}
}
