package xyz.toesinthesand.ashfall.salvage.bike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A found bicycle. Ridden like a horse without a saddle: use it to get on, steer with the mouse, W to pedal, S to
 * back up slowly. Faster than sprinting and silent (no footsteps, no noise stimulus), it climbs kerbs and single
 * steps but cannot jump, and it crawls in water. Sneak-use (or a punch) picks it up as an item.
 * <p>
 * It is never crafted: bikes only stand where the city left them (see {@code StreetProps}).
 */
public class Bicycle extends Mob {
	public static final double SPEED = 0.25; // ~11 blocks/s, about twice a sprint (0.3 felt too fast)
	/** Pedalling through water is slow. */
	public static final float WATER_FACTOR = 0.3f;

	public Bicycle(EntityType<? extends Bicycle> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 10.0)
				.add(Attributes.MOVEMENT_SPEED, SPEED)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		Vec2 rotation = new Vec2(player.getXRot() * 0.5f, player.getYRot());
		setRot(rotation.y, rotation.x);
		yRotO = yBodyRot = yHeadRot = getYRot();
	}

	/** Forward only (a bike doesn't strafe); backing up is a quarter speed. */
	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 input) {
		float forward = player.zza;
		return new Vec3(0, 0, forward <= 0 ? forward * 0.25f : forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		float speed = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
		return isInWater() ? speed * WATER_FACTOR : speed;
	}

	@Override
	protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
		return getPassengers().isEmpty() && passenger instanceof Player;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive()) {
			if (isVehicle()) {
				return InteractionResult.PASS;
			}
			if (level() instanceof ServerLevel) {
				pickUp(player);
			}
			return InteractionResult.SUCCESS;
		}
		if (!isVehicle() && level() instanceof ServerLevel) {
			player.setYRot(getYRot());
			player.setXRot(getXRot());
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	/** Back into the player's inventory (or at their feet). */
	public void pickUp(Player player) {
		ItemStack stack = new ItemStack(Bikes.ITEM);
		if (!player.addItem(stack)) {
			spawnAtLocation((ServerLevel) level(), stack);
		}
		level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.6f, 1.2f);
		discard();
	}

	/** A punch from a player knocks it into an item, like a boat; nothing else harms it except falling out of the world. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player player && !isVehicle()) {
			pickUp(player);
			return true;
		}
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Bikes.ITEM);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
