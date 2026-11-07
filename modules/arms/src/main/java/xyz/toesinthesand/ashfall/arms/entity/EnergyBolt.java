package xyz.toesinthesand.ashfall.arms.entity;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.gun.ArmsParticles;
import xyz.toesinthesand.ashfall.arms.gun.ArmsSounds;
import xyz.toesinthesand.ashfall.arms.gun.BoltStyle;
import xyz.toesinthesand.ashfall.arms.gun.GunStats;
import xyz.toesinthesand.ashfall.arms.gun.Headshots;
import xyz.toesinthesand.ashfall.director.senses.Stun;

/**
 * A server-side energy bolt: flies straight (no gravity, no drag) at the speed it was fired with, deals
 * its damage once, and fizzles after {@link #maxDistance} blocks or in water.
 * <p>
 * Built on {@link AbstractHurtingProjectile} (the fireball/wind charge base) for its swept hit test and
 * straight-line movement; acceleration is zeroed and inertia is 1 so speed stays constant. Unlike a
 * fireball it can't be punched back, never sets fire and never breaks blocks.
 * <p>
 * Colour and {@link BoltStyle} are synchronised, for the renderer and the effects. Everything visual
 * on the client (muzzle flash, trail sparks) goes through {@link #clientEffects}, installed by the
 * client entrypoint; the server only sends one impact emitter particle per hit. Bolts are not saved:
 * they live a second or two, and a reloaded half-flown bolt would only be a surprise.
 * <p>
 * A stun bolt ({@link #setStun}) hurts nothing: it stuns the mob it hits ({@link Stun#tryStun}) and makes no sound.
 * It handles its own hits instead of going through {@link #onHit}'s super call, because vanilla's emits a
 * {@code PROJECTILE_LAND} game event (a Director noise) and pokes the block it lands on (bells ring).
 */
