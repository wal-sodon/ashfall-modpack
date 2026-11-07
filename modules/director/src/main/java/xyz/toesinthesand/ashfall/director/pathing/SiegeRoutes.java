package xyz.toesinthesand.ashfall.director.pathing;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.DoorBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leads siege and Breach zombies to a target inside a building (a Substation Core) through the building's
 * entrance: first to the nearest door or ground-level opening ({@link EntranceFinder}), then along the way the flood
 * found from there to the core, in short legs. This is the fix for "some siege zombies stand in the cellar, on other
 * roofs or in the street" from the Phase 3 report.
 * <p>
 * Routes are cached per target for {@link #CACHE_TICKS}: every zombie of a wave shares one flood, and the cache is
 * short enough that a door a player blocks or a wall a zombie breaks is noticed within half a minute.
 */
public final class SiegeRoutes {
	/** Below the attack goal (2), above senses (4), same as the horde goal (siege zombies never join hordes). */
	public static final int GOAL_PRIORITY = 3;
	/** How long a computed route is reused. */
	public static final long CACHE_TICKS = 600;
	/** Steps between waypoints along the inside of the building. */
	public static final int WAYPOINT_SPACING = 6;

	/** A route into a building: waypoints from outside the entrance to the target, the last one next to it. */
	public record Route(BlockPos target, List<BlockPos> waypoints, BlockPos entrance, long computedAt) {
		/** True when the target is in the open and a zombie can just walk to it. */
		public boolean direct() {
			return waypoints.size() <= 1;
		}
	}

	private static final Map<Long, Route> CACHE = new HashMap<>();

	private SiegeRoutes() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHE.clear());
	}

	/** The route into the building around {@code target}, computed or from the cache. */
	public static Route route(ServerLevel level, BlockPos target) {
		long now = level.getGameTime();
		Route cached = CACHE.get(target.asLong());
		if (cached != null && now - cached.computedAt() < CACHE_TICKS && now >= cached.computedAt()) {
			return cached;
		}
		Route route = compute(level, target, now);
		CACHE.put(target.asLong(), route);
		return route;
	}

	/** Forgets cached routes (tests, or after a building changed). */
	public static void invalidate() {
		CACHE.clear();
	}

	static Route compute(ServerLevel level, BlockPos target, long now) {
		EntranceFinder.Result found = EntranceFinder.find(level, target);
		if (found.outdoors()) {
			return new Route(target, List.of(target), found.exit(), now);
		}
		List<BlockPos> inward = new ArrayList<>(found.path());
		java.util.Collections.reverse(inward); // exit -> core
		List<BlockPos> waypoints = new ArrayList<>();
		for (int i = 0; i < inward.size(); i++) {
			BlockPos p = inward.get(i);
			boolean door = level.getBlockState(p).getBlock() instanceof DoorBlock;
			boolean turn = i > 0 && i < inward.size() - 1 && inward.get(i - 1).getY() != inward.get(i + 1).getY();
			if (i == 0 || i == inward.size() - 1 || door || turn || i % WAYPOINT_SPACING == 0) {
				waypoints.add(p);
			}
		}
		waypoints.add(target);
		return new Route(target, List.copyOf(waypoints), found.exit(), now);
	}

	/**
	 * Gives {@code mob} a route to {@code target} (replacing a route to somewhere else). Idempotent: a mob already led
	 * to the same target keeps its progress. @return the mob's route goal
	 */
	public static RouteGoal assign(PathfinderMob mob, BlockPos target) {
		RouteGoal existing = find(mob);
		if (existing != null) {
			if (existing.target().equals(target)) {
				return existing;
			}
			mob.getGoalSelector().removeGoal(existing);
		}
		RouteGoal goal = new RouteGoal(mob, target);
		mob.getGoalSelector().addGoal(GOAL_PRIORITY, goal);
		return goal;
	}

	/** The mob's route goal, or null. */
	public static RouteGoal find(PathfinderMob mob) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof RouteGoal route) {
				return route;
			}
		}
		return null;
	}
}
