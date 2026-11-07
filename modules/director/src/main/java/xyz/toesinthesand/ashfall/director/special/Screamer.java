package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;

import java.util.List;

/**
 * The screamer: frail, but the moment it has prey it shrieks. The shriek is a very large SOUND stimulus, so every
 * zombie in earshot (ambient walkers, building populations, a whole horde, whose leader hears at twice the range)
 * walks to it through the normal senses goal, and zombies close by also get a short speed buff. One unanswered
 * screamer turns a quiet street into a horde, which is why it carries an Energy Cell and why the spawn rules cap
 * it per player.
 * <p>
 * Tuning keys (special_infected/screamer.json): {@code scream_radius} (48), {@code scream_cooldown_seconds} (20),
 * {@code buff_radius} (16), {@code buff_seconds} (8), {@code buff_amplifier} (1).
 */
public class Screamer extends SpecialInfectedEntity {
	private int screamCooldown;

	public Screamer(EntityType<? extends Zombie> type, Level level) {
		super(type, level, SpecialKind.SCREAMER);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (screamCooldown > 0) {
			screamCooldown--;
		} else if (getTarget() != null && getTarget().isAlive()) {
			scream(level);
		}
	}

	/** Ticks until the next shriek is allowed. */
	public int screamCooldown() {
		return screamCooldown;
	}

	/**
	 * Shrieks now: sound, the big stimulus and the speed buff, then the cooldown. Public for tests and so a future
	 * horde leader can trigger it.
	 *
	 * @return the stimulus left in the field (null if the field dropped it)
	 */
	public Stimulus scream(ServerLevel level) {
		screamCooldown = Math.round(tuning("scream_cooldown_seconds", 20f) * 20);
		level.playSound(null, getX(), getY(), getZ(), SpecialSounds.SCREAM, SoundSource.HOSTILE, 4.0f, 1.0f);
		Stimulus stimulus = Stimuli.emit(level, getEyePosition(), StimulusType.SOUND, tuning("scream_radius", 48f), this);
		double buffRadius = tuning("buff_radius", 16f);
		int buffTicks = Math.round(tuning("buff_seconds", 8f) * 20);
		int amplifier = Math.round(tuning("buff_amplifier", 1f));
		List<Zombie> near = level.getEntitiesOfClass(Zombie.class, getBoundingBox().inflate(buffRadius), z -> z != this && z.isAlive());
		Vec3 here = position();
		for (Zombie zombie : near) {
			if (zombie.position().distanceToSqr(here) <= buffRadius * buffRadius) {
				zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, buffTicks, amplifier));
				if (zombie.getTarget() == null && getTarget() != null) {
					// the nearest pack goes straight for the prey, the rest come to the noise
					zombie.setTarget(getTarget());
				}
			}
		}
		return stimulus;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("ashfall_scream_cooldown", screamCooldown);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		screamCooldown = input.getIntOr("ashfall_scream_cooldown", 0);
	}
}
