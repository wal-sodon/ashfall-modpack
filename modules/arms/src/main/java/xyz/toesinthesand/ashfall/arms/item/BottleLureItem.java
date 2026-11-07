package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.entity.BottleLure;

/** Thrown far, quietly: the only noise is where it lands. */
public class BottleLureItem extends Item {
	public static final float THROW_POWER = 1.3f;
	public static final int COOLDOWN_TICKS = 10;

	public BottleLureItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
				0.3f, 0.5f);
		if (level instanceof ServerLevel server) {
			Projectile.spawnProjectileFromRotation(BottleLure::new, server, stack, player, 0.0f, THROW_POWER, 1.0f);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
