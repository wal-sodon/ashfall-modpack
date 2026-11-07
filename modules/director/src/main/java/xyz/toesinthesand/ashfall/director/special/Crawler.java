package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * The crawler: a low, fast zombie dragging itself along. Everything that makes it dangerous is its shape and
 * stats: the hitbox is under one block tall ({@link SpecialEntities}), so it comes through the gaps under
 * barricades, vents and half-broken walls that stop a walker, and it is hard to hit from above. Low health keeps it
 * a nuisance rather than a wall. No behaviour beyond the zombie's own.
 */
public class Crawler extends SpecialInfectedEntity {
	public Crawler(EntityType<? extends Zombie> type, Level level) {
		super(type, level, SpecialKind.CRAWLER);
	}
}
