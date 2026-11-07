package xyz.toesinthesand.ashfall.arms.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Map;
import java.util.Optional;

/**
 * The district loop's numbers, from {@code data/ashfall/ashfall/grid/settings.json}, so balancing the loop is a
 * datapack edit. Every field is optional and falls back to the default here; a missing file means all defaults.
 *
 * @param couplersRequired        Grid Couplers that repair a core (endgame-design: "socket Grid Couplers")
 * @param cellsRequired           Energy Cells that start the hold
 * @param cellMinEnergy           charge a cell needs to count; full by default, so a drained cell is no shortcut
 * @param energizeSeconds         attended seconds of hold ("a 5-minute hold")
 * @param holdRadius              a player within this many blocks of the core keeps the hold running
 * @param humIntervalSeconds      seconds between the core's hum (and its SOUND stimulus) while it holds or is besieged
 * @param humRadius               SOUND radius of the hum: 3x the Power Relay's 48 ("hums at 3x relay radius")
 * @param pressure                Director spawn pressure near a holding core
 * @param core                    core health and how siege zombies damage it
 * @param sirenWarningSeconds     siren warning before the siege nightfall (the horde siren uses 150 too)
 * @param dawnTime                time of day a siege night ends (vanilla sunrise)
 * @param minSiegePresenceSeconds attended siege seconds a dawn win needs; less and the siege is postponed
 * @param presenceRadius          a player within this many blocks of the core attends the siege
 * @param reclaimed               what a Reclaimed district changes
 * @param districts               per-district rewards beyond the shared ones, keyed by district id
 * @param relaysRequired          key relays that must be linked (of the district's six); the nearest few will do
 */
