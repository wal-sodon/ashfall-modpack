package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The stalker: invisible while it is in the dark and nobody is close, so a dark interior can hold one you walk
 * right past. When a player comes within {@code reveal_distance} it drops its cover with a hiss, gets a short burst
 * of speed, and its first hit lands harder: the ambush. Light is the counter: in a lit spot it is always visible,
 * which is one more reason the light and darkness design matters.
 * <p>
 * Uses the vanilla invisible flag, so the vanilla renderer hides it (spectators still see the usual ghost) and
 * vanilla mob AI and player targeting need no changes. Taking damage reveals it for a few seconds.
 * <p>
 * Tuning keys (special_infected/stalker.json): {@code dark_light_level} (5, the brightest block light it can hide
 * in), {@code reveal_distance} (5), {@code ambush_bonus_damage} (4), {@code ambush_speed_seconds} (3),
 * {@code reveal_after_hurt_seconds} (4).
 */
public class Stalker extends SpecialInfectedEntity {
	/** How often the hide check runs: a light-level read and a player lookup, four times a second. */
	public static final int CHECK_INTERVAL = 5;
	private static final Identifier AMBUSH_DAMAGE = Identifier.fromNamespaceAndPath("ashfall", "stalker_ambush");

	private boolean ambushReady;

	public Stalker(EntityType<? extends Zombie> type, Level level) {
		super(type, level, SpecialKind.STALKER);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if ((tickCount + getId()) % CHECK_INTERVAL == 0) {
			updateCover(level);
		}
	}

	/** Whether the stalker may be hidden right now: dark spot, no player near, not just hurt. */
	public boolean shouldHide(ServerLevel level) {
		int light = level.getMaxLocalRawBrightness(blockPosition());
		if (light > tuning("dark_light_level", 5f)) {
			return false;
		}
		int hurtFor = Math.round(tuning("reveal_after_hurt_seconds", 4f) * 20);
		if (getLastHurtByMobTimestamp() > 0 && tickCount - getLastHurtByMobTimestamp() < hurtFor) {
			return false;
		}
		if (hurtTime > 0) {
			return false;
		}
		Player near = level.getNearestPlayer(getX(), getY(), getZ(), tuning("reveal_distance", 5f),
				p -> !p.isSpectator() && p.isAlive());
		return near == null;
	}

	/** Hides or reveals; a reveal next to a player springs the ambush. Public for tests. */
	public void updateCover(ServerLevel level) {
		boolean hide = shouldHide(level);
		if (hide == isInvisible()) {
			return;
		}
		setInvisible(hide);
		if (!hide) {
			Player near = level.getNearestPlayer(getX(), getY(), getZ(), tuning("reveal_distance", 5f) + 1,
					p -> !p.isSpectator() && p.isAlive());
			if (near != null) {
				ambushReady = true;
				addEffect(new MobEffectInstance(MobEffects.SPEED, Math.round(tuning("ambush_speed_seconds", 3f) * 20), 1));
				level.playSound(null, getX(), getY(), getZ(), SpecialSounds.AMBUSH, SoundSource.HOSTILE, 1.2f, 1.0f);
				if (getTarget() == null) {
					setTarget(near);
				}
			}
		}
	}

	public boolean ambushReady() {
		return ambushReady;
	}

	/** The ambush hit: bonus attack damage for exactly one swing, as a transient modifier so nothing is saved. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
		boolean bonus = ambushReady && attack != null;
		if (bonus) {
			attack.addTransientModifier(new AttributeModifier(AMBUSH_DAMAGE, tuning("ambush_bonus_damage", 4f),
					AttributeModifier.Operation.ADD_VALUE));
		}
		try {
			boolean hit = super.doHurtTarget(level, target);
			if (hit) {
				ambushReady = false;
			}
			return hit;
		} finally {
			if (bonus) {
				attack.removeModifier(AMBUSH_DAMAGE);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && isInvisible()) {
			setInvisible(false);
		}
		return hurt;
	}
}
