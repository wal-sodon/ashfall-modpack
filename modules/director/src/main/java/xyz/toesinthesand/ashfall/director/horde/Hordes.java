package xyz.toesinthesand.ashfall.director.horde;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.senses.LightSight;
import xyz.toesinthesand.ashfall.director.senses.SenseGoal;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.senses.Stun;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorTracker;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic hordes (plan M8): Director zombies that stand near each other band together behind a leader, the leader
 * drifts towards SOUND, LIGHT and BLOOD it perceives from further away than any single zombie could, the followers
 * keep up, and hordes that meet merge.
 * <p>
 * Why this shape: one horde decision per update (a single wide stimulus query and at most one line-of-sight ray from
 * the leader) replaces dozens of per-zombie decisions, so a big horde costs about what one zombie does. Followers
 * only walk back to their leader when they straggle beyond the leash; within it they keep their own senses, which is
 * what makes a horde spread out over a street and still arrive together.
 * <p>
 * Only AMBIENT and HORDE zombies band together. Building populations stay home (they are the building's dead), and
 * siege zombies have their own route to the core ({@code SiegeRoutes}).
 * <p>
 * Cost: every {@code update_ticks} (40) one pass over the loaded Director zombies (at most 150, the world cap), an
 * O(n²) distance check among the lone ones, and one wide stimulus query per horde.
 */
public final class Hordes {
	/** Below the attack goal (2), above the senses (4): a horde's errand beats one zombie's curiosity. */
	public static final int GOAL_PRIORITY = 3;

	private static final Map<Integer, Horde> HORDES = new LinkedHashMap<>();
	private static final Map<UUID, Horde> BY_MEMBER = new HashMap<>();
	private static int nextId = 1;

	private Hordes() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % HordeSettings.get().updateTicks() == 13) {
				update(server.overworld());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			clear();
			CoreBreakIn.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension() == Level.OVERWORLD && entity instanceof Zombie zombie && eligible(zombie)) {
				attachGoal(zombie);
			}
		});
	}

	/** Whether {@code mob} may be in a horde: a Director zombie spawned by the budget loop or a horde night. */
	public static boolean eligible(Mob mob) {
		DirectorZombie tag = DirectorSpawns.tagOf(mob);
		return tag != null && (tag.source() == DirectorZombie.Source.AMBIENT || tag.source() == DirectorZombie.Source.HORDE);
	}

	/** Gives the zombie its {@link HordeGoal} once (goals are not saved, so this runs on every load). */
	public static HordeGoal attachGoal(Mob mob) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof HordeGoal goal) {
				return goal;
			}
		}
		HordeGoal goal = new HordeGoal(mob);
		mob.getGoalSelector().addGoal(GOAL_PRIORITY, goal);
		return goal;
	}

	/** The horde {@code entity} belongs to, or null. */
	public static @Nullable Horde hordeOf(Entity entity) {
		return BY_MEMBER.get(entity.getUUID());
	}

	/** Every horde (read-only, for the status command and tests). */
	public static Collection<Horde> all() {
		return Collections.unmodifiableCollection(HORDES.values());
	}

	/** The leader of {@code horde} if it is loaded. */
	public static @Nullable Mob leaderMob(Horde horde) {
		return DirectorTracker.get(horde.leader());
	}

	public static void clear() {
		HORDES.clear();
		BY_MEMBER.clear();
	}

	/**
	 * Makes a horde out of {@code mobs} (the first leads), taking them out of any horde they were in. Horde nights
	 * use it for every wave, so a wave arrives as one group with a destination instead of trickling in.
	 */
	public static Horde create(List<? extends Mob> mobs, @Nullable Vec3 destination, @Nullable UUID chase, long until) {
		if (mobs.isEmpty()) {
			throw new IllegalArgumentException("a horde needs a member");
		}
		Horde horde = new Horde(nextId++, mobs.getFirst().getUUID());
		HORDES.put(horde.id(), horde);
		for (Mob mob : mobs) {
			join(horde, mob.getUUID());
			attachGoal(mob);
		}
		horde.setDestination(destination, until);
		horde.setChase(chase);
		return horde;
	}

	private static void join(Horde horde, UUID member) {
		Horde old = BY_MEMBER.put(member, horde);
		if (old != null && old != horde) {
			old.mutableMembers().remove(member);
			if (old.isLeader(member) && !old.mutableMembers().isEmpty()) {
				old.setLeader(old.mutableMembers().iterator().next());
			}
		}
		horde.mutableMembers().add(member);
	}

	private static void disband(Horde horde) {
		HORDES.remove(horde.id());
		for (UUID member : horde.mutableMembers()) {
			BY_MEMBER.remove(member, horde);
		}
	}

	/** One horde update: prune, recruit, form, merge, then pick each horde's destination. Public for gametests. */
	public static void update(ServerLevel level) {
		HordeSettings settings = HordeSettings.get();
		prune();
		List<Mob> lone = new ArrayList<>();
		for (Mob mob : DirectorTracker.loaded()) {
			if (mob.isAlive() && mob.level() == level && !BY_MEMBER.containsKey(mob.getUUID()) && mob.getTarget() == null && eligible(mob)) {
				lone.add(mob);
			}
		}
		recruit(lone, settings);
		form(lone, settings);
		merge(settings);
		for (Horde horde : HORDES.values()) {
			steer(level, horde, settings);
			horde.setBesieging(settings.breakIn().enabled() ? besiegedCore(level, horde) : null);
		}
	}

	/** Drops dead, unloaded and no longer eligible members; hands leadership on; disbands hordes of one. */
	private static void prune() {
		Iterator<Horde> it = HORDES.values().iterator();
		while (it.hasNext()) {
			Horde horde = it.next();
			horde.mutableMembers().removeIf(uuid -> {
				Mob mob = DirectorTracker.get(uuid);
				boolean gone = mob == null || !mob.isAlive() || !eligible(mob);
				if (gone) {
					BY_MEMBER.remove(uuid, horde);
				}
				return gone;
			});
			boolean keep = horde.size() >= 2 || (horde.chase() != null && horde.size() == 1);
			if (!keep) {
				for (UUID member : horde.mutableMembers()) {
					BY_MEMBER.remove(member, horde);
				}
				it.remove();
				continue;
			}
			if (!horde.mutableMembers().contains(horde.leader())) {
				horde.setLeader(horde.mutableMembers().iterator().next());
			}
		}
	}

	/** Lone zombies close to a horde's leader join it (removed from {@code lone}). */
	private static void recruit(List<Mob> lone, HordeSettings settings) {
		if (HORDES.isEmpty()) {
			return;
		}
		Iterator<Mob> it = lone.iterator();
		while (it.hasNext()) {
			Mob mob = it.next();
			Horde best = null;
			double bestDist = Double.MAX_VALUE;
			for (Horde horde : HORDES.values()) {
				Mob leader = leaderMob(horde);
				if (leader == null) {
					continue;
				}
				double d = leader.distanceToSqr(mob);
				if (d < bestDist && HordeGrouping.shouldJoin(d, horde.size(), settings.groupRadius(), settings.maxSize())) {
					best = horde;
					bestDist = d;
				}
			}
			if (best != null) {
				join(best, mob.getUUID());
				it.remove();
			}
		}
	}

	/** Lone zombies standing together become new hordes. */
	private static void form(List<Mob> lone, HordeSettings settings) {
		if (lone.size() < settings.minGroup()) {
			return;
		}
		List<HordeGrouping.Point> points = new ArrayList<>(lone.size());
		for (int i = 0; i < lone.size(); i++) {
			Mob mob = lone.get(i);
			points.add(new HordeGrouping.Point(i, mob.getX(), mob.getY(), mob.getZ()));
		}
		for (List<Integer> group : HordeGrouping.cluster(points, settings.groupRadius(), settings.minGroup(), settings.maxSize())) {
			List<Mob> mobs = new ArrayList<>(group.size());
			for (int index : group) {
				mobs.add(lone.get(index));
			}
			create(mobs, null, null, 0L);
		}
	}

	/** Hordes whose leaders meet merge into the bigger one, which keeps its errand (or takes the smaller's). */
	private static void merge(HordeSettings settings) {
		List<Horde> list = new ArrayList<>(HORDES.values());
		Set<Horde> absorbed = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			Horde a = list.get(i);
			if (absorbed.contains(a)) {
				continue;
			}
			for (int j = i + 1; j < list.size(); j++) {
				Horde b = list.get(j);
				if (absorbed.contains(b)) {
					continue;
				}
				Mob la = leaderMob(a), lb = leaderMob(b);
				if (la == null || lb == null
						|| !HordeGrouping.shouldMerge(la.distanceToSqr(lb), a.size(), b.size(), settings.mergeRadius(), settings.maxSize())) {
					continue;
				}
				Horde big = a.size() >= b.size() ? a : b;
				Horde small = big == a ? b : a;
				mergeInto(big, small);
				absorbed.add(small);
				if (small == a) {
					break;
				}
			}
		}
	}

	private static void mergeInto(Horde big, Horde small) {
		if (big.destination() == null && small.destination() != null) {
			big.setDestination(small.destination(), small.destinationUntil());
		}
		if (big.chase() == null) {
			big.setChase(small.chase());
		}
		HORDES.remove(small.id());
		for (UUID member : List.copyOf(small.mutableMembers())) {
			BY_MEMBER.put(member, big);
			big.mutableMembers().add(member);
		}
		small.mutableMembers().clear();
	}

	/**
	 * Picks where a horde goes: the strongest stimulus its leader perceives at the horde's wider reach, else (on a
	 * horde night) the haven or player it was sent after, else it keeps its last destination until that runs out.
	 */
	static void steer(ServerLevel level, Horde horde, HordeSettings settings) {
		Mob leader = leaderMob(horde);
		if (leader == null) {
			return;
		}
		long now = level.getGameTime();
		Stimulus heard = strongestStimulus(level, horde, leader, settings.stimulusScale());
		if (heard != null) {
			// keep walking a while after the noise fades: it came from somewhere worth a look
			horde.setDestination(new Vec3(heard.x(), heard.y(), heard.z()), heard.expiresAt() + 200);
			return;
		}
		if (horde.chase() != null) {
			Vec3 target = chaseTarget(level, horde.chase(), HordeSchedule.get().spawn().havenRadius());
			if (target != null) {
				horde.setDestination(target, Long.MAX_VALUE);
				return;
			}
			horde.setChase(null); // the player left or logged off: the horde goes back to roaming
		}
		Vec3 dest = horde.destination();
		if (dest != null && (now >= horde.destinationUntil()
				|| leader.distanceToSqr(dest.x, leader.getY(), dest.z) <= settings.arriveRadius() * settings.arriveRadius())) {
			horde.setDestination(null, 0L);
		}
	}

	/**
	 * The stimulus a horde follows: SOUND and BLOOD within radius x scale, LIGHT within the same and seen from the
	 * leader's eyes (curtains still work against hordes). Scored like {@link SenseGoal}: nearest relative to reach.
	 * Nothing while the leader is stunned.
	 */
	static @Nullable Stimulus strongestStimulus(ServerLevel level, Horde horde, Mob leader, double scale) {
		if (Stun.isStunned(leader)) {
			return null; // a stunned leader can't hear or see for the horde
		}
		Vec3 eyes = leader.getEyePosition();
		Set<Integer> memberIds = new HashSet<>();
		for (UUID uuid : horde.mutableMembers()) {
			Mob m = DirectorTracker.get(uuid);
			if (m != null) {
				memberIds.add(m.getId());
			}
		}
		long now = level.getGameTime();
		List<Stimulus> candidates = new ArrayList<>();
		Stimuli.queryWide(level, eyes, scale, candidates::add);
		candidates.sort((x, y) -> Double.compare(score(y, eyes, scale, now), score(x, eyes, scale, now)));
		for (Stimulus s : candidates) {
			if (memberIds.contains(s.sourceId())) {
				continue;
			}
			if (s.type() == StimulusType.LIGHT) {
				double reach = Math.min(s.radius(), SenseGoal.SIGHT_RANGE) * scale;
				if (s.distanceSqr(eyes.x, eyes.y, eyes.z) > reach * reach
						|| !LightSight.canSee(level, eyes, new Vec3(s.x(), s.y(), s.z()))) {
					continue;
				}
			}
			return s;
		}
		return null;
	}

	private static double score(Stimulus s, Vec3 eyes, double scale, long now) {
		double reach = s.radius() * scale;
		double score = 1.0 - Math.sqrt(s.distanceSqr(eyes.x, eyes.y, eyes.z)) / Math.max(1e-6, reach);
		if (s.type() == StimulusType.BLOOD) {
			score += SenseGoal.BLOOD_FRESHNESS * (s.expiresAt() - now) / (double) StimulusType.BLOOD.lifetimeTicks();
		}
		return score;
	}

	/**
	 * Where a horde sent after {@code player} goes: their Bunker Core if they are within {@code havenRadius} of it
	 * (the horde comes for the haven they hide in), else the player. Null if the player is not in this level.
	 */
	/**
	 * The Bunker Core {@code horde} is besieging, or null: a horde-night horde whose destination is the core of the
	 * player it was sent after (that player is within the haven radius of it, see {@link #chaseTarget}). A horde
	 * following a noise, chasing a player away from home, or sent after a player with no core gets null and moves
	 * as it always has.
	 */
	public static @Nullable BlockPos besiegedCore(ServerLevel level, Horde horde) {
		UUID chase = horde.chase();
		Vec3 dest = horde.destination();
		if (chase == null || dest == null) {
			return null;
		}
		BunkerCoreRecord core = BunkerCores.forPlayer(level.getServer(), chase).orElse(null);
		if (core == null || core.dimension() != level.dimension()) {
			return null;
		}
		return Vec3.atBottomCenterOf(core.pos()).distanceToSqr(dest) < 0.01 ? core.pos() : null;
	}

	public static @Nullable Vec3 chaseTarget(ServerLevel level, UUID player, int havenRadius) {
		if (!(level.getPlayerByUUID(player) instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) {
			return null;
		}
		BunkerCoreRecord core = BunkerCores.forPlayer(level.getServer(), player).orElse(null);
		if (core != null && core.dimension() == level.dimension()) {
			BlockPos pos = core.pos();
			if (p.distanceToSqr(Vec3.atCenterOf(pos)) <= (double) havenRadius * havenRadius) {
				return Vec3.atBottomCenterOf(pos);
			}
		}
		return p.position();
	}
}
