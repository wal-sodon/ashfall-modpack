package xyz.toesinthesand.ashfall.director.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * Walks a zombie along a {@link SiegeRoutes.Route}: to the waypoint it should head for next, one short leg at a time,
 * until it stands next to the target. A zombie outside starts at the entrance; one already inside picks up the route
 * at the waypoint nearest to it, so a zombie that chased a player through the building rejoins the route instead of
 * walking back out.
 * <p>
 * Never runs while the zombie has a target: a player in the way is attacked first, and the route resumes after.
 */
public class RouteGoal extends Goal {
	/** Close enough to a waypoint to head for the next one. */
	private static final double REACHED_SQR = 1.6 * 1.6;
	/** Close enough to the target to be done (the siege's attack radius is wider). */
	private static final double ARRIVED_SQR = 2.5 * 2.5;
	/** Ticks between re-paths on the same leg (the goal ticks every other game tick). */
	private static final int REPATH_TICKS = 30;
	/**
	 * Least ticks between re-paths when the last path is already done. A zombie battering the control-room wall
	 * never gets within {@link #ARRIVED_SQR} of the core, so every path it gets ends short and "done"; without this
	 * floor it re-pathed every other tick for the whole siege.
	 */
	private static final int DONE_REPATH_TICKS = 10;
	/** Failed paths to one waypoint before it is skipped. */
	private static final int MAX_FAILS = 3;
	public static final double SPEED = 1.0;

	private final PathfinderMob mob;
	private final BlockPos target;
	private List<BlockPos> waypoints = List.of();
	private int index;
	private int ticks;
	private int fails;

	public RouteGoal(PathfinderMob mob, BlockPos target) {
		this.mob = mob;
		this.target = target;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	public BlockPos target() {
		return target;
	}

	/** The waypoint the zombie is heading for (tests and debugging). */
	public BlockPos currentWaypoint() {
		return index < waypoints.size() ? waypoints.get(index) : target;
	}

	@Override
	public boolean canUse() {
		return mob.getTarget() == null && !arrived();
	}

	@Override
	public boolean canContinueToUse() {
		return mob.getTarget() == null && !arrived();
	}

	@Override
	public void start() {
		if (mob.level() instanceof ServerLevel level) {
			waypoints = SiegeRoutes.route(level, target).waypoints();
		}
		index = startIndex();
		ticks = 0;
		fails = 0;
		path();
	}

	@Override
	public void stop() {
		if (mob.getTarget() == null) {
			mob.getNavigation().stop();
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		ticks += 2;
		BlockPos wp = currentWaypoint();
		if (index < waypoints.size() - 1 && mob.distanceToSqr(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5) <= REACHED_SQR) {
			index++;
			fails = 0;
			path();
			return;
		}
		if (ticks >= REPATH_TICKS || (ticks >= DONE_REPATH_TICKS && mob.getNavigation().isDone())) {
			path();
		}
	}

	private void path() {
		ticks = 0;
		BlockPos wp = currentWaypoint();
		boolean ok = mob.getNavigation().moveTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, SPEED);
		if (!ok && ++fails >= MAX_FAILS && index < waypoints.size() - 1) {
			index++; // unreachable from here: try the next leg
			fails = 0;
		}
	}

	private boolean arrived() {
		return mob.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVED_SQR;
	}

	/**
	 * Outside (under open sky) the route starts at the entrance. Inside, it starts at the nearest waypoint, preferring
	 * the later one on a tie so the zombie never turns back towards the door.
	 */
	private int startIndex() {
		if (waypoints.size() <= 1 || !(mob.level() instanceof ServerLevel level)) {
			return 0;
		}
		if (EntranceFinder.outside(level, mob.blockPosition())) {
			return 0;
		}
		int best = 0;
		double bestDist = Double.MAX_VALUE;
		for (int i = 0; i < waypoints.size(); i++) {
			BlockPos p = waypoints.get(i);
			double d = mob.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			if (d <= bestDist) {
				best = i;
				bestDist = d;
			}
		}
		return best;
	}
}
