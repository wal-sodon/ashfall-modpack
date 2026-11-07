package xyz.toesinthesand.ashfall.director.spawn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loaded Director zombies in the overworld, maintained from entity load/unload events.
 * <p>
 * The hard caps bound this set at {@link SpawnRules#WORLD_HARD_CAP} (plus whatever was saved before a
 * cap change), so a linear distance scan per player per cycle is cheaper than any spatial index would
 * be to maintain as zombies walk around. Unloaded zombies (in far chunks) are not counted, which is
 * the vanilla mob-cap convention: only what is simulated competes for the budget.
 */
public final class DirectorTracker {
	private static final Map<UUID, Mob> LOADED = new LinkedHashMap<>();

	private DirectorTracker() {
	}

	static void add(Mob mob) {
		LOADED.put(mob.getUUID(), mob);
	}

	static void remove(Entity entity) {
		LOADED.remove(entity.getUUID());
	}

	static void clear() {
		LOADED.clear();
	}

	public static Collection<Mob> loaded() {
		return LOADED.values();
	}

	/** The loaded Director zombie with this UUID, or null. */
	public static Mob get(UUID uuid) {
		return LOADED.get(uuid);
	}

	public static boolean isLoaded(UUID uuid) {
		return LOADED.containsKey(uuid);
	}

	public static int total() {
		return LOADED.size();
	}

	/** Counts within {@code radius} of a point: [0] ambient zombies, [1] all Director zombies. */
	public static int[] countNear(double x, double y, double z, double radius) {
		double r2 = radius * radius;
		int ambient = 0, all = 0;
		for (Mob mob : LOADED.values()) {
			if (mob.distanceToSqr(x, y, z) > r2) {
				continue;
			}
			all++;
			DirectorZombie tag = DirectorSpawns.tagOf(mob);
			if (tag != null && tag.source() == DirectorZombie.Source.AMBIENT) {
				ambient++;
			}
		}
		return new int[]{ambient, all};
	}

	/** Loaded Director zombies of {@code source} within {@code radius} of a point (horde-night caps). */
	public static int countNear(double x, double y, double z, double radius, DirectorZombie.Source source) {
		double r2 = radius * radius;
		int n = 0;
		for (Mob mob : LOADED.values()) {
			DirectorZombie tag = DirectorSpawns.tagOf(mob);
			if (tag != null && tag.source() == source && mob.distanceToSqr(x, y, z) <= r2) {
				n++;
			}
		}
		return n;
	}

	/** Loaded count by source, for the status command. */
	public static int count(DirectorZombie.Source source) {
		int n = 0;
		for (Mob mob : LOADED.values()) {
			DirectorZombie tag = DirectorSpawns.tagOf(mob);
			if (tag != null && tag.source() == source) {
				n++;
			}
		}
		return n;
	}
}