public record GridSettings(int couplersRequired, int cellsRequired, long cellMinEnergy, int energizeSeconds, int holdRadius,
		int humIntervalSeconds, int humRadius, Pressure pressure, Core core, int sirenWarningSeconds, int dawnTime,
		int minSiegePresenceSeconds, int presenceRadius, Reclaimed reclaimed, Map<String, DistrictRewards> districts,
		int relaysRequired) {
	public static final int DEFAULT_RELAYS_REQUIRED = 3;

	/** Settings with the default relay count. */
	public GridSettings(int couplersRequired, int cellsRequired, long cellMinEnergy, int energizeSeconds, int holdRadius,
			int humIntervalSeconds, int humRadius, Pressure pressure, Core core, int sirenWarningSeconds, int dawnTime,
			int minSiegePresenceSeconds, int presenceRadius, Reclaimed reclaimed, Map<String, DistrictRewards> districts) {
		this(couplersRequired, cellsRequired, cellMinEnergy, energizeSeconds, holdRadius, humIntervalSeconds, humRadius, pressure,
				core, sirenWarningSeconds, dawnTime, minSiegePresenceSeconds, presenceRadius, reclaimed, districts, DEFAULT_RELAYS_REQUIRED);
	}

	/**
	 * Spawn pressure while a core holds: the hum alone only pulls zombies that are already near, so the Director
	 * also adds a few, out of sight, around the core.
	 *
	 * @param intervalSeconds seconds between pressure spawns (only while a player keeps the hold running)
	 * @param count           zombies per pressure spawn
	 * @param maxAlive        no more pressure spawns while this many pressure zombies live near the core
	 * @param radiusMin       nearest spawn distance from the core
	 * @param radiusMax       farthest spawn distance from the core
	 */
	public record Pressure(int intervalSeconds, int count, int maxAlive, int radiusMin, int radiusMax) {
		public static final Pressure DEFAULT = new Pressure(15, 2, 8, 24, 40);
		public static final Codec<Pressure> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 3600).optionalFieldOf("interval_seconds", DEFAULT.intervalSeconds).forGetter(Pressure::intervalSeconds),
				Codec.intRange(0, 32).optionalFieldOf("count", DEFAULT.count).forGetter(Pressure::count),
				Codec.intRange(0, 128).optionalFieldOf("max_alive", DEFAULT.maxAlive).forGetter(Pressure::maxAlive),
				Codec.intRange(1, 128).optionalFieldOf("radius_min", DEFAULT.radiusMin).forGetter(Pressure::radiusMin),
				Codec.intRange(1, 128).optionalFieldOf("radius_max", DEFAULT.radiusMax).forGetter(Pressure::radiusMax)
		).apply(i, Pressure::new));
	}

	/**
	 * The core during a siege.
	 *
	 * @param hp                  health at the start of each siege
	 * @param attackRadius        a siege zombie this close to the core, horizontally, is attacking the substation
	 * @param attackHeight        ... if it is also within this many blocks of the core's height
	 * @param damagePerZombieSecond health one attacking zombie takes off per second
	 * @param doorRadius          security doors this close to the core are forced open for the siege
	 *                            ({@link SiegeDoors}); 0 leaves doors alone
	 */
	public record Core(int hp, double attackRadius, double attackHeight, int damagePerZombieSecond, int doorRadius) {
		public static final Core DEFAULT = new Core(1600, 6.5, 3.0, 1, 14);
		public static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 100_000).optionalFieldOf("hp", DEFAULT.hp).forGetter(Core::hp),
				Codec.doubleRange(0.5, 16).optionalFieldOf("attack_radius", DEFAULT.attackRadius).forGetter(Core::attackRadius),
				Codec.doubleRange(0.5, 16).optionalFieldOf("attack_height", DEFAULT.attackHeight).forGetter(Core::attackHeight),
				Codec.intRange(0, 10_000).optionalFieldOf("damage_per_zombie_second", DEFAULT.damagePerZombieSecond)
						.forGetter(Core::damagePerZombieSecond),
				Codec.intRange(0, 32).optionalFieldOf("door_radius", DEFAULT.doorRadius).forGetter(Core::doorRadius)
		).apply(i, Core::new));
	}

	/**
	 * What Reclaimed changes (endgame-design section 3, "Reclaimed means").
	 *
	 * @param darknessScale        multiplier on darkness spawning inside the district ("drops by 80%")
	 * @param compoundRadiusChunks the substation compound's safe zone: chunks on each side of the core's chunk
	 * @param lightRadius          LIGHT stimulus radius of one relit street light at night
	 * @param lightIntervalTicks   ticks between light stimulus passes (a LIGHT stimulus lives 40 ticks)
	 * @param lightPlayerRadius    only lights this close to a player shine for the Director (nobody else is hunted)
	 * @param lightMaxPerPass      cap on light stimuli per pass, so a lit downtown can't flood the stimulus field
	 * @param relightChunksPerTick chunks relit per server tick from the load queue
	 */
	public record Reclaimed(float darknessScale, int compoundRadiusChunks, int lightRadius, int lightIntervalTicks,
			int lightPlayerRadius, int lightMaxPerPass, int relightChunksPerTick) {
		public static final Reclaimed DEFAULT = new Reclaimed(0.2f, 1, 16, 40, 96, 64, 4);
		public static final Codec<Reclaimed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0, 10).optionalFieldOf("darkness_scale", DEFAULT.darknessScale).forGetter(Reclaimed::darknessScale),
				Codec.intRange(0, 8).optionalFieldOf("compound_radius_chunks", DEFAULT.compoundRadiusChunks)
						.forGetter(Reclaimed::compoundRadiusChunks),
				Codec.intRange(1, 24).optionalFieldOf("light_radius", DEFAULT.lightRadius).forGetter(Reclaimed::lightRadius),
				Codec.intRange(10, 1200).optionalFieldOf("light_interval_ticks", DEFAULT.lightIntervalTicks)
						.forGetter(Reclaimed::lightIntervalTicks),
				Codec.intRange(8, 256).optionalFieldOf("light_player_radius", DEFAULT.lightPlayerRadius).forGetter(Reclaimed::lightPlayerRadius),
				Codec.intRange(0, 512).optionalFieldOf("light_max_per_pass", DEFAULT.lightMaxPerPass).forGetter(Reclaimed::lightMaxPerPass),
				Codec.intRange(1, 64).optionalFieldOf("relight_chunks_per_tick", DEFAULT.relightChunksPerTick)
						.forGetter(Reclaimed::relightChunksPerTick)
		).apply(i, Reclaimed::new));
	}

	/**
	 * A district's own rewards on top of the shared ones.
	 *
	 * @param rewardTable the per-player cache's loot table; default {@code ashfall_cache:grid_reward_<district>}
	 * @param unsealLair  boss lair opened when the district is reclaimed (Mission: Patient Zero's deep ward)
	 * @param cityPump    while this district is reclaimed, bottles filled in any reclaimed district come up clean
	 */
	public record DistrictRewards(Optional<Identifier> rewardTable, Optional<Identifier> unsealLair, boolean cityPump) {
		public static final DistrictRewards NONE = new DistrictRewards(Optional.empty(), Optional.empty(), false);
		public static final Codec<DistrictRewards> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.optionalFieldOf("reward_table").forGetter(DistrictRewards::rewardTable),
				Identifier.CODEC.optionalFieldOf("unseal_lair").forGetter(DistrictRewards::unsealLair),
				Codec.BOOL.optionalFieldOf("city_pump", false).forGetter(DistrictRewards::cityPump)
		).apply(i, DistrictRewards::new));
	}

	public static final GridSettings DEFAULT = new GridSettings(4, 4, 240L, 300, 32, 4, 144, Pressure.DEFAULT, Core.DEFAULT,
			150, 23_000, 240, 96, Reclaimed.DEFAULT, Map.of());

	public static final Codec<GridSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 64).optionalFieldOf("couplers_required", DEFAULT.couplersRequired).forGetter(GridSettings::couplersRequired),
			Codec.intRange(1, 64).optionalFieldOf("cells_required", DEFAULT.cellsRequired).forGetter(GridSettings::cellsRequired),
			Codec.LONG.optionalFieldOf("cell_min_energy", DEFAULT.cellMinEnergy).forGetter(GridSettings::cellMinEnergy),
			Codec.intRange(1, 7200).optionalFieldOf("energize_seconds", DEFAULT.energizeSeconds).forGetter(GridSettings::energizeSeconds),
			Codec.intRange(1, 256).optionalFieldOf("hold_radius", DEFAULT.holdRadius).forGetter(GridSettings::holdRadius),
			Codec.intRange(1, 600).optionalFieldOf("hum_interval_seconds", DEFAULT.humIntervalSeconds).forGetter(GridSettings::humIntervalSeconds),
			Codec.intRange(1, 512).optionalFieldOf("hum_radius", DEFAULT.humRadius).forGetter(GridSettings::humRadius),
			Pressure.CODEC.optionalFieldOf("pressure", Pressure.DEFAULT).forGetter(GridSettings::pressure),
			Core.CODEC.optionalFieldOf("core", Core.DEFAULT).forGetter(GridSettings::core),
			Codec.intRange(0, 12_000).optionalFieldOf("siren_warning_seconds", DEFAULT.sirenWarningSeconds)
					.forGetter(GridSettings::sirenWarningSeconds),
			Codec.intRange(12_001, 24_000).optionalFieldOf("dawn_time", DEFAULT.dawnTime).forGetter(GridSettings::dawnTime),
			Codec.intRange(0, 36_000).optionalFieldOf("min_siege_presence_seconds", DEFAULT.minSiegePresenceSeconds)
					.forGetter(GridSettings::minSiegePresenceSeconds),
			Codec.intRange(8, 512).optionalFieldOf("presence_radius", DEFAULT.presenceRadius).forGetter(GridSettings::presenceRadius),
			Reclaimed.CODEC.optionalFieldOf("reclaimed", Reclaimed.DEFAULT).forGetter(GridSettings::reclaimed),
			Codec.unboundedMap(Codec.STRING, DistrictRewards.CODEC).optionalFieldOf("districts", Map.of()).forGetter(GridSettings::districts),
			Codec.intRange(1, 64).optionalFieldOf("relays_required", DEFAULT_RELAYS_REQUIRED).forGetter(GridSettings::relaysRequired)
	).apply(i, GridSettings::new));

	/** The loop rules in ticks. */
	public LoopRules rules() {
		return new LoopRules(couplersRequired, cellsRequired, energizeSeconds * 20L, core.hp(), sirenWarningSeconds * 20L,
				dawnTime - LoopRules.NIGHTFALL, minSiegePresenceSeconds * 20L, relaysRequired);
	}

	public DistrictRewards rewards(String district) {
		return districts.getOrDefault(district, DistrictRewards.NONE);
	}

	/** The reward cache table of {@code district}. */
	public Identifier rewardTable(String district) {
		return rewards(district).rewardTable().orElse(Identifier.fromNamespaceAndPath("ashfall_cache", "grid_reward_" + district));
	}

	private static volatile GridSettings current = DEFAULT;

	public static GridSettings get() {
		return current;
	}

	/** Reload listener for {@code data/<ns>/ashfall/grid/settings.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<GridSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "grid_settings");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "settings");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/grid"));
		}

		@Override
		protected void apply(Map<Identifier, GridSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Grid settings loaded ({} couplers, {} cells, {} s hold)", current.couplersRequired,
					current.cellsRequired, current.energizeSeconds);
		}
	}
}
