package xyz.toesinthesand.ashfall.director.pets;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Who area damage from a player's weapon or trap must skip: every player (co-op friends can't hurt each other
 * with splash, chains or traps), every player's tamed pet, the dogs above all, and every survivor (arms' living NPCs,
 * known here only by the {@link #SURVIVORS} tag because director cannot see arms classes). One check so each weapon
 * doesn't grow its own list. With the {@link PetGuard#FRIENDLY_FIRE} game rule on, only the attacker and survivors
 * are spared.
 */
public final class Friendly {
	/** Every survivor entity type; arms fills it ({@code data/ashfall/tags/entity_type/survivors.json}). */
	public static final TagKey<EntityType<?>> SURVIVORS = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "survivors"));

	private Friendly() {
	}

	/**
	 * Whether {@code entity} is on the players' side.
	 *
	 * @param player who is dealing the damage, or null for a trap with no attacker; their own pets and everyone
	 *               else's count alike, so it only matters as the attacker themselves
	 */
	public static boolean isFriendly(Entity entity, @Nullable Player player) {
		if (PetGuard.friendlyFire(entity.level())) {
			return entity == player || isSurvivor(entity);
		}
		return entity == player || entity instanceof Player || isPet(entity) || isSurvivor(entity);
	}

	/** A tamed animal with an owner (only players tame in this pack). */
	public static boolean isPet(Entity entity) {
		return entity instanceof OwnableEntity pet && pet.getOwnerReference() != null;
	}

	/** A survivor ({@code #ashfall:survivors}): on the players' side, and prey to zombies like a player. */
	public static boolean isSurvivor(Entity entity) {
		return entity.typeHolder().is(SURVIVORS);
	}
}
