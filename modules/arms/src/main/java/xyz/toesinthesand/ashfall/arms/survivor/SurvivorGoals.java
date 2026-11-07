package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import xyz.toesinthesand.ashfall.director.spawn.ZombiesOnly;

import java.util.EnumSet;

/**
 * The survivor's own goals. The rest of its AI is vanilla (float, door opening, fleeing, looking around); see
 * {@link Survivor#registerGoals} for the order.
 */
final class SurvivorGoals {
	private SurvivorGoals() {
	}

	/**
	 * Walks after the leader like a pet that never teleports: it starts when the leader is more than
	 * {@link #START_DISTANCE} away, stops within {@link #STOP_DISTANCE}, hurries past {@link #CATCH_UP_DISTANCE}, and
	 * waits where it is when the leader is more than {@link #GIVE_UP_DISTANCE} off or in another dimension (the mode
	 * stays FOLLOW, so it picks up again when they come back).
	 */
	static final class FollowLeader extends Goal {
		static final double START_DISTANCE = 5.0, STOP_DISTANCE = 2.5, CATCH_UP_DISTANCE = 12.0, GIVE_UP_DISTANCE = 64.0;
		static final double WALK = 1.0, HURRY = 1.3;

		private final Survivor survivor;
		private Player leader;
		private int repath;

		FollowLeader(Survivor survivor) {
			this.survivor = survivor;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (survivor.mode() != SurvivorMode.FOLLOW) {
				return false;
			}
			Player player = survivor.leaderPlayer();
			if (player == null || player.isSpectator()) {
				return false;
			}
			double d = survivor.distanceToSqr(player);
			if (d < START_DISTANCE * START_DISTANCE || d > GIVE_UP_DISTANCE * GIVE_UP_DISTANCE) {
				return false;
			}
			leader = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			if (survivor.mode() != SurvivorMode.FOLLOW || leader == null || !leader.isAlive() || leader.isSpectator()
					|| leader.level() != survivor.level()) {
				return false;
			}
			double d = survivor.distanceToSqr(leader);
			return d > STOP_DISTANCE * STOP_DISTANCE && d < GIVE_UP_DISTANCE * GIVE_UP_DISTANCE;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			leader = null;
			survivor.getNavigation().stop();
		}

		@Override
		public void tick() {
			survivor.getLookControl().setLookAt(leader, 10.0f, survivor.getMaxHeadXRot());
			if (--repath <= 0) {
				repath = adjustedTickDelay(10);
				double speed = survivor.distanceToSqr(leader) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE ? HURRY : WALK;
				survivor.getNavigation().moveTo(leader, speed);
			}
		}
	}

	/** Holds the spot it was told to stay at, walking back to it after a fight or a scare. */
	static final class StayPut extends Goal {
		private final Survivor survivor;
		private int recheck;

		StayPut(Survivor survivor) {
			this.survivor = survivor;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return survivor.mode() == SurvivorMode.STAY;
		}

		@Override
		public void start() {
			survivor.getNavigation().stop();
			recheck = 0;
		}

		@Override
		public void tick() {
			if (--recheck > 0) {
				return;
			}
			recheck = adjustedTickDelay(20);
			BlockPos spot = survivor.stayPos();
			if (spot != null && survivor.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 2.25 && survivor.getNavigation().isDone()) {
				survivor.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, FollowLeader.WALK);
			}
		}
	}

	/** Wanders the home base when settled ({@link SurvivorMode#HOME}); the vanilla home restriction keeps it inside. */
	static final class HomeStroll extends WaterAvoidingRandomStrollGoal {
		private final Survivor survivor;

		HomeStroll(Survivor survivor) {
			super(survivor, 0.8);
			this.survivor = survivor;
		}

		@Override
		public boolean canUse() {
			return survivor.mode() == SurvivorMode.HOME && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return survivor.mode() == SurvivorMode.HOME && super.canContinueToUse();
		}
	}

	/**
	 * A guard's swing: vanilla melee, only while the survivor is a Guard, with the time between swings stretched by
	 * {@link SurvivorWork#workSpeed} (a hungry or new guard swings half as often).
	 */
	static final class GuardMelee extends MeleeAttackGoal {
		/** Vanilla's ticks between melee swings. */
		static final int SWING_TICKS = 20;
		private final Survivor survivor;
		private int nextSwing;

		GuardMelee(Survivor survivor) {
			super(survivor, 1.2, true);
			this.survivor = survivor;
		}

		@Override
		public boolean canUse() {
			return survivor.role() == SurvivorRole.GUARD && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return survivor.role() == SurvivorRole.GUARD && super.canContinueToUse();
		}

		@Override
		protected boolean isTimeToAttack() {
			return super.isTimeToAttack() && survivor.tickCount >= nextSwing;
		}

		@Override
		protected void resetAttackCooldown() {
			super.resetAttackCooldown();
			nextSwing = survivor.tickCount + (int) Math.ceil(SWING_TICKS / SurvivorWork.workSpeed(survivor));
		}
	}

	/**
	 * A guard picks the nearest zombie-family mob it can see within {@link Survivor#GUARD_RANGE} of itself and keeps it
	 * only while it stays that close: guards hold their ground instead of chasing a horde down the street.
	 */
	static final class GuardTarget extends NearestAttackableTargetGoal<Mob> {
		GuardTarget(Survivor survivor) {
			super(survivor, Mob.class, 10, true, false, (target, level) -> isZombieTarget(survivor, target));
		}

		@Override
		protected double getFollowDistance() {
			return Survivor.GUARD_RANGE;
		}
	}

	static boolean isZombieTarget(Survivor survivor, LivingEntity target) {
		return survivor.role() == SurvivorRole.GUARD && ZombiesOnly.isZombieFamily(target)
				&& survivor.distanceToSqr(target) <= Survivor.GUARD_RANGE * Survivor.GUARD_RANGE;
	}
}
