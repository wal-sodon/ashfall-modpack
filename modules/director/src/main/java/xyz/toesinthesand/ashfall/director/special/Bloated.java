package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;

/**
 * The bloated: slow and swollen, and the threat is killing it up close. On death it bursts into a lingering cloud
 * (a vanilla area effect cloud: poison and nausea) tagged {@link InfectionClouds#TAG}, and every second a player
 * stands in it rolls an infection chance ({@link InfectionClouds}). That makes it a target to shoot from range,
 * which is what the blasters are for, rather than one more thing to melee.
 * <p>
 * Tuning keys (special_infected/bloated.json): {@code cloud_radius} (3.5), {@code cloud_seconds} (12),
 * {@code cloud_poison_seconds} (5), {@code burst_noise_radius} (16).
 */
public class Bloated extends SpecialInfectedEntity {
	public Bloated(EntityType<? extends Zombie> type, Level level) {
		super(type, level, SpecialKind.BLOATED);
	}

	@Override
	public void die(DamageSource source) {
		boolean wasAlive = !isRemoved() && !dead;
		super.die(source);
		if (wasAlive && level() instanceof ServerLevel server) {
			burst(server);
		}
	}

	/** Spawns the infection cloud where the bloated stands. Public so tests and commands can trigger it directly. */
	public AreaEffectCloud burst(ServerLevel level) {
		float radius = tuning("cloud_radius", 3.5f);
		int duration = Math.round(tuning("cloud_seconds", 12f) * 20);
		AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
		cloud.setOwner(this);
		cloud.setRadius(radius);
		cloud.setDuration(duration);
		cloud.setWaitTime(10);
		// shrink to nothing over its life, like a lingering potion
		cloud.setRadiusPerTick(-radius / Math.max(1, duration));
		int poison = Math.round(tuning("cloud_poison_seconds", 5f) * 20);
		cloud.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0));
		cloud.addEffect(new MobEffectInstance(MobEffects.NAUSEA, poison * 2, 0));
		cloud.addTag(InfectionClouds.TAG);
		level.addFreshEntity(cloud);
		level.playSound(null, getX(), getY(), getZ(), SpecialSounds.BURST, SoundSource.HOSTILE, 1.5f, 1.0f);
		Stimuli.emit(level, new Vec3(getX(), getY() + 1, getZ()), StimulusType.SOUND, tuning("burst_noise_radius", 16f), this);
		return cloud;
	}
}
