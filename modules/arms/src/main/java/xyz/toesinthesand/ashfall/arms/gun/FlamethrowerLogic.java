package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.director.senses.LightRules;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/**
 * Server-side flamethrower behaviour: the fuel tank, the flame cone, and refuelling with Lighter Fluid. Kept apart
 * from {@link FlamethrowerItem} so gametests can drive it with a mock player and a chosen use tick.
 * <ul>
 *   <li><b>Fuel:</b> one tick of fire costs one tick of fuel ({@link ArmsComponents#FUEL}); the tank holds
 *   {@link #TANK_TICKS}. The reload key pours in one Lighter Fluid, {@link #TICKS_PER_USE} per remaining use.</li>
 *   <li><b>Cone:</b> every {@link #HIT_INTERVAL} ticks, each mob within {@link #RANGE} blocks and
 *   {@link #HALF_ANGLE_DEGREES} of the aim, with no wall in between, takes {@link #DAMAGE} as the player's attack
 *   and burns for {@link #BURN_TICKS} ({@link FlamerBurn}). The damage type bypasses hurt cooldowns, so that is a
 *   steady 5 damage a second plus burn across a whole crowd. Players, pets and survivors are skipped.</li>
 *   <li><b>Blocks:</b> never set on fire; unlit campfires and candles in the cone light, as with Lighter Fluid.
 *   Torches are left alone: relighting a burnt-out torch is a deliberate act with a fire starter
 *   ({@code TorchBurnout} in salvage), not something a sweep of flame should do to every torch in a corridor.</li>
 *   <li><b>Noise:</b> {@link #NOISE_RADIUS} every {@link #NOISE_INTERVAL} ticks, plus a Director LIGHT stimulus for
 *   a light of {@link #LIGHT_LEVEL} when it would be seen (night, or indoors).</li>
 * </ul>
 */
