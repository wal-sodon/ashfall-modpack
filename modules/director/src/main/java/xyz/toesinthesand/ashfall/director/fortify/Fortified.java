package xyz.toesinthesand.ashfall.director.fortify;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.haven.Havens;

/**
 * The rules behind the Zombies Break and Build vetoes (spec section 4), kept free of ZB&B types so they
 * are testable without the mod and the mixins stay one-liners.
 * <ul>
 *   <li>{@code #ashfall:fortified} blocks (iron, obsidian, copper doors, reinforced planks, barricades)
 *   cannot be broken outright by zombies, except by {@code #ashfall:bosses} (the mutant zombie; Undead Nights' demolition
 *   zombie was listed until that mod left the pack). Since 10.2.0 a zombie's hits wear them down instead
 *   ({@link FortifyWear}) until they downgrade into a block ZB&B breaks as usual: a fortified base holds a horde
 *   for a while, not forever, and needs repair after.</li>
 *   <li>Zombies never build inside a land claim, so a horde can't pillar or bridge into a base.</li>
 *   <li>Zombies break nothing at all inside a safe zone (plan: safe zones are partial havens with no ZB&B
 *   building or breaking). A Bunker Core's area gets only the fortified rule above, so a horde can still
 *   break a base's ordinary blocks.</li>
 * </ul>
 */
public final class Fortified {
	public static final TagKey<Block> FORTIFIED = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("ashfall", "fortified"));
	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("ashfall", "bosses"));

	private Fortified() {
	}

	/** Whether a mob of {@code breaker} type may break {@code state}. */
	public static boolean mayBreak(BlockState state, EntityType<?> breaker) {
		return !state.is(FORTIFIED) || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(breaker).is(BOSSES);
	}

	/** {@link #mayBreak(BlockState, EntityType)} plus the safe-zone rule: nothing breaks inside a safe zone. */
	public static boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, EntityType<?> breaker) {
		return !Havens.inSafeZone(level, pos) && mayBreak(state, breaker);
	}

	/**
	 * The ZB&B break gate: whether {@code mob} may start breaking the block at {@code pos}. Outside a safe zone a
	 * non-boss zombie that is refused a fortified block wears it instead ({@link FortifyWear#zombieHit}); inside one
	 * nothing breaks or wears. A still-sealed lair's doors ({@link BossLairs#sealedDoor}, the hospital's deep ward)
	 * neither break nor wear, for bosses too.
	 */
	public static boolean zombieBreak(ServerLevel level, BlockPos pos, BlockState state, Mob mob) {
		if (Havens.inSafeZone(level, pos) || BossLairs.sealedDoor(level, pos, state)) {
			return false;
		}
		if (mayBreak(state, mob.getType())) {
			return true;
		}
		FortifyWear.zombieHit(level, pos, state, mob);
		return false;
	}

	/** Whether a mob may place a block at {@code pos}: never inside a claim. */
	public static boolean mayBuild(ServerLevel level, BlockPos pos) {
		return !Claims.isClaimed(level, pos);
	}
}
