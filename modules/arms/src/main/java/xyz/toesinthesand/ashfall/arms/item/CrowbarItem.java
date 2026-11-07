package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.salvage.lock.Strongboxes;

import java.util.function.Consumer;

/**
 * A short iron bar: a weak melee weapon, and the loud way into a locked Strongbox. Hold use on the box for
 * {@link #FORCE_TICKS}; looking away or letting go starts over. When it gives, the lock is broken for good, the bar
 * loses {@link #FORCE_DURABILITY} and zombies within {@link #FORCE_NOISE} blocks hear it, where the building's key
 * would have opened it without a sound.
 */
public class CrowbarItem extends Item {
	/** Four seconds of prying. */
	public static final int FORCE_TICKS = 4 * 20;
	/** Prying a lock is loud: twice a Spark shot, louder than a Bottle Lure (32), so it pulls the block in. */
	public static final float FORCE_NOISE = 48.0f;
	/** About 8 boxes per bar (250 durability): keys stay the cheap, quiet way in. */
	public static final int FORCE_DURABILITY = 31;
	private static final int SCRAPE_INTERVAL = 10;

	public CrowbarItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !Strongboxes.isLocked(context.getLevel().getBlockState(context.getClickedPos()))) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(context.getHand());
		return InteractionResult.CONSUME;
	}

	/** Stops the moment the player looks off the box; scrapes now and then so the effort is heard (by players only). */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		BlockPos target = target(level, entity);
		if (target == null) {
			entity.stopUsingItem();
			return;
		}
		if (!level.isClientSide() && (FORCE_TICKS - remainingTicks) % SCRAPE_INTERVAL == 0) {
			level.playSound(null, target, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8f, 0.6f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		BlockPos target = target(level, entity);
		if (target != null && level instanceof ServerLevel server) {
			force(server, entity, stack, target);
		}
		return stack;
	}

	/**
	 * The end of a full hold: breaks the lock at {@code pos}, makes the noise and wears the bar.
	 *
	 * @return false if there was no locked Strongbox there
	 */
	public static boolean force(ServerLevel level, LivingEntity user, ItemStack stack, BlockPos pos) {
		if (!Strongboxes.force(level, pos)) {
			return false;
		}
		ArmsNoise.emit(level, pos, FORCE_NOISE);
		stack.hurtAndBreak(FORCE_DURABILITY, user, user.getUsedItemHand());
		return true;
	}

	/** The locked Strongbox the player is looking at, or null. */
	private static BlockPos target(Level level, LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return null;
		}
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		return hit.getType() == HitResult.Type.BLOCK && Strongboxes.isLocked(level.getBlockState(hit.getBlockPos()))
				? hit.getBlockPos() : null;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return FORCE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BRUSH;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable("tooltip.ashfall.crowbar", FORCE_TICKS / 20, (int) FORCE_NOISE).withStyle(ChatFormatting.GRAY));
	}
}
