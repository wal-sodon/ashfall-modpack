package xyz.toesinthesand.ashfall.salvage.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data-driven description of one kind of building ({@code data/<ns>/ashfall/building_profile/<id>.json}):
 * how it is recognised, how much loot of which tier it holds, and how it refills.
 *
 * @param match       tag rules, any of which classifies the whole building. A rule matches when every key
 *                    matches; a value may list alternatives with '|', and each alternative may use the globs
 *                    '*' (any run of characters) and '?' (one character). Lost Cities buildings carry
 *                    {@code lc_building} (building id at the origin chunk, e.g. "lcmt:building5"),
 *                    {@code lc_multibuilding} (e.g. "lcmt:townhall"), {@code lc_zone} (our zone, e.g.
 *                    "downtown") and {@code lc_style}, so {@code {"lc_building": "*:building?", "lc_zone":
 *                    "downtown"}} reads "any numbered tower downtown". Arnis buildings carry their OSM tags
 *                    (amenity=police, ...).
 * @param arnisUses   unit uses classified per unit, exact ("Shop(Pharmacy)") or family ("Shop"): Arnis
 *                    furnishing uses, or "Floor"/"Cellar" for Lost Cities storeys
 * @param priority    higher wins when several profiles match
 * @param tier        1 (common) .. 5 (military-grade); decides what the loot table rolls
 * @param lootTable   table rolled into every container; empty = leave containers empty
 * @param containers  how many containers a unit gets and which blocks they are
 * @param refill      when shared containers refill
 * @param population  zombie weights for the building's one-time population (used by the director)
 * @param safeCandidate whether admins are offered this building as a safe zone
 * @param relayChance chance that an Arnis building gets a Power Relay; unused for Lost Cities, whose relays go
 *                    on streets by a fixed plan ({@code RelayPlan})
 * @param cacheTable  if present, one extra chest per building rolls this table per player: Lootr
 *                    converts it because its namespace (ashfall_cache) is not on Lootr's blacklist
 */
