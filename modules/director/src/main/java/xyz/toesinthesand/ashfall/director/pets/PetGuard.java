package xyz.toesinthesand.ashfall.director.pets;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;

/**
 * Friendly fire against pets and survivors, done once for every weapon: a tamed pet ({@link Friendly#isPet}) or a
 * survivor ({@link Friendly#isSurvivor}) takes no damage from any player (its owner or leader included), from anything
 * a player shot, threw, lit or tamed, from another survivor, or from an Ashfall trap. Cancelled rather than zeroed so
 * there is no knockback or hurt flash either. Zombies and other mobs still hurt them.
 * <p>
 * Survivors (not pets) also ignore the world damage a player can aim at them without being its source
 * ({@link #SURVIVORS_IGNORE}: lava, fire, a hot floor, suffocation, falling anvils, blocks and stalactites), so a bucket
 * of lava or a dropped anvil can't kill one either. Damage that bypasses invulnerability (/kill, the void) still does.
 * <p>
 * {@code /gamerule ashfall:friendly_fire true} (off by default, also a button in the Game Rules screen) drops the guard
 * for pets, and {@link Friendly#isFriendly} stops sparing pets and other players from area damage. Survivors stay
 * protected either way.
 */
public final class PetGuard {
	/** Damage types survivors never take ({@code data/ashfall/tags/damage_type/survivors_ignore.json}). */
	public static final TagKey<DamageType> SURVIVORS_IGNORE = TagKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "survivors_ignore"));

	/** Whether players can hurt pets, and each other with splash and area weapons. Registered when {@link #init} loads this class. */
	public static final GameRule<Boolean> FRIENDLY_FIRE = GameRuleBuilder.forBoolean(false)
			.category(GameRuleCategory.PLAYER)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ashfall", "friendly_fire"));

	private PetGuard() {
	}

	/** Whether friendly fire is on in {@code level}'s world (always off on the client, which never decides damage). */
	public static boolean friendlyFire(Level level) {
		return level instanceof ServerLevel server && server.getGameRules().get(FRIENDLY_FIRE);
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !spares(entity, source));
	}

	/** Whether {@code target} is a pet or survivor that {@code source} must not hurt. */
	public static boolean spares(Entity target, DamageSource source) {
		if (!Friendly.isPet(target) && !Friendly.isSurvivor(target)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| !Friendly.isSurvivor(target) && friendlyFire(target.level())) {
			return false;
		}
		if (Friendly.isSurvivor(target) && source.is(SURVIVORS_IGNORE)) {
			return true;
		}
		return source.is(SpikeStripBlock.DAMAGE) || playerSide(source.getEntity()) || playerSide(source.getDirectEntity());
	}

	/** A player, a pet (only players tame), a survivor, or a projectile one of those fired. */
	private static boolean playerSide(@Nullable Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entity instanceof Player || Friendly.isPet(entity) || Friendly.isSurvivor(entity)) {
			return true;
		}
		return entity instanceof Projectile projectile && projectile.getOwner() instanceof Player;
	}
}
