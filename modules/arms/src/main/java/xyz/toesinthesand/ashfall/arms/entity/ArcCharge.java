package xyz.toesinthesand.ashfall.arms.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.director.senses.Stun;

/**
 * A stun grenade built from a battery and scrap electronics. Thrown like a {@link BottleLure}, it sticks where it hits
 * (block or mob), crackles for {@link #FUSE_TICKS}, then discharges: every non-friendly mob within
 * {@link #STUN_RADIUS} blocks that the burst can see (walls block it) gets {@link Stun#tryStun}, so the usual
 * durations, lockout and boss immunity apply. No damage and no knockback, but the bang is heard
 * {@link #NOISE_RADIUS} blocks away, so whatever it doesn't hold comes running.
 */
public class ArcCharge extends ThrowableItemProjectile {
	public static final int FUSE_TICKS = 20;
	public static final double STUN_RADIUS = 5.0;
	public static final float NOISE_RADIUS = 32f;
	/** How far off the struck face the charge rests, so the burst's sight lines don't start inside the block. */
	private static final double REST_OFFSET = 0.2;
	/** Pale blue, the stun effect's colour. */
	private static final int FLASH_COLOR = 0xFF000000 | Stun.COLOR;

	/** Synced so the client stops simulating the throw and crackles while the fuse burns. */
	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(ArcCharge.class, EntityDataSerializers.BOOLEAN);

	/** Ticks left on the fuse; only meaningful once armed. */
	private int fuse = FUSE_TICKS;

	public ArcCharge(EntityType<? extends ArcCharge> type, Level level) {
		super(type, level);
	}

	public ArcCharge(Level level, LivingEntity owner, ItemStack stack) {
		super(ArmsEntities.ARC_CHARGE, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ArmsItems.ARC_CHARGE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ARMED, false);
	}

	public boolean isArmed() {
		return entityData.get(ARMED);
	}

	/** Stops the charge at {@code at} and lights the fuse. Public for tests. */
	public void arm(Vec3 at) {
		setPos(at);
		setDeltaMovement(Vec3.ZERO);
		setNoGravity(true);
		fuse = FUSE_TICKS;
		entityData.set(ARMED, true);
	}

	@Override
	protected void onHit(HitResult result) {
		if (isArmed()) {
			return;
		}
		super.onHit(result);
		if (!level().isClientSide()) {
			Vec3 at = result.getLocation();
			if (result instanceof BlockHitResult block) {
				at = at.add(block.getDirection().getUnitVec3().scale(REST_OFFSET));
			}
			arm(at);
			level().playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 1.0f, 1.6f);
		}
	}

	@Override
	public void tick() {
		if (!isArmed()) {
			super.tick();
			return;
		}
		// Stuck: no movement or collision, just the fuse.
		baseTick();
		Vec3 centre = centre();
		if (level().isClientSide()) {
			if (tickCount % 3 == 0) {
				level().addParticle(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z,
						(random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
			}
			return;
		}
		if (fuse % 5 == 0) {
			level().playSound(null, centre.x, centre.y, centre.z, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.PLAYERS, 0.6f, 1.8f);
		}
		if (--fuse <= 0 && level() instanceof ServerLevel level) {
			burst(level, centre, getOwner() instanceof Player player ? player : null);
			discard();
		}
	}

	private Vec3 centre() {
		return position().add(0, getBbHeight() / 2, 0);
	}

	/**
	 * The discharge at {@code at}: sparks, the bang, a {@link #NOISE_RADIUS} noise and a stun on every mob in range and
	 * in sight. Returns how many mobs it stunned. Public for tests.
	 *
	 * @param owner the thrower, if still known; only passed on as the stun's source
	 */
	public static int burst(ServerLevel level, Vec3 at, @Nullable Player owner) {
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, FLASH_COLOR), at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 60, 1.2, 0.8, 1.2, 0.6);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 1.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.7f);
		ArmsNoise.emit(level, BlockPos.containing(at), NOISE_RADIUS);

		int stunned = 0;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(STUN_RADIUS),
				e -> e.getBoundingBox().distanceToSqr(at) <= STUN_RADIUS * STUN_RADIUS && !Friendly.isFriendly(e, owner))) {
			if (inSight(level, at, target) && Stun.tryStun(target, owner) > 0) {
				stunned++;
			}
		}
		return stunned;
	}

	/** Whether a straight line from the burst reaches the target's eyes or middle without passing through a block. */
	private static boolean inSight(ServerLevel level, Vec3 at, LivingEntity target) {
		for (Vec3 point : new Vec3[] {target.getEyePosition(), target.getBoundingBox().getCenter()}) {
			if (level.clip(new ClipContext(at, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
					.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (isArmed()) {
			output.putInt("fuse", fuse);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		int saved = input.getIntOr("fuse", -1);
		if (saved >= 0) {
			arm(position());
			fuse = saved;
		}
	}
}
