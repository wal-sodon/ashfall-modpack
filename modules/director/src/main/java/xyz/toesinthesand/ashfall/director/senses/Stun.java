package xyz.toesinthesand.ashfall.director.senses;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;

/**
 * The {@code ashfall:stunned} effect: a held mob can't move, attack, pick a target or sense anything. Arms' stun
 * bolts apply it through {@link #tryStun}; it lives here because the Director's senses and mixins must check it and
 * director cannot see arms classes.
 * <ul>
 *   <li><b>Frozen AI:</b> {@code MobStunMixin} skips {@code Mob#serverAiStep} while the effect lasts, so goals,
 *   pathing and special abilities (a Screamer's scream) stop; gravity and knockback from others still apply.</li>
 *   <li><b>No senses:</b> {@link SenseGoal#pickStimulus} and the horde leader's stimulus pick return nothing, and
 *   {@code MobTargetMixin} refuses new targets.</li>
 *   <li><b>Durations:</b> {@link #STUN_TICKS} for ordinary mobs, half for special infected, {@link #BOSS_STUN_TICKS}
 *   for lair bosses and {@code #ashfall:stun_immune}. After a stun ends the mob can't be stunned again for
 *   {@link #LOCKOUT_TICKS}, so a crowd can't be held forever by one pistol.</li>
 * </ul>
 * Players, pets and survivors ({@link Friendly}) are never stunned.
 */
public final class Stun {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "stunned");
	/** About 3 s. */
	public static final int STUN_TICKS = 60;
	public static final int SPECIAL_STUN_TICKS = STUN_TICKS / 2;
	public static final int BOSS_STUN_TICKS = 10;
	/** After a stun wears off, the mob shrugs off further stuns for this long. */
	public static final int LOCKOUT_TICKS = 60;
	/** Pale blue: the effect's swirl particles on a held mob. */
	public static final int COLOR = 0xBFE8FF;

	/** Mobs that only flinch: {@code data/ashfall/tags/entity_type/stun_immune.json} (the bosses by default). */
	public static final TagKey<EntityType<?>> STUN_IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "stun_immune"));

	/**
	 * Game time before which the mob can't be stunned again. Not saved: a reloaded chunk forgetting a three-second
	 * lockout is harmless, and keeping it on the entity means there is no static map to clean up.
	 */
	public static final AttachmentType<Long> LOCKED_UNTIL = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "stun_locked_until"));

	public static final Holder<MobEffect> STUNNED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ID,
			new StunnedEffect().addAttributeModifier(Attributes.MOVEMENT_SPEED, ID, -1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	private Stun() {
	}

	/** Forces class loading, and so registration. */
	static void init() {
	}

	public static boolean isStunned(LivingEntity entity) {
		return entity.hasEffect(STUNNED);
	}

	/** How long a stun holds {@code mob}: bosses and the immune tag briefly, special infected half as long. */
	public static int durationFor(Mob mob) {
		if (mob.entityTags().contains(BossLairs.BOSS_TAG) || mob.typeHolder().is(STUN_IMMUNE)) {
			return BOSS_STUN_TICKS;
		}
		return mob instanceof SpecialInfectedEntity ? SPECIAL_STUN_TICKS : STUN_TICKS;
	}

	/**
	 * Stuns {@code target} if it can be: a living, non-friendly mob that isn't already held or in its lockout.
	 * Returns the ticks applied, or 0 when refused. No damage, no anger: the attacker is only the effect's source.
	 */
	public static int tryStun(LivingEntity target, @Nullable Player attacker) {
		if (!(target instanceof Mob mob) || !mob.isAlive() || mob.level().isClientSide()
				|| Friendly.isFriendly(mob, attacker) || isStunned(mob)) {
			return 0;
		}
		long now = mob.level().getGameTime();
		Long lockedUntil = mob.getAttached(LOCKED_UNTIL);
		if (lockedUntil != null && now < lockedUntil) {
			return 0;
		}
		int ticks = durationFor(mob);
		if (!mob.addEffect(new MobEffectInstance(STUNNED, ticks, 0, false, true, true), attacker)) {
			return 0;
		}
		mob.setAttached(LOCKED_UNTIL, now + ticks + LOCKOUT_TICKS);
		halt(mob);
		return ticks;
	}

	/** Drops the mob's target and path and zeroes its movement input, so it stops on the spot. */
	public static void halt(Mob mob) {
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.setSpeed(0f);
		mob.xxa = 0f;
		mob.yya = 0f;
		mob.zza = 0f;
		mob.setJumping(false);
	}

	/** The effect itself: the movement modifier and a halt when it starts (for {@code /effect} too). */
	private static final class StunnedEffect extends MobEffect {
		StunnedEffect() {
			super(MobEffectCategory.HARMFUL, COLOR);
		}

		@Override
		public void onEffectStarted(LivingEntity entity, int amplifier) {
			super.onEffectStarted(entity, amplifier);
			if (entity instanceof Mob mob && !mob.level().isClientSide()) {
				halt(mob);
			}
		}
	}
}
