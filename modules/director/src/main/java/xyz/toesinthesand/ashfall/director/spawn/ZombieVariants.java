package xyz.toesinthesand.ashfall.director.spawn;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.Map;
import java.util.TreeMap;

/**
 * Loads {@code data/<ns>/ashfall/zombie_variant/*.json} and picks variants by weight. The map is
 * sorted by id so a given roll always lands on the same variant, which keeps picks reproducible in
 * tests and independent of hash order.
 */
public final class ZombieVariants extends SimpleJsonResourceReloadListener<ZombieVariant> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "zombie_variants");

	private static volatile Map<Identifier, ZombieVariant> variants = Map.of();

	public ZombieVariants() {
		super(ZombieVariant.CODEC, FileToIdConverter.json("ashfall/zombie_variant"));
	}

	@Override
	protected void apply(Map<Identifier, ZombieVariant> loaded, ResourceManager manager, ProfilerFiller profiler) {
		variants = new TreeMap<>(loaded);
		AshfallDirector.LOGGER.info("Loaded {} zombie variants", loaded.size());
	}

	public static Map<Identifier, ZombieVariant> all() {
		return variants;
	}

	public static ZombieVariant get(Identifier id) {
		return variants.get(id);
	}

	/** Looks a variant up by its short name ({@code runner}) in any namespace, ashfall first. */
	public static Map.Entry<Identifier, ZombieVariant> byName(String name) {
		ZombieVariant own = variants.get(Identifier.fromNamespaceAndPath("ashfall", name));
		if (own != null) {
			return Map.entry(Identifier.fromNamespaceAndPath("ashfall", name), own);
		}
		for (Map.Entry<Identifier, ZombieVariant> e : variants.entrySet()) {
			if (e.getKey().getPath().equals(name)) {
				return e;
			}
		}
		return null;
	}

	/**
	 * Weighted pick among variants eligible on {@code day} at a spot of kind {@code spot}, with the
	 * building bias of {@code profile} applied (null outside buildings) and no spawn context.
	 *
	 * @param roll uniform in [0, 1)
	 * @return the chosen entry, or null if nothing is eligible
	 */
	public static Map.Entry<Identifier, ZombieVariant> pick(Map<Identifier, ZombieVariant> from, double roll, int day,
															ZombieVariant.Environment spot, Identifier profile) {
		return pick(from, roll, day, spot, profile, SpawnContext.NONE);
	}

	/**
	 * Weighted pick with the spot's {@link SpawnContext}: variants whose conditions do not hold there are skipped,
	 * and each eligible variant's weight carries its building and zone bias.
	 *
	 * @param roll uniform in [0, 1)
	 * @return the chosen entry, or null if nothing is eligible
	 */
	public static Map.Entry<Identifier, ZombieVariant> pick(Map<Identifier, ZombieVariant> from, double roll, int day,
															ZombieVariant.Environment spot, Identifier profile, SpawnContext ctx) {
		double total = 0;
		for (ZombieVariant v : from.values()) {
			if (v.eligible(day, spot, ctx)) {
				total += v.weightIn(profile, ctx);
			}
		}
		if (total <= 0) {
			return null;
		}
		double target = roll * total;
		Map.Entry<Identifier, ZombieVariant> last = null;
		for (Map.Entry<Identifier, ZombieVariant> e : from.entrySet()) {
			ZombieVariant v = e.getValue();
			if (!v.eligible(day, spot, ctx)) {
				continue;
			}
			double w = v.weightIn(profile, ctx);
			if (w <= 0) {
				continue;
			}
			last = e;
			target -= w;
			if (target < 0) {
				return e;
			}
		}
		return last;
	}

	/** Test hook: replaces the loaded variants (a reload puts the datapack's back). */
	public static void setForTesting(Map<Identifier, ZombieVariant> replacement) {
		variants = new TreeMap<>(replacement);
	}
}
