package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The spitter: keeps its melee, but anything it can see between a few blocks and its spit range gets a gob of
 * acid ({@link AcidSpit}): a little damage, poison and an infection roll. It punishes standing still on a
 * barricade or a rooftop out of reach, the habits that make ordinary zombies harmless.
 * <p>
 * Tuning keys (special_infected/spitter.json): {@code spit_range} (16), {@code spit_min_range} (3),
 * {@code spit_cooldown_seconds} (3.5), {@code spit_damage} (3), {@code spit_poison_seconds} (4),
 * {@code spit_infection_chance} (0.05), {@code spit_speed} (1.4).
 */
public class Spitter extends SpecialInfectedEntity {
	private int spitCooldown;

	public Spitter(EntityType<? extends Zombie> type, Level level) {
		super(type, level, SpecialKind.SPITTER);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (spitCooldown > 0) {
			spitCooldown--;
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		double range = tuning("spit_range", 16f);
		double min = tuning("spit_min_range", 3f);
		double d2 = distanceToSqr(target);
		if (d2 <= range * range && d2 >= min * min && getSensing().hasLineOfSight(target)) {
			spitAt(level, target);
		}
	}

	/** Ticks until the next spit is allowed (above 0 right after a spit). */
	public int spitCooldown() {
		return spitCooldown;
	}

	/**
	 * Spits at {@code target} now, like a llama does (aim a little above the line to arc it), and starts the
	 * cooldown. Public for tests.
	 *
	 * @return the projectile, already in the level, or null if it could not be added
	 */
	@Nullable
	public AcidSpit spitAt(ServerLevel level, LivingEntity target) {
		spitCooldown = Math.round(tuning("spit_cooldown_seconds", 3.5f) * 20);
		AcidSpit spit = new AcidSpit(SpecialEntities.ACID_SPIT, level);
		spit.setOwner(this);
		spit.setPos(getX(), getEyeY() - 0.1, getZ());
		spit.configure(tuning("spit_damage", 3f), Math.round(tuning("spit_poison_seconds", 4f) * 20),
				tuning("spit_infection_chance", 0.05f));
		double dx = target.getX() - getX();
		double dy = target.getY(0.3333) - spit.getY();
		double dz = target.getZ() - getZ();
		double arc = Math.sqrt(dx * dx + dz * dz) * 0.2;
		spit.shoot(dx, dy + arc, dz, tuning("spit_speed", 1.4f), 6.0f);
		lookAt(target, 30f, 30f);
		level.playSound(null, getX(), getY(), getZ(), SpecialSounds.SPIT, SoundSource.HOSTILE, 1.0f, 0.8f + getRandom().nextFloat() * 0.3f);
		return level.addFreshEntity(spit) ? spit : null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("ashfall_spit_cooldown", spitCooldown);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		spitCooldown = input.getIntOr("ashfall_spit_cooldown", 0);
	}
}