public record BuildingProfile(List<Map<String, String>> match, List<String> arnisUses, int priority, int tier,
							  Optional<Identifier> lootTable, Containers containers, Refill refill,
							  Population population, boolean safeCandidate, float relayChance,
							  Optional<Identifier> cacheTable) {

	public record ContainerBlock(Identifier block, int weight) {
		public static final Codec<ContainerBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("block").forGetter(ContainerBlock::block),
				Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(ContainerBlock::weight)
		).apply(i, ContainerBlock::new));
	}

	/**
	 * @param per100m2 containers per 100 floor cells of a unit (a Lost Cities storey has 14 x 14 = 196 inside one chunk)
	 */
	public record Containers(float per100m2, int min, int max, List<ContainerBlock> blocks) {
		public static final Containers NONE = new Containers(0, 0, 0, List.of());
		public static final Codec<Containers> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0, 100).optionalFieldOf("per_100m2", 0f).forGetter(Containers::per100m2),
				Codec.intRange(0, 64).optionalFieldOf("min", 0).forGetter(Containers::min),
				Codec.intRange(0, 256).optionalFieldOf("max", 4).forGetter(Containers::max),
				ContainerBlock.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(Containers::blocks)
		).apply(i, Containers::new));
	}

	/**
	 * Shared containers refill (and reroll) once {@code delayTicks} have passed since they were last
	 * opened and no player has been within {@code noPlayerRadius} for {@code quietTicks}.
	 */
	public record Refill(boolean enabled, long delayTicks, int noPlayerRadius, long quietTicks) {
		public static final Refill DEFAULT = new Refill(true, 72_000, 32, 6_000);
		public static final Codec<Refill> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled()).forGetter(Refill::enabled),
				Codec.LONG.optionalFieldOf("delay_ticks", DEFAULT.delayTicks()).forGetter(Refill::delayTicks),
				Codec.intRange(0, 256).optionalFieldOf("no_player_radius", DEFAULT.noPlayerRadius()).forGetter(Refill::noPlayerRadius),
				Codec.LONG.optionalFieldOf("quiet_ticks", DEFAULT.quietTicks()).forGetter(Refill::quietTicks)
		).apply(i, Refill::new));
	}

	/**
	 * @param per100m2 zombies per 100 floor cells of the building's units
	 * @param entities entity id -> weight
	 */
	public record Population(float per100m2, Map<Identifier, Integer> entities) {
		public static final Population NONE = new Population(0, Map.of());
		public static final Codec<Population> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0, 100).optionalFieldOf("per_100m2", 0f).forGetter(Population::per100m2),
				Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, 1000)).optionalFieldOf("entities", Map.of()).forGetter(Population::entities)
		).apply(i, Population::new));
	}

	public static final Codec<BuildingProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).listOf().optionalFieldOf("match", List.of()).forGetter(BuildingProfile::match),
			Codec.STRING.listOf().optionalFieldOf("arnis_uses", List.of()).forGetter(BuildingProfile::arnisUses),
			Codec.INT.optionalFieldOf("priority", 0).forGetter(BuildingProfile::priority),
			Codec.intRange(1, 5).optionalFieldOf("tier", 1).forGetter(BuildingProfile::tier),
			Identifier.CODEC.optionalFieldOf("loot_table").forGetter(BuildingProfile::lootTable),
			Containers.CODEC.optionalFieldOf("containers", Containers.NONE).forGetter(BuildingProfile::containers),
			Refill.CODEC.optionalFieldOf("refill", Refill.DEFAULT).forGetter(BuildingProfile::refill),
			Population.CODEC.optionalFieldOf("population", Population.NONE).forGetter(BuildingProfile::population),
			Codec.BOOL.optionalFieldOf("safe_candidate", false).forGetter(BuildingProfile::safeCandidate),
			Codec.floatRange(0, 1).optionalFieldOf("relay_chance", 0f).forGetter(BuildingProfile::relayChance),
			Identifier.CODEC.optionalFieldOf("cache_table").forGetter(BuildingProfile::cacheTable)
	).apply(i, BuildingProfile::new));

	/** True if any tag rule matches these building tags. */
	public boolean matchesTags(Map<String, String> tags) {
		for (Map<String, String> rule : match) {
			if (!rule.isEmpty() && rule.entrySet().stream().allMatch(e -> valueMatches(e.getValue(), tags.get(e.getKey())))) {
				return true;
			}
		}
		return false;
	}

	/** 2 = exact Arnis use match, 1 = family match ("Shop" for "Shop(Pharmacy)"), 0 = none. */
	public int matchesArnisUse(String use) {
		int family = use.indexOf('(');
		String base = family < 0 ? use : use.substring(0, family);
		int best = 0;
		for (String candidate : arnisUses) {
			if (candidate.equals(use)) {
				return 2;
			}
			if (candidate.equals(base)) {
				best = 1;
			}
		}
		return best;
	}

	static boolean valueMatches(String expected, String actual) {
		if (actual == null) {
			return false;
		}
		for (String alternative : expected.split("\\|")) {
			if (glob(alternative, 0, actual, 0)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whole-string glob match with '*' and '?'. Lost Cities ids come in families ("lcmt:building1".."8",
	 * "lostcities:building1".."8", "lcmt:highway/gas_station"), and globs let one rule cover a family across
	 * asset packs without a regex dialect in the data.
	 */
	private static boolean glob(String pattern, int p, String text, int t) {
		while (p < pattern.length()) {
			char c = pattern.charAt(p);
			if (c == '*') {
				for (int k = t; k <= text.length(); k++) {
					if (glob(pattern, p + 1, text, k)) {
						return true;
					}
				}
				return false;
			}
			if (t >= text.length() || (c != '?' && c != text.charAt(t))) {
				return false;
			}
			p++;
			t++;
		}
		return t == text.length();
	}
}