public final class FlamethrowerLogic {
	/** 16 s of fire. */
	public static final int TANK_TICKS = 320;
	/** Fuel per remaining Lighter Fluid use: a full 16-use can is 160 ticks, 8 s. */
	public static final int TICKS_PER_USE = 10;
	/** Found flamethrowers come half full (loot tables set the same number). */
	public static final int FOUND_FUEL = TANK_TICKS / 2;
	/** Draw time, as the Storm Repeater's; see {@link DrawTime}. */
	public static final int DRAW_TICKS = 18;
	public static final double RANGE = 6.0;
	public static final double HALF_ANGLE_DEGREES = 25.0;
	public static final int HIT_INTERVAL = 4;
	public static final float DAMAGE = 1.0f;
	public static final int BURN_TICKS = 80;
	public static final int NOISE_INTERVAL = 10;
	public static final float NOISE_RADIUS = 40f;
	public static final int LIGHT_LEVEL = 15;
	/** Pouring a can in takes a moment; the flamethrower can't fire meanwhile. */
	public static final int REFUEL_TICKS = 20;
	/** Pause after a dry click so a held trigger doesn't click every tick. */
	public static final int EMPTY_CLICK_TICKS = 8;
	private static final double COS_HALF_ANGLE = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));

	public static final ResourceKey<DamageType> DAMAGE_TYPE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("ashfall", "flamethrower"));

	/** What one tick of a held trigger did. */
	public enum FireResult {
		/** Only players fire powered weapons; see {@link GunLogic#canFire}. */
		NOT_A_PLAYER,
		FIRED, COOLDOWN, EMPTY;

		/** Whether the held trigger should be released (anything but a flame or a not-yet-ready tick). */
		public boolean stopsFiring() {
			return this != FIRED && this != COOLDOWN;
		}
	}

	private FlamethrowerLogic() {
	}

	public static int fuel(ItemStack stack) {
		return stack.getOrDefault(ArmsComponents.FUEL, 0);
	}

	/** A flamethrower holding {@code fuel} ticks (clamped to the tank). */
	public static ItemStack withFuel(int fuel) {
		ItemStack stack = new ItemStack(ArmsItems.FLAMETHROWER);
		stack.set(ArmsComponents.FUEL, Mth.clamp(fuel, 0, TANK_TICKS));
		return stack;
	}

	/**
	 * One tick of a held trigger. {@code useTicks} counts from 0 on the tick the trigger went down, and decides when
	 * the cone hits and when the noise goes out, so a fresh pull always hits and is always heard at once.
	 */
	public static FireResult tick(ServerLevel level, LivingEntity shooter, ItemStack stack, int useTicks) {
		if (!GunLogic.canFire(shooter) || !(shooter instanceof Player player)) {
			return FireResult.NOT_A_PLAYER;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return FireResult.COOLDOWN;
		}
		boolean free = player.hasInfiniteMaterials();
		int fuel = fuel(stack);
		if (fuel <= 0 && !free) {
			dryClick(level, player, stack);
			return FireResult.EMPTY;
		}
		if (!free) {
			stack.set(ArmsComponents.FUEL, fuel - 1);
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		spray(level, eye, look);
		if (useTicks % 8 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
					0.6f, 0.55f + level.getRandom().nextFloat() * 0.1f);
		}
		if (useTicks % HIT_INTERVAL == 0) {
			burnCone(level, player, eye, look);
			lightBlocks(level, player, eye, look);
		}
		if (useTicks % NOISE_INTERVAL == 0) {
			ArmsNoise.emit(level, player.blockPosition(), NOISE_RADIUS);
			double light = LightRules.radius(LIGHT_LEVEL, level.isDarkOutside(), !level.canSeeSky(BlockPos.containing(eye)));
			if (light > 0.0) {
				Stimuli.emit(level, eye, StimulusType.LIGHT, light, player);
			}
		}
		return FireResult.FIRED;
	}

	/** Whether {@code point} lies within range and the cone's half-angle of a flame from {@code eye} along {@code look}. */
	public static boolean inCone(Vec3 eye, Vec3 look, Vec3 point) {
		Vec3 to = point.subtract(eye);
		double distance = to.length();
		if (distance > RANGE) {
			return false;
		}
		// Point blank counts as in front: a mob pressed against the nozzle is still in the flame.
		return distance < 0.5 || to.dot(look) / distance >= COS_HALF_ANGLE;
	}

	/** Whether nothing solid stands between {@code eye} and {@code point}. */
	private static boolean clearPath(ServerLevel level, Player player, Vec3 eye, Vec3 point) {
		return level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
				== HitResult.Type.MISS;
	}

	/** Hurts and ignites every mob the flame reaches; returns how many it hit. */
	public static int burnCone(ServerLevel level, Player player, Vec3 eye, Vec3 look) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), player);
		AABB box = new AABB(eye, eye).inflate(RANGE);
		int hit = 0;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
			// A dying mob is left alone: Mutant Monsters' Mutant Zombie loses its revive when ignited mid-death.
			if (target.isDeadOrDying() || Friendly.isFriendly(target, player)) {
				continue;
			}
			Vec3 centre = target.getBoundingBox().getCenter();
			Vec3 head = target.getEyePosition();
			boolean reached = (inCone(eye, look, centre) && clearPath(level, player, eye, centre))
					|| (inCone(eye, look, head) && clearPath(level, player, eye, head));
			if (!reached) {
				continue;
			}
			target.hurtServer(level, source, DAMAGE);
			if (target.isAlive() && !target.isDeadOrDying()) {
				FlamerBurn.ignite(target, BURN_TICKS);
			}
			hit++;
		}
		return hit;
	}

	/** Lights unlit campfires and candles the flame reaches (the blocks Lighter Fluid lights); returns how many. */
	public static int lightBlocks(ServerLevel level, Player player, Vec3 eye, Vec3 look) {
		int reach = Mth.ceil(RANGE);
		BlockPos origin = BlockPos.containing(eye);
		int lit = 0;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-reach, -reach, -reach), origin.offset(reach, reach, reach))) {
			BlockState state = level.getBlockState(pos);
			if (!(CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state))) {
				continue;
			}
			Vec3 centre = Vec3.atCenterOf(pos);
			if (!inCone(eye, look, centre)) {
				continue;
			}
			BlockHitResult sight = level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (sight.getType() != HitResult.Type.MISS && !sight.getBlockPos().equals(pos)) {
				continue;
			}
			BlockPos at = pos.immutable();
			level.setBlock(at, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, at);
			lit++;
		}
		return lit;
	}

	/** Flame and smoke along the cone, for everyone watching. */
	private static void spray(ServerLevel level, Vec3 eye, Vec3 look) {
		Vec3 nozzle = eye.add(0, -0.25, 0).add(look.scale(0.8));
		double spread = Math.tan(Math.toRadians(HALF_ANGLE_DEGREES)) * 0.5;
		for (int i = 1; i <= 5; i++) {
			double along = RANGE * i / 5.5;
			Vec3 p = nozzle.add(look.scale(along));
			double width = along * spread;
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, width, width, width, 0.02);
			if (i >= 3) {
				level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.2, p.z, 1, width, width, width, 0.01);
			}
		}
	}

	private static void dryClick(ServerLevel level, Player player, ItemStack stack) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ArmsSounds.GUN_EMPTY, SoundSource.PLAYERS, 0.6f, 0.6f);
		player.getCooldowns().addCooldown(stack, EMPTY_CLICK_TICKS);
	}

	/** The flamethrower the player would refuel: the main hand's only, like {@link GunLogic#heldGun}. */
	public static ItemStack heldFlamethrower(Player player) {
		ItemStack stack = player.getMainHandItem();
		return stack.getItem() instanceof FlamethrowerItem ? stack : ItemStack.EMPTY;
	}

	/**
	 * Pours one can of Lighter Fluid into the tank: the most-used can first, {@link #TICKS_PER_USE} per remaining
	 * use. A can that doesn't fit pours only what fits and keeps the rest; an emptied can is used up. A tank with no
	 * room for one use refuses. Returns the fuel added (0 when nothing was poured).
	 */
	public static int refuel(Player player, ItemStack flamer) {
		if (!(flamer.getItem() instanceof FlamethrowerItem)) {
			return 0;
		}
		int fuel = fuel(flamer);
		int roomUses = (TANK_TICKS - fuel) / TICKS_PER_USE;
		Inventory inventory = player.getInventory();
		int canSlot = -1;
		int mostDamage = -1;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack candidate = inventory.getItem(slot);
			if (candidate.is(SalvageItems.LIGHTER_FLUID) && candidate.getDamageValue() > mostDamage) {
				mostDamage = candidate.getDamageValue();
				canSlot = slot;
			}
		}
		if (roomUses <= 0 || canSlot < 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ArmsSounds.GUN_EMPTY,
					SoundSource.PLAYERS, 0.5f, 1.3f);
			return 0;
		}
		ItemStack can = inventory.getItem(canSlot);
		int left = can.getMaxDamage() - can.getDamageValue();
		int poured = Math.min(left, roomUses);
		if (poured >= left) {
			can.shrink(1);
		} else {
			can.setDamageValue(can.getDamageValue() + poured);
		}
		inventory.setChanged();
		if (player.isUsingItem() && player.getUseItem() == flamer) {
			player.stopUsingItem();
		}
		flamer.set(ArmsComponents.FUEL, fuel + poured * TICKS_PER_USE);
		player.getCooldowns().addCooldown(flamer, REFUEL_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY,
				SoundSource.PLAYERS, 0.8f, 0.8f);
		return poured * TICKS_PER_USE;
	}
}
