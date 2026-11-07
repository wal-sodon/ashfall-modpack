package xyz.toesinthesand.ashfall.arms.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * District Siege tables, from {@code data/ashfall/ashfall/grid/siege.json}.
 * <p>
 * Siege tier = max(day tier, Grid tier) (endgame-design section 3): the day tier grows with the world's age, the
 * Grid tier with progress, so relighting a district always makes the next siege harder and a late, lit city is
 * never a free safe zone. The Grid tier is the larger of the district's own zone tier (the act table gives each
 * district one) and {@code grid_tier_base} plus the number of districts already reclaimed.
 *
 * @param dayTiers           ascending "from this world day on, at least this tier" steps
 * @param gridTierBase       Grid tier with nothing reclaimed yet
 * @param spawnRadiusMin     nearest wave spawn distance from the core
 * @param spawnRadiusMax     farthest wave spawn distance from the core
 * @param waveIntervalSeconds the next wave comes after this long even if the last one still lives
 * @param nextWaveAliveAtMost the next wave comes early once no more than this many siege zombies are left
 * @param tiers              waves per tier; a tier missing from the table uses the highest tier below it
 * @param partyScale         wave size multiplier by the number of players at the core (index 0 = solo; the last entry
 *                           covers bigger parties). Why: a siege's waves used to be the same for one player and four,
 *                           so a party never felt the siege and a solo player was swamped (phase 7 balance pass)
 * @param firstSiegeDayTierCap while no district is reclaimed, the day tier counts at most this much (0 = no cap). Why: a
 *                           careful player reaching their first district on day 15 met a tier III siege from the day
 *                           tier alone; the district's own zone tier still applies, so Mission stays hard
 */
