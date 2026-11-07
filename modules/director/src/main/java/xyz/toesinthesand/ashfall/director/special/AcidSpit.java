package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import xyz.toesinthesand.ashfall.director.infection.Infection;

/**
 * The spitter's acid. Extends {@link LlamaSpit} so the client draws it with the vanilla llama-spit renderer and
 * particles (no new art) and it inherits the spit's flight, drag and block hits. Only the entity hit differs:
 * the spitter's damage instead of the llama's 1, poison, and an infection roll on players.
 * <p>
 * Never hurts zombies: spitters stand behind the horde, and acid in the backs of their own front line would thin
 * it for the player.
 */
public class AcidSpit extends LlamaSpit {
	private float damage = 3f;
	private int poisonTicks = 80;
	private float infectionChance = 0.05f;

	public AcidSpit(EntityType<? extends LlamaSpit> type, Level level) {
		super(type, level);
	}

	/** Copies the spitter's numbers onto this shot (the projectile is not saved, so nothing else needs them). */
	public void configure(float damage, int poisonTicks, float infectionChance) {
		this.damage = damage;
		this.poisonTicks = poisonTicks;
		this.infectionChance = infectionChance;
	}

	public float damage() {
		return damage;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		Entity target = hit.getEntity();
		if (target instanceof Zombie || !(level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = getOwner();
		DamageSource source = owner instanceof LivingEntity living
				? damageSources().spit(this, living) : damageSources().mobProjectile(this, null);
		if (target.hurtServer(level, source, damage) && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.POISON, poisonTicks, 0), this);
			if (living instanceof ServerPlayer player) {
				Infection.tryInfect(player, infectionChance, player.getRandom().nextFloat());
			}
		}
	}
}
