package xyz.toesinthesand.ashfall.director.senses;

import net.minecraft.core.BlockPos;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Zombie senses (spec section 3): walk to what you hear, walk to light you can see, follow blood you smell, and
 * grab the player the moment you lay eyes on them. BLOOD is handled like SOUND (no line of sight, scaled by the
 * variant's hearing as a stand-in for its senses in general), with a bonus for fresh drops so a trail is followed
 * forwards.
 * <p>
 * Cost control: a zombie only looks at the stimulus field once per {@link #SENSE_INTERVAL} ticks,
 * staggered by entity id so a horde doesn't query on the same tick, and the field query itself only
 * visits nearby chunk sections. Light needs a raycast, but only for LIGHT stimuli that already reach
 * the zombie, which is rare.
 * <p>
 * Targeting stays vanilla: this goal only moves the zombie. When it spots a player (or a survivor, see
 * {@link HuntSurvivorsGoal}) on the way it sets that one as its target, and from then on the normal attack goal (higher priority) takes over and
 * this goal stands down because it never runs while the zombie has a target.
 */
public class SenseGoal extends Goal {
	/** Ticks between stimulus checks. */
	public static final int SENSE_INTERVAL = 20;
	/** How far zombies can make out a light at all, whatever its radius. */
	public static final double SIGHT_RANGE = 32.0;
	/** Walk speed modifier while investigating (1.0 = the zombie's normal walk). */
	public static final double INVESTIGATE_SPEED = 1.0;
	/** Give up an investigation after this long even if the path never finishes. */
	public static final int MAX_INVESTIGATE_TICKS = 400;
	/** How far a player is spotted during an investigation; capped by the zombie's follow range. */
	public static final double SPOT_RANGE = 24.0;
	/** How much a fresh BLOOD drop outranks an old one of the same distance (0 = distance only). */
	public static final double BLOOD_FRESHNESS = 0.5;
	/** Close enough to the stimulus to call the investigation done. */
	private static final double ARRIVED_SQR = 1.5 * 1.5;
	/** A stimulus this close to the last one investigated is the same thing; don't walk there again. */
	private static final double SAME_SPOT_SQR = 3.0 * 3.0;
	/**
	 * A better stimulus within this distance of the one being walked to keeps the current path. Emitters
	 * refresh their stimulus as a new object every few ticks (a lit torch, a running generator, footsteps on
	 * the same spot), and re-pathing to each copy ran a fresh path search per zombie per sense tick for as long
	 * as the source stayed put; in Lost Cities interiors many of those searches fail and use their whole node
	 * budget.
	 */
	private static final double SAME_TARGET_SQR = 2.0 * 2.0;

	private final PathfinderMob mob;
	private final double hearing;
	@Nullable
	private Stimulus current;
	private int investigateTicks;

	/**
	 * @param hearing multiplier on SOUND radii for this mob (variant hearing; 1.0 for a plain zombie),
	 *                clamped to {@link StimulusField#MAX_REACH_SCALE}
	 */
	public SenseGoal(PathfinderMob mob, double hearing) {
		this.mob = mob;
		this.hearing = Math.min(hearing, StimulusField.MAX_REACH_SCALE);
		setFlags(EnumSet.of(Flag.MOVE));
	}

	public double hearing() {
		return hearing;
	}

	@Override
	public boolean canUse() {
		if (!onSenseTick() || mob.getTarget() != null) {
			return false;
		}
		Stimulus best = pickStimulus();
		if (best == null) {
			return false;
		}
		current = best;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return current != null && mob.getTarget() == null && investigateTicks < MAX_INVESTIGATE_TICKS
				&& !(mob.getNavigation().isDone() && arrived());
	}

	@Override
	public void start() {
		investigateTicks = 0;
		if (!walkTo(current)) {
			current = null;
		}
	}

	@Override
	public void stop() {
		current = null;
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
		// non-every-tick goals tick every other game tick
		investigateTicks += 2;
		if (investigateTicks % 10 == 0 && spotPlayer()) {
			return;
		}
		if (!onSenseTick()) {
			return;
		}
		// a louder or newer stimulus may have come up on the way: switch to it
		Stimulus better = pickStimulus();
		if (better != null && better != current) {
			// compare with where the path actually leads, not the last stimulus, or a slowly moving source
			// (sneaking footsteps, a blood trail) would count as "same place" forever and never re-path
			BlockPos target = mob.getNavigation().getTargetPos();
			boolean samePlace = target != null
					&& better.distanceSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= SAME_TARGET_SQR;
			current = better;
			if (!samePlace || mob.getNavigation().isDone()) {
				walkTo(better);
			}
		} else if (current != null && mob.getNavigation().isDone() && !arrived() && !walkTo(current)) {
			current = null; // unreachable for now; canContinueToUse ends the investigation
		}
	}

	/**
	 * True on one goal-selector pass per {@link #SENSE_INTERVAL} ticks, staggered by entity id. The
	 * selector only evaluates goals on every other tick, hence the two-tick window.
	 */
	private boolean onSenseTick() {
		return Math.floorMod(mob.tickCount + mob.getId(), SENSE_INTERVAL) < 2;
	}

	/**
	 * The stimulus this zombie would investigate now, or null: the strongest one relative to its reach
	 * (1 at the source, 0 at the edge), SOUND within hearing, LIGHT only within {@link #SIGHT_RANGE}
	 * and with a clear line of sight. A stunned zombie ({@link Stun}) senses nothing. Public so tests can check the
	 * decision without walking.
	 */
	@Nullable
	public Stimulus pickStimulus() {
		if (!(mob.level() instanceof ServerLevel level) || Stun.isStunned(mob)) {
			return null;
		}
		Vec3 eyes = mob.getEyePosition();
		long now = level.getGameTime();
		Stimulus[] best = {null};
		double[] bestScore = {0.0};
		// query with at least the full radius: hearing only scales SOUND, and a dull-eared variant
		// (hearing < 1) must still see a LIGHT out to its radius; SOUND reach is re-checked below
		Stimuli.query(level, eyes, Math.max(hearing, 1.0), s -> {
			if (s.sourceId() == mob.getId()) {
				return;
			}
			double distSqr = s.distanceSqr(eyes.x, eyes.y, eyes.z);
			if (current == null && isNearLastSpot(s)) {
				return; // just investigated this spot and found nothing; don't stand on it forever
			}
			double reach;
			if (s.type() == StimulusType.LIGHT) {
				reach = Math.min(s.radius(), SIGHT_RANGE);
				if (distSqr > reach * reach) {
					return;
				}
			} else {
				reach = s.radius() * hearing;
				if (distSqr > reach * reach) {
					return;
				}
			}
			double score = 1.0 - Math.sqrt(distSqr) / reach;
			if (s.type() == StimulusType.BLOOD) {
				// A trail is a row of drops: prefer the freshest, which lies further along towards the wounded player,
				// so the zombie walks the trail forwards instead of back to where the bleeding started.
				score += BLOOD_FRESHNESS * (s.expiresAt() - now) / (double) StimulusType.BLOOD.lifetimeTicks();
			}
			if (score <= bestScore[0] && best[0] != null) {
				return;
			}
			if (s.type() == StimulusType.LIGHT && !LightSight.canSee(level, eyes, new Vec3(s.x(), s.y(), s.z()))) {
				return;
			}
			best[0] = s;
			bestScore[0] = score;
		});
		return best[0];
	}

	private double lastX = Double.NaN;
	private double lastY;
	private double lastZ;

	private boolean isNearLastSpot(Stimulus s) {
		return !Double.isNaN(lastX) && s.distanceSqr(lastX, lastY, lastZ) <= SAME_SPOT_SQR
				&& mob.distanceToSqr(lastX, lastY, lastZ) <= SAME_SPOT_SQR;
	}

	private boolean walkTo(Stimulus s) {
		lastX = s.x();
		lastY = s.y();
		lastZ = s.z();
		return mob.getNavigation().moveTo(s.x(), s.y(), s.z(), INVESTIGATE_SPEED);
	}

	private boolean arrived() {
		return current == null || mob.distanceToSqr(current.x(), mob.getY(), current.z()) <= ARRIVED_SQR;
	}

	/**
	 * Targets the nearest visible, attackable player, or a survivor when one is nearer (plain zombies only, see
	 * {@link HuntSurvivorsGoal#hunts}), handing over to the vanilla attack goal.
	 */
	private boolean spotPlayer() {
		double range = Math.min(SPOT_RANGE, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
		LivingEntity prey = mob.level().getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), range,
				e -> e instanceof Player p && !p.isSpectator() && !p.isCreative() && mob.canAttack(p)
						&& mob.getSensing().hasLineOfSight(p));
		if (HuntSurvivorsGoal.hunts(mob)) {
			double within = prey == null ? range : mob.distanceTo(prey);
			LivingEntity survivor = HuntSurvivorsGoal.nearestSurvivor(mob, within);
			if (survivor != null) {
				prey = survivor;
			}
		}
		if (prey == null) {
			return false;
		}
		mob.setTarget(prey);
		return true;
	}
}
