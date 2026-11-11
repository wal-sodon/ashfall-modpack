package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.Skills;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;

/**
 * Server-side gun behaviour: firing (heat, energy, bolt, sound, noise), reloading (pack swap) and the fire mode
 * switch. A stun-mode shot ({@link FireMode#STUN}) costs {@link GunStats#stunEnergyMultiplier} times the energy and
 * {@link #STUN_HEAT_MULTIPLIER} times the heat, and makes no sound and no noise; overheating stays loud. Kept apart from {@link EnergyGunItem} so gametests can drive it directly with a mock player.
 */
public final class GunLogic {
	/** About 1.2 s: the gun can't fire while the new pack is seated. */
	public static final int RELOAD_TICKS = 24;
	/** Pause after a dry click so a held trigger doesn't spam clicks every tick. */
	public static final int EMPTY_CLICK_TICKS = 8;
	/** Overheating is louder than any shot: the radius is the shot radius times this. */
	public static final float OVERHEAT_NOISE_FACTOR = 1.5f;
	/** A stun shot heats the gun this much more than a normal one. */
	public static final float STUN_HEAT_MULTIPLIER = 1.5f;
	/** Pale blue stun bolts, whatever the gun's own colour. */
	public static final int STUN_BOLT_COLOR = 0xBFE8FF;
	/** Action bar line after a mode switch: "Fire mode: %s". */
	public static final String MESSAGE_FIRE_MODE = "message.ashfall.gun.fire_mode";

	/** What a trigger pull did. */
	public enum FireResult {
		/**
		 * The shooter is not a player. Powered weapons are a survivor's tool: zombies and every other
		 * mob may carry a blaster (and drop it), but never fire it.
		 */
		NOT_A_PLAYER,
		FIRED, COOLDOWN, EMPTY, LOCKED, NO_STATS;

		/** Whether an automatic gun should stop firing (anything but a shot or a not-yet-ready tick). */
		public boolean stopsAutoFire() {
			return this != FIRED && this != COOLDOWN;
		}
	}

	private GunLogic() {
	}

	/**
	 * Attempts one shot of {@code gun} by {@code shooter} at the current game time. Only players fire: the
	 * check sits here, the single entry to firing, so no route (an AI goal, another mod calling the item)
	 * can let a zombie carrying a dead player's blaster shoot it. Nothing is spent on a refused pull.
	 */
	public static FireResult tryFire(ServerLevel level, LivingEntity shooter, ItemStack gun) {
		if (!canFire(shooter)) {
			return FireResult.NOT_A_PLAYER;
		}
		if (!(gun.getItem() instanceof EnergyGunItem item)) {
			return FireResult.NO_STATS;
		}
		GunStats stats = effectiveStats(false, gun, shooter.isShiftKeyDown());
		if (stats == null) {
			return FireResult.NO_STATS;
		}
		long now = level.getGameTime();
		GunState state = gun.getOrDefault(ArmsComponents.GUN_STATE, GunState.EMPTY);
		if (state.lastShotTick() > 0 && now >= state.lastShotTick() && now - state.lastShotTick() < stats.ticksBetweenShots()) {
			return FireResult.COOLDOWN;
		}
		if (state.lockedAt(now, stats.coolPerSecond())) {
			dryClick(level, shooter, gun, 0.7f);
			return FireResult.LOCKED;
		}

		boolean stun = isStunMode(gun, stats);
		float energyPerShot = energyPerShot(stats, stun);
		boolean free = shooter instanceof Player player && player.hasInfiniteMaterials();
		ItemStackTemplate pack = gun.get(ArmsComponents.LOADED_PACK);
		long units = EnergyCost.unitsToDraw(energyPerShot, state.energyCredit());
		float credit = state.energyCredit();
		if (!free) {
			long stored = pack == null ? 0L : EnergyItems.stored(pack);
			if (pack == null || stored < units) {
				dryClick(level, shooter, gun, 1.0f);
				return FireResult.EMPTY;
			}
			gun.set(ArmsComponents.LOADED_PACK, EnergyItems.withEnergy(pack, stored - units));
			credit = EnergyCost.creditAfter(energyPerShot, state.energyCredit(), units);
		}

		HeatModel.Shot shot = HeatModel.fire(state.heat(), state.lastShotTick(), state.locked(), now,
				stats.coolPerSecond(), heatPerShot(stats, stun), stats.heatCapacity());
		gun.set(ArmsComponents.GUN_STATE, new GunState(shot.heat(), now, shot.locked(), credit));

		// Steady Aim: a player's bolts hit harder per rank
		float damage = stats.damage() * (shooter instanceof Player p ? 1f + (float) Skills.bonus(p, Skill.STEADY_AIM) : 1f);
		EnergyBolt bolt = EnergyBolt.create(level, shooter, stun ? 0f : damage,
				stats.headshotMultiplier() * (shooter instanceof Player hp ? 1f + (float) Skills.bonus(hp, Skill.HEADHUNTER) : 1f),
				stats.range(), stun ? STUN_BOLT_COLOR : stats.boltColor());
		bolt.setStyle(item.boltStyle());
		bolt.setStun(stun);
		bolt.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0f, stats.boltSpeed(), stats.spread());
		level.addFreshEntity(bolt);

