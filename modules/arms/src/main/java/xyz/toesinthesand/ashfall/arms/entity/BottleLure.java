package xyz.toesinthesand.ashfall.arms.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

/**
 * A bottle full of scrap, thrown to make noise somewhere else: it shatters loudly where it lands and pulls zombies
 * there, away from the thrower. No damage, all distraction.
 */
public class BottleLure extends ThrowableItemProjectile {
	public static final float NOISE_RADIUS = 32f;

	public BottleLure(EntityType<? extends BottleLure> type, Level level) {
		super(type, level);
	}

	public BottleLure(Level level, LivingEntity owner, ItemStack stack) {
		super(ArmsEntities.BOTTLE_LURE, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ArmsItems.BOTTLE_LURE;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel level) {
			shatter(level, BlockPos.containing(result.getLocation()));
			discard();
		}
	}

	/** The crash and the noise at {@code pos}. Public for tests. */
	public static void shatter(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.9f);
		level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.5f, 0.8f);
		level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.2);
		ArmsNoise.emit(level, pos, NOISE_RADIUS);
	}
}