public record SiegeSettings(List<DayTier> dayTiers, int gridTierBase, int spawnRadiusMin, int spawnRadiusMax,
		int waveIntervalSeconds, int nextWaveAliveAtMost, List<Tier> tiers, List<Double> partyScale, int firstSiegeDayTierCap) {

	/** No first-siege cap. */
	public SiegeSettings(List<DayTier> dayTiers, int gridTierBase, int spawnRadiusMin, int spawnRadiusMax,
			int waveIntervalSeconds, int nextWaveAliveAtMost, List<Tier> tiers, List<Double> partyScale) {
		this(dayTiers, gridTierBase, spawnRadiusMin, spawnRadiusMax, waveIntervalSeconds, nextWaveAliveAtMost, tiers, partyScale, 0);
	}

	/** The pre-phase-7 shape: no party scaling. */
	public SiegeSettings(List<DayTier> dayTiers, int gridTierBase, int spawnRadiusMin, int spawnRadiusMax,
			int waveIntervalSeconds, int nextWaveAliveAtMost, List<Tier> tiers) {
		this(dayTiers, gridTierBase, spawnRadiusMin, spawnRadiusMax, waveIntervalSeconds, nextWaveAliveAtMost, tiers, List.of(1.0));
	}

	public record DayTier(int fromDay, int tier) {
		public static final Codec<DayTier> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, Integer.MAX_VALUE).fieldOf("from_day").forGetter(DayTier::fromDay),
				Codec.intRange(1, 100).fieldOf("tier").forGetter(DayTier::tier)
		).apply(i, DayTier::new));
	}

	/**
	 * One tier's siege.
	 *
	 * @param tier     the tier number
	 * @param waves    waves in the siege
	 * @param size     zombies per wave
	 * @param variants Director zombie variant id to weight (variants that are not loaded are skipped)
	 * @param coreHp   the core's health in this tier's siege (0: the grid settings' {@code core.hp}). Per tier because
	 *                 waves grow with the tier: one core health for every tier meant an undefended tier-VI core fell
	 *                 in under 20 s while a tier-II one lasted twice as long, and neither gave a party that stepped
	 *                 away the 2-3 minutes the balance pass aims for (docs/balance-v1.md)
	 */
	public record Tier(int tier, int waves, int size, Map<Identifier, Integer> variants, int coreHp) {
		public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 100).fieldOf("tier").forGetter(Tier::tier),
				Codec.intRange(1, 50).fieldOf("waves").forGetter(Tier::waves),
				Codec.intRange(1, 100).fieldOf("size").forGetter(Tier::size),
				Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, 10_000)).optionalFieldOf("variants", Map.of())
						.forGetter(Tier::variants),
				Codec.intRange(0, 100_000).optionalFieldOf("core_hp", 0).forGetter(Tier::coreHp)
		).apply(i, Tier::new));

		/** A tier that uses the settings' core health. */
		public Tier(int tier, int waves, int size, Map<Identifier, Integer> variants) {
			this(tier, waves, size, variants, 0);
		}

		/** This tier's core health, or {@code fallback} (the grid settings' {@code core.hp}) when the tier sets none. */
		public int coreHp(int fallback) {
			return coreHp > 0 ? coreHp : fallback;
		}
	}

	public static final SiegeSettings DEFAULT = new SiegeSettings(List.of(new DayTier(1, 1)), 1, 18, 30, 90, 2,
			List.of(new Tier(1, 3, 6, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1))));

	public static final Codec<SiegeSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			DayTier.CODEC.listOf().optionalFieldOf("day_tiers", DEFAULT.dayTiers).forGetter(SiegeSettings::dayTiers),
			Codec.intRange(1, 100).optionalFieldOf("grid_tier_base", DEFAULT.gridTierBase).forGetter(SiegeSettings::gridTierBase),
			Codec.intRange(1, 128).optionalFieldOf("spawn_radius_min", DEFAULT.spawnRadiusMin).forGetter(SiegeSettings::spawnRadiusMin),
			Codec.intRange(1, 128).optionalFieldOf("spawn_radius_max", DEFAULT.spawnRadiusMax).forGetter(SiegeSettings::spawnRadiusMax),
			Codec.intRange(1, 3600).optionalFieldOf("wave_interval_seconds", DEFAULT.waveIntervalSeconds)
					.forGetter(SiegeSettings::waveIntervalSeconds),
			Codec.intRange(0, 100).optionalFieldOf("next_wave_alive_at_most", DEFAULT.nextWaveAliveAtMost)
					.forGetter(SiegeSettings::nextWaveAliveAtMost),
			Tier.CODEC.listOf().optionalFieldOf("tiers", DEFAULT.tiers).forGetter(SiegeSettings::tiers),
			Codec.doubleRange(0.1, 10).listOf(1, 32).optionalFieldOf("party_scale", DEFAULT.partyScale)
					.forGetter(SiegeSettings::partyScale),
			Codec.intRange(0, 100).optionalFieldOf("first_siege_day_tier_cap", 0).forGetter(SiegeSettings::firstSiegeDayTierCap)
	).apply(i, SiegeSettings::new));

	/** Zombies per wave of {@code tier} for {@code players} at the core (nobody counts as solo), at least one. */
	public int waveSize(Tier tier, int players) {
		int index = Math.min(partyScale.size(), Math.max(1, players)) - 1;
		return Math.max(1, (int) Math.round(tier.size() * partyScale.get(index)));
	}

	/** The day tier on world day {@code day} (1-based). */
	public int dayTier(int day) {
		int tier = 1;
		for (DayTier step : dayTiers) {
			if (day >= step.fromDay()) {
				tier = Math.max(tier, step.tier());
			}
		}
		return tier;
	}

	/** Grid tier for a district of zone tier {@code zoneTier} with {@code reclaimed} districts already relit. */
	public int gridTier(int zoneTier, int reclaimed) {
		return Math.max(zoneTier, gridTierBase + Math.max(0, reclaimed));
	}

	/** Siege tier = max(day tier, Grid tier), the day tier capped for the first siege (see {@link #firstSiegeDayTierCap}). */
	public int siegeTier(int day, int zoneTier, int reclaimed) {
		int dayTier = dayTier(day);
		if (reclaimed <= 0 && firstSiegeDayTierCap > 0) {
			dayTier = Math.min(dayTier, firstSiegeDayTierCap);
		}
		return Math.max(dayTier, gridTier(zoneTier, reclaimed));
	}

	/** The table row for {@code tier}: that tier, else the highest below it, else the lowest there is. */
	public Tier tier(int tier) {
		Tier best = null;
		for (Tier row : tiers) {
			if (row.tier() <= tier && (best == null || row.tier() > best.tier())) {
				best = row;
			}
		}
		if (best == null) {
			best = tiers.stream().min(Comparator.comparingInt(Tier::tier)).orElse(DEFAULT.tiers.getFirst());
		}
		return best;
	}

	private static volatile SiegeSettings current = DEFAULT;

	public static SiegeSettings get() {
		return current;
	}

	/** Reload listener for {@code data/<ns>/ashfall/grid/siege.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<SiegeSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "grid_siege");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "siege");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/grid"));
		}

		@Override
		protected void apply(Map<Identifier, SiegeSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Siege tables loaded ({} tiers)", current.tiers.size());
		}
	}
}