		if (!stun) {
			float noise = stats.noiseRadius();
			// Loudness above 1 makes the shot carry further (16 blocks per 1.0); the Dampener cuts it by more than half.
			float volume = item.fireSound().loudness()
					* AttachmentMath.volume(AttachmentStatsManager.fitted(false, gun.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE)));
			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), item.fireSound().sound(), SoundSource.PLAYERS,
					volume, 0.95f + level.getRandom().nextFloat() * 0.1f);
			level.gameEvent(shooter, GameEvent.PROJECTILE_SHOOT, shooter.position());
			ArmsNoise.emit(level, shooter.blockPosition(), noise);
		}

		if (shot.justLocked()) {
			overheat(level, shooter, stats.noiseRadius() * OVERHEAT_NOISE_FACTOR);
		}
		if (!item.isAutomatic() && shooter instanceof Player player) {
			// Semi guns: the cooldown both enforces RPM on clicks and shows the recovery on the hotbar.
			player.getCooldowns().addCooldown(gun, stats.ticksBetweenShots());
		}
		return FireResult.FIRED;
	}

	/**
	 * The gun's datapack stats with its fitted attachments applied, or null when no stats are loaded. One
	 * function for server and client, so the HUD's heat bar is computed with exactly the numbers the
	 * server fires with. {@code aiming} (sneaking) adds the Optic's tighter spread.
	 */
	public static GunStats effectiveStats(boolean clientSide, ItemStack gun, boolean aiming) {
		GunStats base = GunStatsManager.get(clientSide, gun.getItem());
		if (base == null) {
			return null;
		}
		GunAttachments attachments = gun.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE);
		if (attachments.isEmpty()) {
			return base;
		}
		return AttachmentMath.apply(base, AttachmentStatsManager.fitted(clientSide, attachments), aiming);
	}

	/** Whether these stats give the gun a stun mode at all. */
	public static boolean hasStunMode(@Nullable GunStats stats) {
		return stats != null && stats.stunEnergyMultiplier() > 0f;
	}

	/** The mode saved on the gun; DAMAGE when unset. */
	public static FireMode fireMode(ItemStack gun) {
		return gun.getOrDefault(ArmsComponents.FIRE_MODE, FireMode.DAMAGE);
	}

	/**
	 * Whether the next shot is a stun shot: the gun is set to STUN and its stats still allow it (a datapack that
	 * drops a gun's stun mode turns its saved STUN back into ordinary shots).
	 */
	public static boolean isStunMode(ItemStack gun, @Nullable GunStats stats) {
		return hasStunMode(stats) && fireMode(gun) == FireMode.STUN;
	}

	/** Energy one shot costs in the given mode. */
	public static float energyPerShot(GunStats stats, boolean stun) {
		return stun ? stats.energyPerShot() * stats.stunEnergyMultiplier() : stats.energyPerShot();
	}

	/** Heat one shot adds in the given mode. */
	public static float heatPerShot(GunStats stats, boolean stun) {
		return stun ? stats.heatPerShot() * STUN_HEAT_MULTIPLIER : stats.heatPerShot();
	}

	/**
	 * Switches {@code gun} between DAMAGE and STUN and tells the player on the action bar. Refused (null, nothing
	 * changes) for anything that isn't a gun with a stun mode. The switch itself is quiet: a soft click only.
	 */
	public static @Nullable FireMode toggleMode(Player player, ItemStack gun) {
		if (!(gun.getItem() instanceof EnergyGunItem)) {
			return null;
		}
		GunStats stats = GunStatsManager.get(player.level().isClientSide(), gun.getItem());
		if (!hasStunMode(stats)) {
			return null;
		}
		FireMode next = fireMode(gun).next();
		if (next == FireMode.DAMAGE) {
			gun.remove(ArmsComponents.FIRE_MODE);
		} else {
			gun.set(ArmsComponents.FIRE_MODE, next);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ArmsSounds.GUN_EMPTY, SoundSource.PLAYERS,
				0.3f, next == FireMode.STUN ? 1.6f : 1.3f);
		player.sendOverlayMessage(Component.translatable(MESSAGE_FIRE_MODE,
				Component.translatable("tooltip.ashfall.gun.mode." + next.getSerializedName()))
				.withStyle(next == FireMode.STUN ? ChatFormatting.AQUA : ChatFormatting.WHITE));
		return next;
	}

	/** Whether {@code shooter} may fire powered weapons at all: players only. */
	public static boolean canFire(LivingEntity shooter) {
		return shooter instanceof Player;
	}

	private static void dryClick(ServerLevel level, LivingEntity shooter, ItemStack gun, float pitch) {
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), ArmsSounds.GUN_EMPTY, SoundSource.PLAYERS, 0.6f, pitch);
		if (shooter instanceof Player player) {
			player.getCooldowns().addCooldown(gun, EMPTY_CLICK_TICKS);
		}
	}

	/** Overheat lockout: hiss, steam and a noise louder than the shot that caused it. */
	private static void overheat(ServerLevel level, LivingEntity shooter, float noise) {
		Vec3 muzzle = shooter.getEyePosition().add(shooter.getLookAngle().scale(0.6));
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), ArmsSounds.GUN_OVERHEAT, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, muzzle.x, muzzle.y - 0.2, muzzle.z, 14, 0.15, 0.1, 0.15, 0.03);
		level.sendParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y - 0.2, muzzle.z, 4, 0.1, 0.1, 0.1, 0.02);
		level.gameEvent(shooter, GameEvent.EXPLODE, shooter.position());
		ArmsNoise.emit(level, shooter.blockPosition(), noise);
	}

	/**
	 * The gun the player would reload, switch mode on or aim: the main hand's only. Guns don't work from the
	 * off hand (see {@link EnergyGunItem#use}), so neither does anything else. Empty if the main hand holds no gun.
	 */
	public static ItemStack heldGun(Player player) {
		ItemStack stack = player.getMainHandItem();
		return stack.getItem() instanceof EnergyGunItem ? stack : ItemStack.EMPTY;
	}

	/**
	 * Swaps the gun's pack for the fullest matching pack in the player's inventory, if that holds more
	 * energy than the one loaded. The old pack (even an empty one, so it can be recharged) goes back to
	 * the inventory, or drops if there is no room. Returns whether a swap happened.
	 */
	public static boolean reload(Player player, ItemStack gun) {
		if (!(gun.getItem() instanceof EnergyGunItem item)) {
			return false;
		}
		ItemStackTemplate loaded = gun.get(ArmsComponents.LOADED_PACK);
		long best = loaded == null ? 0L : EnergyItems.stored(loaded);
		Inventory inventory = player.getInventory();
		int bestSlot = -1;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack candidate = inventory.getItem(slot);
			if (!candidate.is(item.gunClass().pack())) {
				continue;
			}
			long energy = SimpleEnergyItem.getStoredEnergyUnchecked(candidate);
			if (energy > best) {
				best = energy;
				bestSlot = slot;
			}
		}
		if (bestSlot < 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ArmsSounds.GUN_EMPTY,
					SoundSource.PLAYERS, 0.5f, 1.3f);
			return false;
		}
		ItemStack fresh = inventory.getItem(bestSlot).split(1);
		inventory.setChanged();
		if (player.isUsingItem() && player.getUseItem() == gun) {
			player.stopUsingItem();
		}
		gun.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(fresh));
		if (loaded != null) {
			inventory.placeItemBackInInventory(loaded.create(), Prediction.SERVER_ONLY);
		}
		player.getCooldowns().addCooldown(gun, RELOAD_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ArmsSounds.GUN_RELOAD,
				SoundSource.PLAYERS, 0.8f, 1.0f);
		return true;
	}
}
