package xyz.toesinthesand.ashfall.director.senses;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * Gives every zombie its senses, and plain zombies their {@link HuntSurvivorsGoal}. Goals are not saved with the entity, so the goal is added on each
 * {@code ENTITY_LOAD} (spawn, chunk load, dimension change), guarded so a mob never gets two.
 * <p>
 * Covers the whole vanilla {@link Zombie} family (zombie, husk, drowned, zombie villager) and anything a
 * mod builds on it, which includes the Director's own spawns since they are vanilla zombies. Zombified
 * piglins are left alone: they are neutral Nether mobs and investigating noises would make them stalk
 * players who never angered them.
 */
public final class SenseGoals {
	/** Below the attack goal (2) so a target always wins, above wandering (7) so a noise beats a stroll. */
	public static final int PRIORITY = 4;

	private SenseGoals() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Zombie zombie && !(entity instanceof ZombifiedPiglin)) {
				attach(zombie, 1.0);
				attachHunt(zombie);
			}
		});
	}

	/**
	 * Adds a {@link SenseGoal} unless the mob already has one with this hearing. Public for the spawner,
	 * which passes a variant's hearing multiplier: a goal the load event added with the default hearing
	 * is replaced. Returns the goal the mob ends up with.
	 */
	public static SenseGoal attach(PathfinderMob mob, double hearing) {
		SenseGoal existing = find(mob);
		if (existing != null) {
			if (existing.hearing() == Math.min(hearing, StimulusField.MAX_REACH_SCALE)) {
				return existing;
			}
			mob.getGoalSelector().removeGoal(existing);
		}
		SenseGoal goal = new SenseGoal(mob, hearing);
		mob.getGoalSelector().addGoal(PRIORITY, goal);
		return goal;
	}

	/** Adds a {@link HuntSurvivorsGoal} to a zombie that hunts survivors and has none yet. */
	public static void attachHunt(PathfinderMob mob) {
		if (!HuntSurvivorsGoal.hunts(mob)) {
			return;
		}
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof HuntSurvivorsGoal) {
				return;
			}
		}
		mob.getGoalSelector().addGoal(PRIORITY, new HuntSurvivorsGoal(mob));
	}

	/** The mob's sense goal, or null when it has none. */
	public static SenseGoal find(PathfinderMob mob) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof SenseGoal sense) {
				return sense;
			}
		}
		return null;
	}
}