public class EnergyBolt extends AbstractHurtingProjectile {
	public static final ResourceKey<DamageType> DAMAGE_TYPE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("ashfall", "energy_bolt"));
	/** Hard cap on lifetime, in case a bolt is somehow stopped without hitting anything. */
	public static final int MAX_AGE_TICKS = 200;
	/**
	 * The bolt's position is the bottom of its 0.25-block box, but it is drawn (and aimed) through the
	 * box's centre: impacts are lifted by this much along the hit surface to land where the beam was.
	 */
	public static final double VISUAL_CENTRE_LIFT = 0.125;
	/** A spawn packet this close to the owner's eyes is a fresh shot (muzzle flash); further, a bolt entering view mid-flight. */
	public static final double FRESH_SHOT_RADIUS = 3.0;

	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(EnergyBolt.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_STYLE = SynchedEntityData.defineId(EnergyBolt.class, EntityDataSerializers.BYTE);

	/** Client visuals hook; a no-op until the client entrypoint installs one (and forever on a dedicated server). */
	public static ClientEffects clientEffects = ClientEffects.NONE;

	private float damage;
	private float headshotMultiplier = GunStats.DEFAULT_HEADSHOT_MULTIPLIER;
	private float maxDistance = GunStats.DEFAULT_RANGE;
	private double travelled;
	private boolean lastHitHeadshot;
	private boolean stun;

	// Client-only visual state, filled from the spawn packet and the client effects.
	private @Nullable Vec3 visualOrigin;
	private Vec3 muzzleOffset = Vec3.ZERO;
	private boolean freshShot;

	public EnergyBolt(EntityType<? extends EnergyBolt> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0;
	}

	/** A bolt ready to be shot by {@code shooter}, positioned at its eyes. */
	public static EnergyBolt create(Level level, LivingEntity shooter, float damage, float headshotMultiplier, float range, int color) {
		return create(level, shooter, new Vec3(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ()),
				damage, headshotMultiplier, range, color);
	}

	/** A bolt at {@code pos}, credited to {@code owner} (may be null, e.g. for a turret or a test). */
	public static EnergyBolt create(Level level, Entity owner, Vec3 pos, float damage, float headshotMultiplier, float range, int color) {
		EnergyBolt bolt = new EnergyBolt(ArmsEntities.ENERGY_BOLT, level);
		if (owner != null) {
			bolt.setOwner(owner);
		}
		bolt.setPos(pos.x, pos.y, pos.z);
		bolt.damage = damage;
		bolt.headshotMultiplier = headshotMultiplier;
		bolt.maxDistance = range;
		bolt.setColor(color);
		return bolt;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_COLOR, 0xFFFFFF);
		builder.define(DATA_STYLE, (byte) BoltStyle.CARBINE.ordinal());
	}

	public int getColor() {
		return entityData.get(DATA_COLOR);
	}

	public void setColor(int rgb) {
		entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
	}

	public BoltStyle getStyle() {
		return BoltStyle.byId(entityData.get(DATA_STYLE));
	}

	public void setStyle(BoltStyle style) {
		entityData.set(DATA_STYLE, (byte) style.ordinal());
	}

	public float getDamage() {
		return damage;
	}

	/** Whether this is a silent stun bolt (server side only; the client just sees its pale colour). */
	public boolean isStun() {
		return stun;
	}

	public void setStun(boolean stun) {
		this.stun = stun;
	}

	/** Whether this bolt's entity hit (if any) was a headshot; for tests and future hit markers. */
	public boolean lastHitWasHeadshot() {
		return lastHitHeadshot;
	}

	/** Client: where the bolt was when this client first saw it, or null if unknown. */
	public @Nullable Vec3 visualOrigin() {
		return visualOrigin;
	}

	/** Client: whether this client saw the bolt leave the gun (so a muzzle flash and a muzzle offset apply). */
	public boolean isFreshShot() {
		return freshShot;
	}

	/** Client: offset from the bolt's true line to the gun's muzzle, faded out over the first blocks of flight. */
	public Vec3 muzzleOffset() {
		return muzzleOffset;
	}

	public void setMuzzleOffset(Vec3 offset) {
		this.muzzleOffset = offset;
	}

	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket packet) {
		super.recreateFromPacket(packet);
		visualOrigin = position();
		Entity owner = getOwner();
		freshShot = owner != null && owner.getEyePosition().distanceToSqr(visualOrigin) < FRESH_SHOT_RADIUS * FRESH_SHOT_RADIUS;
	}

	@Override
	public void tick() {
		Vec3 before = position();
		boolean first = tickCount == 0;
		super.tick();
		Level level = level();
		if (level.isClientSide()) {
			if (first) {
				clientEffects.onFirstTick(this);
			}
			if (!isRemoved()) {
				clientEffects.onTick(this, before, position());
			}
			return;
		}
		if (isRemoved()) {
			return;
		}
		travelled += position().distanceTo(before);
		if (travelled > maxDistance || tickCount > MAX_AGE_TICKS) {
			discard();
		} else if (isInWater()) {
			if (stun) {
				discard(); // stun bolts go out without a hiss
			} else {
				fizzle((ServerLevel) level);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Entity target = result.getEntity();
		float amount = damage;
		// The hit location is on the bolt's swept bottom edge; judge the head by the bolt's centre.
		double hitY = result.getLocation().y + getBbHeight() / 2.0;
		lastHitHeadshot = target instanceof LivingEntity
				&& Headshots.isHeadshot(hitY, target.getEyeY(), target.getBbHeight());
		if (lastHitHeadshot) {
			amount *= headshotMultiplier;
			level.sendParticles(ParticleTypes.CRIT, result.getLocation().x, result.getLocation().y, result.getLocation().z,
					8, 0.1, 0.1, 0.1, 0.3);
		}
		if (target instanceof LivingEntity living) {
			// Every bolt is its own hit, so automatic blasters deal their stated DPS. In 26.x the window that
			// swallows repeat damage is LivingEntity#damageCooldownTime (set to 20 on each hit; while it is above
			// 10, a hit only lands if it beats lastHurt, and then only the difference). Entity#invulnerableTime is
			// a different timer that hurtServer never reads, so clearing that one did nothing.
			living.damageCooldownTime = 0;
		}
		DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), this, getOwner());
		target.hurtServer(level, source, amount);
	}

	@Override
	protected void onHit(HitResult result) {
		if (stun) {
			stunHit(result);
			return;
		}
		super.onHit(result);
		if (level() instanceof ServerLevel level) {
			if (result instanceof BlockHitResult blockHit) {
				impact(level, result.getLocation(), blockHit.getDirection().getUnitVec3(), false);
			} else {
				impact(level, result.getLocation(), impactNormal(getDeltaMovement()), true);
			}
			discard();
		}
	}

	/**
	 * A stun bolt's hit: stuns a living target, shows the impact flash, and nothing else. No damage (so no anger,
	 * knockback or hurt sound), no impact sound, no game event.
	 */
	private void stunHit(HitResult result) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 normal;
		boolean entity = result instanceof EntityHitResult;
		if (result instanceof EntityHitResult entityHit) {
			if (entityHit.getEntity() instanceof LivingEntity living) {
				Stun.tryStun(living, getOwner() instanceof Player player ? player : null);
			}
			normal = impactNormal(getDeltaMovement());
		} else if (result instanceof BlockHitResult blockHit) {
			normal = blockHit.getDirection().getUnitVec3();
		} else {
			return;
		}
		Vec3 at = visualImpactPoint(result.getLocation(), normal);
		level.sendParticles(ColorParticleOption.create(entity ? ArmsParticles.BOLT_HIT : ArmsParticles.BOLT_IMPACT, 0xFF000000 | getColor()),
				at.x, at.y, at.z, 0, normal.x, normal.y, normal.z, getStyle().impactScale() * 0.6);
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		// Deliberately nothing else: bolts never break or ignite blocks.
	}

	/**
	 * Where an impact is drawn: the hit point lifted by {@link #VISUAL_CENTRE_LIFT} along the surface
	 * (the up vector with its component along the normal removed), so a wall hit lands at beam height and
	 * a floor hit stays on the floor.
	 */
	public static Vec3 visualImpactPoint(Vec3 hit, Vec3 normal) {
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 along = up.subtract(normal.scale(up.dot(normal)));
		return hit.add(along.scale(VISUAL_CENTRE_LIFT));
	}

	/** The normal used for an entity hit: back along the bolt's flight (or straight up if it isn't moving). */
	public static Vec3 impactNormal(Vec3 velocity) {
		return velocity.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : velocity.normalize().scale(-1);
	}

	/** One emitter particle (direction = surface normal x impact size) plus the impact or hit sound. */
	private void impact(ServerLevel level, Vec3 hit, Vec3 normal, boolean entity) {
		Vec3 at = visualImpactPoint(hit, normal);
		ColorParticleOption emitter = ColorParticleOption.create(entity ? ArmsParticles.BOLT_HIT : ArmsParticles.BOLT_IMPACT,
				0xFF000000 | getColor());
		level.sendParticles(emitter, at.x, at.y, at.z, 0, normal.x, normal.y, normal.z, getStyle().impactScale());
		level.playSound(null, at.x, at.y, at.z, entity ? ArmsSounds.BOLT_HIT : ArmsSounds.BOLT_IMPACT, SoundSource.PLAYERS,
				entity ? 0.8f : 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
	}

	private void fizzle(ServerLevel level) {
		level.sendParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, getX(), getY() + 0.2, getZ(), 3, 0.05, 0.05, 0.05, 0.02);
		level.playSound(null, getX(), getY(), getZ(), ArmsSounds.BOLT_IMPACT, SoundSource.PLAYERS, 0.4f, 0.6f);
		discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false; // can't be punched back like a ghast fireball
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected ParticleOptions getTrailParticle() {
		return null; // trail sparks come from the client effects
	}

	@Override
	protected float getInertia() {
		return 1.0f;
	}

	@Override
	protected float getLiquidInertia() {
		return 1.0f;
	}

	@Override
	public float getLightLevelDependentMagicValue() {
		return 1.0f;
	}

	/** Client-side visuals for bolts. Implemented in the client source set; never called on a server. */
	public interface ClientEffects {
		ClientEffects NONE = new ClientEffects() {
			@Override
			public void onFirstTick(EnergyBolt bolt) {
			}

			@Override
			public void onTick(EnergyBolt bolt, Vec3 from, Vec3 to) {
			}
		};

		/** The bolt's first client tick (before it has been drawn moving): muzzle flash and muzzle offset. */
		void onFirstTick(EnergyBolt bolt);

		/** Every client tick the bolt survives, with the segment it just flew: trail sparks. */
		void onTick(EnergyBolt bolt, Vec3 from, Vec3 to);
	}
}
