package xyz.toesinthesand.ashfall.arms.entity;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

/**
 * A nail from the Nail Launcher: a weak, nearly silent shot. It hurts what it hits and is gone; it is the quiet
 * option for picking off one zombie without bringing the street, not a replacement for a blaster.
 */
public class Nail extends ThrowableItemProjectile {
	public static final float DAMAGE = 5.0f;

	public Nail(EntityType<? extends Nail> type, Level level) {
		super(type, level);
	}

	public Nail(Level level, LivingEntity owner, ItemStack stack) {
		super(ArmsEntities.NAIL, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ArmsItems.NAILS;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (level() instanceof ServerLevel level) {
			result.getEntity().hurtServer(level, damageSources().thrown(this, getOwner()), DAMAGE);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (level() instanceof ServerLevel level) {
			level.playSound(null, result.getBlockPos(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.3f, 1.6f);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 3, 0.05, 0.05, 0.05, 0.05);
		}
	}

	@Override
	protected void onHit(net.minecraft.world.phys.HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) {
			discard();
		}
	}
}
