package xyz.toesinthesand.ashfall.arms.gun;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who set a mob on fire: the flamethrower, or something else. It matters for one rule: a flamethrower burn does not
 * stop the Mutant's revive (that stays the Lancer's counter). Mutant Monsters' Mutant Zombie gives up its revive when
 * it dies while {@code isOnFire()}, or when it is ignited while already dying, so the flamethrower:
 * <ul>
 *   <li>sets burns with {@code setRemainingFireTicks}, never {@code igniteForTicks}, and skips mobs that are dying;</li>
 *   <li>claims a burn ({@link #BURN_UNTIL}) only when the mob was not burning or the burn was already its own. If
 *   anything else lengthens it (fire, lava, a Lancer), the burn is no longer the flamethrower's until it goes out;</li>
 *   <li>puts its own burn out at the moment of death ({@code ALLOW_DEATH}) for mobs in
 *   {@code #ashfall:flamer_revive_safe}, so their death sees no fire.</li>
 * </ul>
 */
public final class FlamerBurn {
	/** Mobs whose death a flamethrower burn must not see: {@code data/ashfall/tags/entity_type/flamer_revive_safe.json}. */
	public static final TagKey<EntityType<?>> REVIVE_SAFE = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "flamer_revive_safe"));

	/**
	 * Game time at which the flamethrower's burn on this mob runs out. Not saved: a reloaded mob's burn simply counts
	 * as anyone's, and keeping it on the entity means there is no static map to clean up.
	 */
	public static final AttachmentType<Long> BURN_UNTIL = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "flamer_burn"));

	private FlamerBurn() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity.typeHolder().is(REVIVE_SAFE)) {
				spareRevive(entity);
			}
			return true;
		});
	}

	/**
	 * Sets {@code target} burning for at least {@code ticks}. The burn is the flamethrower's when the mob was not
	 * burning or was burning only from the flamethrower; someone else's burn is lengthened but stays theirs.
	 */
	public static void ignite(LivingEntity target, int ticks) {
		int current = target.getRemainingFireTicks();
		boolean claim = current <= 0 || isFlamerBurn(target);
		int burn = Math.max(current, ticks);
		target.setRemainingFireTicks(burn);
		if (claim) {
			target.setAttached(BURN_UNTIL, target.level().getGameTime() + burn);
		}
	}

	/**
	 * Whether {@code target} is burning from the flamethrower alone: it is on fire and that fire ends no later than
	 * the flamethrower's last burn (one tick of slack for the order entities tick in).
	 */
	public static boolean isFlamerBurn(LivingEntity target) {
		Long until = target.getAttached(BURN_UNTIL);
		int fire = target.getRemainingFireTicks();
		return until != null && fire > 0 && target.level().getGameTime() + fire <= until + 1;
	}

	/** Puts out a burn that is the flamethrower's alone; returns whether it did. Any other fire is left burning. */
	public static boolean spareRevive(LivingEntity target) {
		if (!isFlamerBurn(target)) {
			return false;
		}
		target.clearFire();
		target.removeAttached(BURN_UNTIL);
		return true;
	}
}
