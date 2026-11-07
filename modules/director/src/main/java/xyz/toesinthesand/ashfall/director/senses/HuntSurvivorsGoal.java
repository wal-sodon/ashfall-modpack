package xyz.toesinthesand.ashfall.director.senses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.haven.RespawnChoice;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;

import java.util.EnumSet;

/**
 * Zombies hunt survivors as they hunt players: a plain zombie ({@link #hunts}) goes for whichever is nearest among the
 * players and survivors ({@link Friendly#isSurvivor}) it can see, and the vanilla attack goal does the rest.
 * <p>
 * How it fits vanilla targeting, which is where the old version lost: the zombie's own
 * {@code NearestAttackableTargetGoal<Player>} (target selector, priority 2) started whenever a player was in sight and
 * simply replaced a survivor set from outside. Rather than replacing that goal (Drowned build theirs with their own
 * predicate, and {@code Mob} keeps the target selector protected), {@code NearestAttackableTargetGoalMixin} hooks its
 * {@code findTarget}: for a hunting zombie the goal's pick goes through {@link #choose}, so the player goal itself may
 * pick a survivor, or adopt the survivor the zombie is already chasing. A survivor target is then owned by a running
 * target goal, so the lower villager and golem goals can't take it either, and stun, respawn protection and
 * {@code HurtByTargetGoal} keep working unchanged.
 * <p>
 * The vanilla goal never looks again while it runs, so this goal (goal selector, no flags, never blocks moving or
 * looking) looks around every {@link #INTERVAL} ticks: an idle zombie takes the nearest survivor, and a zombie chasing a
 * player or survivor switches to the other kind only when {@link #choose} says so. {@link #SWITCH_MARGIN} keeps it from
 * flip-flopping between two prey at nearly the same distance. A target that is neither (an iron golem, whoever hit it)
 * is left alone.
 * <p>
 * Specials (Stalker, Screamer and the rest) keep their player-only behaviour. Hiding holdouts and away scavengers are
 * never prey: {@code Survivor#canBeSeenAsEnemy} makes {@code canAttack} refuse them. A stunned zombie runs no AI at all.
 * Cost: one small entity box query and one player scan per zombie per {@link #INTERVAL} ticks, staggered by entity id.
 */
public class HuntSurvivorsGoal extends Goal {
	/** Ticks between looks around (the selector polls every other tick, hence the two-tick window). */
	public static final int INTERVAL = 20;
	/** A zombie only gives up its prey for another this many blocks nearer (or when its prey is lost). */
	public static final double SWITCH_MARGIN = 4.0;

	private final PathfinderMob mob;
	private @Nullable LivingEntity prey;

	public HuntSurvivorsGoal(PathfinderMob mob) {
		this.mob = mob;
		setFlags(EnumSet.noneOf(Flag.class));
	}

	/** Whether {@code mob} goes after survivors at all: every zombie except the specials. */
	public static boolean hunts(PathfinderMob mob) {
		return !(mob instanceof SpecialInfectedEntity);
	}

	/** {@link #hunts} for any mob: the zombies {@link SenseGoals} equips (no zombified piglins), minus the specials. */
	public static boolean huntsSurvivors(Mob mob) {
		return mob instanceof Zombie zombie && !(mob instanceof ZombifiedPiglin) && hunts(zombie);
	}

	/** The nearest living survivor {@code mob} can see within {@code range} (and its follow range), or null. */
	public static @Nullable LivingEntity nearestSurvivor(PathfinderMob mob, double range) {
		double reach = Math.min(range, Math.min(SenseGoal.SPOT_RANGE, mob.getAttributeValue(Attributes.FOLLOW_RANGE)));
		LivingEntity best = null;
		double bestSqr = reach * reach;
		for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach),
				e -> e.isAlive() && Friendly.isSurvivor(e))) {
			double d = mob.distanceToSqr(e);
			if (d <= bestSqr && mayHunt(mob, e)) {
				best = e;
				bestSqr = d;
			}
		}
		return best;
	}

	/**
	 * The nearest player {@code mob} would go for: in sight within its follow range, attackable, not in creative or
	 * spectator, not under respawn protection. Mirrors what the vanilla player target goal accepts.
	 */
	public static @Nullable Player nearestPlayer(Mob mob) {
		double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
		return mob.level().getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), range,
				e -> e instanceof Player p && !p.isSpectator() && !p.isCreative() && !isProtectedFrom(mob, p)
						&& mayHunt(mob, p));
	}

	/**
	 * What a hunting zombie should be after, given its {@code current} target and the nearest {@code player} and
	 * {@code survivor} on offer (either may be null): the nearest of them, except that a current player or survivor it
	 * can still see and reach is kept unless the other is at least {@link #SWITCH_MARGIN} blocks nearer. Null when
	 * there is nothing to hunt.
	 */
	public static @Nullable LivingEntity choose(Mob mob, @Nullable LivingEntity current, @Nullable LivingEntity player,
			@Nullable LivingEntity survivor) {
		LivingEntity nearest = player == null ? survivor
				: survivor == null || mob.distanceToSqr(player) <= mob.distanceToSqr(survivor) ? player : survivor;
		if (current == null || current == nearest || !isPrey(current) || !stillHunting(mob, current)) {
			return nearest;
		}
		if (nearest == null) {
			return current;
		}
		return mob.distanceTo(nearest) + SWITCH_MARGIN <= mob.distanceTo(current) ? nearest : current;
	}

	/** A player or a survivor: the prey {@link #choose} weighs against each other. */
	public static boolean isPrey(LivingEntity entity) {
		return entity instanceof Player || Friendly.isSurvivor(entity);
	}

	/** Whether {@code mob} can keep chasing {@code target}: alive, attackable, in sight and within its follow range. */
	private static boolean stillHunting(Mob mob, LivingEntity target) {
		double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
		return target.isAlive() && mob.distanceToSqr(target) <= range * range && !isProtectedFrom(mob, target)
				&& mayHunt(mob, target);
	}

	private static boolean mayHunt(Mob mob, LivingEntity target) {
		return mob.canAttack(target) && mob.getSensing().hasLineOfSight(target)
				&& (!(mob instanceof Drowned drowned) || drowned.okTarget(target));
	}

	/** False for a player under respawn protection from {@code mob}, whom {@code MobTargetMixin} would refuse anyway. */
	public static boolean mayChase(Mob mob, LivingEntity target) {
		return !isProtectedFrom(mob, target);
	}

	private static boolean isProtectedFrom(Mob mob, LivingEntity target) {
		return target instanceof ServerPlayer player && RespawnChoice.isProtected(player) && RespawnChoice.affectedBy(mob);
	}

	@Override
	public boolean canUse() {
		if (Math.floorMod(mob.tickCount + mob.getId(), INTERVAL) >= 2) {
			return false;
		}
		LivingEntity current = mob.getTarget();
		if (current != null && !isPrey(current)) {
			return false;
		}
		LivingEntity survivor = nearestSurvivor(mob, SenseGoal.SPOT_RANGE);
		if (current == null && survivor == null) {
			return false;
		}
		LivingEntity pick = choose(mob, current, nearestPlayer(mob), survivor);
		// players are found by the vanilla goal; this one only adds survivors and swaps between the two kinds
		boolean swap = pick != null && pick != current
				&& !(pick instanceof Player && (current == null || current instanceof Player));
		prey = swap ? pick : null;
		return swap;
	}

	@Override
	public void start() {
		if (prey != null) {
			mob.setTarget(prey);
		}
		prey = null;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}
}
