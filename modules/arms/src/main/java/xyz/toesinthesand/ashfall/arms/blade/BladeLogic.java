package xyz.toesinthesand.ashfall.arms.blade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Plasma Blade's sweep rules, kept free of Minecraft types so JUnit covers them.
 * <p>
 * <b>Why swings are counted per game tick.</b> Better Combat resolves a sweep on the server as one
 * runnable that calls {@code Player.attack} once per target, all in the same tick; vanilla attacks one
 * target per click. So "the hits this player landed this tick" is exactly "the targets of this swing" in
 * both cases, and no Better Combat API (or mixin into it) is needed to cap the sweep. Two separate
 * swings never land in the same tick: the attack cooldown is longer than that.
 * <p>
 * Whether the swing is powered is decided once, at its first target, and kept for the rest, so a blade
 * whose last unit of charge paid for this swing still cuts the 2nd and 3rd target.
 */
public final class BladeLogic {
	/** One swing so far: the tick it happened, targets struck, and whether the blade was lit for it. */
	public record Swing(long tick, int hits, boolean powered) {
		public boolean first() {
			return hits == 1;
		}
	}

	private static final Map<UUID, Swing> SWINGS = new ConcurrentHashMap<>();

	private BladeLogic() {
	}

	/**
	 * Counts one more target for {@code player}'s swing at {@code tick}. A new tick starts a new swing
	 * whose powered flag is {@code poweredIfNew}. @return the swing including this target
	 */
	public static Swing registerHit(UUID player, long tick, boolean poweredIfNew) {
		Swing previous = SWINGS.get(player);
		Swing next = previous == null || previous.tick() != tick
				? new Swing(tick, 1, poweredIfNew)
				: new Swing(tick, previous.hits() + 1, previous.powered());
		SWINGS.put(player, next);
		return next;
	}

	/** {@code player}'s swing at {@code tick}, or null if they have not struck anything this tick. */
	public static Swing current(UUID player, long tick) {
		Swing swing = SWINGS.get(player);
		return swing != null && swing.tick() == tick ? swing : null;
	}

	/** Whether {@code player}'s swing at {@code tick} has already passed its target cap. */
	public static boolean overCap(UUID player, long tick, int maxTargets) {
		Swing swing = current(player, tick);
		return swing != null && swing.hits() > maxTargets;
	}

	/** Drops a player's swing record (on disconnect), so the map holds online players only. */
	public static void forget(UUID player) {
		SWINGS.remove(player);
	}

	/**
	 * Damage share of the {@code index}-th target (1-based): the first takes everything, targets 2..max
	 * take {@code extraMultiplier}, any further target takes nothing.
	 */
	public static float targetMultiplier(int index, int maxTargets, float extraMultiplier) {
		if (index <= 1) {
			return 1f;
		}
		return index <= maxTargets ? extraMultiplier : 0f;
	}

	/** Extra food exhaustion for the {@code index}-th target: only the 2nd..max targets cost hunger. */
	public static float extraExhaustion(int index, int maxTargets, float perTarget) {
		return index >= 2 && index <= maxTargets ? perTarget : 0f;
	}

	/** Crystal integrity after {@code wear} more, never below 0. */
	public static int wearAfter(int integrity, int wear) {
		return Math.max(0, integrity - Math.max(0, wear));
	}

	/**
	 * Damage a blade hit deals. {@code incoming} is what vanilla computed from the item attribute (already
	 * scaled by attack cooldown and strength effects); it is rescaled from the attribute's
	 * {@code attributeDamage} to the datapack's powered or hilt damage, so effects keep their share.
	 */
	public static float strikeDamage(float incoming, float attributeDamage, BladeStats stats, boolean powered, int index,
									 boolean pylon) {
		float base = powered ? stats.damage() : stats.unpoweredDamage();
		float scaled = attributeDamage > 0f ? incoming * base / attributeDamage : base;
		float pylonFactor = powered && pylon ? stats.pylonDamageMultiplier() : 1f;
		return scaled * targetMultiplier(index, stats.maxTargets(), stats.extraTargetMultiplier()) * pylonFactor;
	}
}
