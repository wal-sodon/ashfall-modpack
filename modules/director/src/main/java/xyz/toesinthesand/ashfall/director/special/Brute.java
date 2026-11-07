package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * The brute: 1.6x scale (the vanilla {@code minecraft:scale} attribute, so hitbox, reach and rendering all grow
 * together), high health and armour, and a hit that throws players back. Its siege job is breaking in: under
 * Zombies Break and Build it deals {@code block_break_multiplier} times the block damage of a zombie its size
 * ({@code BlockDamageCalculatorMixin}). It is deliberately not in {@code #ashfall:bosses}, so fortified blocks
 * still hold against it: a brute tears through a wooden shack, not through a reinforced base.
 * <p>
 * Tuning keys (special_infected/brute.json): {@code block_break_multiplier} (2.5).
 */
public class Brute extends SpecialInfectedEntity {
	public Brute(EntityType<? extends Zombie> type, Level level) {
		super(type, level, SpecialKind.BRUTE);
	}

	/**
	 * How much harder than its size alone a mob hits blocks under ZB&B: the brute's tuning, 1 for everything else.
	 * Kept here (not in the mixin) so it is testable without ZB&B installed.
	 */
	public static float blockBreakMultiplier(PathfinderMob mob) {
		if (mob instanceof Brute brute) {
			return Math.max(0f, brute.tuning("block_break_multiplier", 2.5f));
		}
		return 1f;
	}
}
