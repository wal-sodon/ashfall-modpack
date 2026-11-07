package xyz.toesinthesand.ashfall.director.horde;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Moves one horde member: the leader walks to the horde's destination, a follower that straggled beyond the leash
 * walks back to the leader. Everything else (who leads, where the horde goes) is decided by {@link Hordes}; this
 * goal only reads it, so it stays cheap enough to check on every goal pass.
 * <p>
 * It never runs while the zombie has a target, so the attack goal always wins, and a follower inside the leash is
 * left to its own senses ({@code SenseGoal}), which spreads a horde out naturally instead of marching in file.
 */
public class HordeGoal extends Goal {
	/** Followers hurry a little to catch up with their leader. */
	public static final double FOLLOW_SPEED = 1.15;
	public static final double LEAD_SPEED = 1.0;
	/** Ticks between re-paths while the goal runs (the goal ticks every other game tick). */
	private static final int REPATH_TICKS = 40;
	/**
	 * Least ticks between re-paths when the last path is already done. A leader whose destination can't be reached
	 * (inside a building, across a gap) ends every path short and "done", and a follower cut off from its leader
	 * the same; without this floor each of them ran a long-range path search every other tick for as long as the
	 * horde lived.
	 */
	private static final int DONE_REPATH_TICKS = 10;
	/**
	 * Ceiling for the done-path floor while paths keep failing to reach. Each failed long search spends the
	 * zombie's whole node budget, and a leader parked against an unreachable destination (or a follower walled
	 * off from it) used to pay that every 10 ticks; the floor doubles per unreachable path up to this, and drops
	 * back to {@link #DONE_REPATH_TICKS} as soon as a path reaches.
	 */
	private static final int MAX_DONE_REPATH_TICKS = 80;

	private final Mob mob;
	private int ticks;
	private int doneRepathTicks = DONE_REPATH_TICKS;

	public HordeGoal(Mob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return mob.getTarget() == null && wanted(1.0) != null;
	}

	@Override
	public boolean canContinueToUse() {
		// hysteresis: a follower walks until it is well inside the leash, not just back on its edge
		return mob.getTarget() == null && wanted(0.5) != null;
	}

	@Override
	public void start() {
		ticks = 0;
		doneRepathTicks = DONE_REPATH_TICKS;
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
		if (ticks >= REPATH_TICKS || (ticks >= doneRepathTicks && mob.getNavigation().isDone())) {
			ticks = 0;
			path();
		}
	}

	private void path() {
		Vec3 to = wanted(0.5);
		if (to != null) {
			Horde horde = Hordes.hordeOf(mob);
			boolean leading = horde != null && horde.isLeader(mob.getUUID());
			Path path = mob.getNavigation().createPath(to.x, to.y, to.z, 1);
			mob.getNavigation().moveTo(path, leading ? LEAD_SPEED : FOLLOW_SPEED);
			doneRepathTicks = path != null && path.canReach() ? DONE_REPATH_TICKS : Math.min(MAX_DONE_REPATH_TICKS, doneRepathTicks * 2);
		}
	}

	/**
	 * Where this member should be walking, or null when it is free: the destination for a leader not yet there, the
	 * leader's position for a follower further than {@code leashFactor} x the leash.
	 */
	private @Nullable Vec3 wanted(double leashFactor) {
		Horde horde = Hordes.hordeOf(mob);
		if (horde == null) {
			return null;
		}
		HordeSettings settings = HordeSettings.get();
		if (horde.isLeader(mob.getUUID())) {
			Vec3 dest = horde.destination();
			if (dest == null) {
				return null;
			}
			double arrive = settings.arriveRadius() * leashFactor;
			return mob.distanceToSqr(dest.x, mob.getY(), dest.z) > arrive * arrive ? dest : null;
		}
		Mob leader = Hordes.leaderMob(horde);
		if (leader == null) {
			return null;
		}
		double leash = settings.leash() * leashFactor;
		return mob.distanceToSqr(leader) > leash * leash ? leader.position() : null;
	}
}
