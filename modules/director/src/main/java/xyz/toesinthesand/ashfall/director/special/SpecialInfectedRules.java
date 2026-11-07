package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Loads {@code data/<ns>/ashfall/special_infected/*.json} (one file per {@link SpecialKind}) and picks a special
 * for a Director spawn. Kept free of world types apart from the loader itself, so the pick is unit tested.
 */
public final class SpecialInfectedRules extends SimpleJsonResourceReloadListener<SpecialInfectedRule> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "special_infected");

	private static volatile Map<SpecialKind, SpecialInfectedRule> rules = Map.of();

	public SpecialInfectedRules() {
		super(SpecialInfectedRule.CODEC, FileToIdConverter.json("ashfall/special_infected"));
	}

	@Override
	protected void apply(Map<Identifier, SpecialInfectedRule> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<SpecialKind, SpecialInfectedRule> byKind = new EnumMap<>(SpecialKind.class);
		for (Map.Entry<Identifier, SpecialInfectedRule> e : loaded.entrySet()) {
			SpecialKind kind = SpecialKind.byId(e.getKey().getPath());
			if (kind == null) {
				AshfallDirector.LOGGER.warn("special_infected/{}.json names no known special; ignored", e.getKey().getPath());
				continue;
			}
			byKind.put(kind, e.getValue());
		}
		rules = byKind;
		AshfallDirector.LOGGER.info("Loaded {} special infected rules", byKind.size());
	}

	public static Map<SpecialKind, SpecialInfectedRule> all() {
		return rules;
	}

	/** The kind's rule, or {@link SpecialInfectedRule#NONE} when no file is loaded for it. */
	public static SpecialInfectedRule get(SpecialKind kind) {
		return rules.getOrDefault(kind, SpecialInfectedRule.NONE);
	}

	/** Test hook: replaces the loaded rules (a reload puts the datapack's back). */
	public static void setForTesting(Map<SpecialKind, SpecialInfectedRule> replacement) {
		rules = new EnumMap<>(replacement);
	}

	/**
	 * Whether this spawn becomes a special, and which. Walks the kinds in declaration order, so a given roll always
	 * lands on the same kind; the part of the 1000 not covered by any weight means "no special" and the caller
	 * falls back to the ordinary variant table. A kind at its cap is skipped as if its weight were 0, which keeps
	 * the other kinds' rates unchanged.
	 *
	 * @param roll       uniform in [0, 1)
	 * @param siege      true for siege waves and horde nights: each rule's siege multiplier applies
	 * @param underCap   whether a kind still has room near the spawn
	 * @return the kind, or null for an ordinary zombie
	 */
	public static SpecialKind pick(Map<SpecialKind, SpecialInfectedRule> from, double roll, int day, String zone, boolean siege,
								   Predicate<SpecialKind> underCap) {
		double target = roll * SpecialInfectedRule.PER;
		for (SpecialKind kind : SpecialKind.values()) {
			SpecialInfectedRule rule = from.get(kind);
			if (rule == null) {
				continue;
			}
			double w = rule.weightFor(day, zone, siege ? rule.siegeMultiplier() : 1f);
			if (w <= 0 || !underCap.test(kind)) {
				continue;
			}
			target -= w;
			if (target < 0) {
				return kind;
			}
		}
		return null;
	}
}
