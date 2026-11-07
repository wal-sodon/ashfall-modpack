package xyz.toesinthesand.ashfall.director.horde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.haven.SirenLogic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Our own horde-night schedule (plan M8, replacing Undead Nights), from {@code data/ashfall/ashfall/horde/schedule.json}.
 * <p>
 * <b>When.</b> A horde night falls every {@code every_nights} nights from {@code first_night}, each one moved by up
 * to {@code jitter_nights} either way. The jitter is a hash of the world seed and the horde's index, so the schedule
 * is fixed for a world (the siren can predict it days ahead, and a restart never reshuffles it) but players cannot
 * count it off a calendar. The jitter is clamped below half the interval, so two horde nights never swap or fall on
 * the same night, and the first one is never early, so the grace nights at the start always hold.
 * <p>
 * <b>Who.</b> Like DeceasedCraft's horde config, zombie classes unlock by day ({@code unlocks}: from day N, variant
 * X may appear) and every wave carries its own spawn table ({@code spawns}) and its own extra drops
 * ({@code loot_table}). A variant no unlock names is available from day 1, so a new variant file works without
 * editing the schedule. The tier is max(day tier, Grid tier) (endgame-design section 3): the Grid tier comes from
 * arms through {@link HordeHooks#gridTier()}, because the Director must not depend on arms.
 * <p>
 * <b>Breach.</b> Every {@code breach.every_days} days from {@code breach.first_day} a horde hits one reclaimed
 * substation (endgame-design section 5); the target and the siege itself belong to arms ({@link BreachHook}).
 * <p>
 * Everything below the codec is pure arithmetic so JUnit pins the schedule down without a server.
 */
public record HordeSchedule(int everyNights, int jitterNights, int firstNight, long nightStart, long dawn,
		List<DayTier> dayTiers, List<Unlock> unlocks, List<Tier> tiers, Spawn spawn, Breach breach) {
	/** Time of day the sun sets; beds start working shortly after. */
	static final long SUNSET = 12_000L;
	public static final long DAY_TICKS = 24_000L;

	/** "From world day {@code fromDay} on, at least tier {@code tier}". */
	public record DayTier(int fromDay, int tier) {
		public static final Codec<DayTier> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, Integer.MAX_VALUE).fieldOf("from_day").forGetter(DayTier::fromDay),
				Codec.intRange(1, 100).fieldOf("tier").forGetter(DayTier::tier)
		).apply(i, DayTier::new));
	}

	/** Zombie class {@code variant} may appear in horde waves from world day {@code day}. */
	public record Unlock(int day, Identifier variant) {
		public static final Codec<Unlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, Integer.MAX_VALUE).fieldOf("day").forGetter(Unlock::day),
				Identifier.CODEC.fieldOf("variant").forGetter(Unlock::variant)
		).apply(i, Unlock::new));
	}

	/**
	 * One wave of a horde night, per player.
	 *
	 * @param size         zombies in the wave (one horde group)
	 * @param delaySeconds seconds after the previous wave (the first wave's delay counts from nightfall)
	 * @param spawns       Director variant id to weight; locked and unloaded variants are skipped
	 * @param lootTable    extra drops for this wave's zombies on death, on top of their normal drops
	 */
	public record Wave(int size, int delaySeconds, Map<Identifier, Integer> spawns, Optional<Identifier> lootTable) {
		public static final Codec<Wave> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 100).fieldOf("size").forGetter(Wave::size),
				Codec.intRange(0, 3600).optionalFieldOf("delay_seconds", 0).forGetter(Wave::delaySeconds),
				Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, 10_000)).optionalFieldOf("spawns", Map.of()).forGetter(Wave::spawns),
				Identifier.CODEC.optionalFieldOf("loot_table").forGetter(Wave::lootTable)
		).apply(i, Wave::new));
	}

	/** The waves of a horde night at this tier. */
	public record Tier(int tier, List<Wave> waves) {
		public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 100).fieldOf("tier").forGetter(Tier::tier),
				Wave.CODEC.listOf().fieldOf("waves").forGetter(Tier::waves)
		).apply(i, Tier::new));
	}

	/**
	 * Where horde-night waves appear and how many may live.
	 *
	 * @param radiusMin         nearest spawn distance from the player (the edge of the area they hold)
	 * @param radiusMax         farthest spawn distance
	 * @param groupSpread       a wave appears as one group within this many blocks of its spawn point
	 * @param havenRadius       a player within this distance of their Bunker Core has the horde come for the core
	 * @param maxAlivePerPlayer horde zombies near one player; the Director's hard caps (120 / 150) apply on top
	 */
	public record Spawn(int radiusMin, int radiusMax, int groupSpread, int havenRadius, int maxAlivePerPlayer) {
		public static final Spawn DEFAULT = new Spawn(40, 64, 4, 96, 60);
		public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(8, 128).optionalFieldOf("radius_min", DEFAULT.radiusMin).forGetter(Spawn::radiusMin),
				Codec.intRange(8, 128).optionalFieldOf("radius_max", DEFAULT.radiusMax).forGetter(Spawn::radiusMax),
				Codec.intRange(0, 16).optionalFieldOf("group_spread", DEFAULT.groupSpread).forGetter(Spawn::groupSpread),
				Codec.intRange(0, 512).optionalFieldOf("haven_radius", DEFAULT.havenRadius).forGetter(Spawn::havenRadius),
				Codec.intRange(0, 150).optionalFieldOf("max_alive_per_player", DEFAULT.maxAlivePerPlayer).forGetter(Spawn::maxAlivePerPlayer)
		).apply(i, Spawn::new));
	}

	/**
	 * Breach events: every {@code everyDays} days from {@code firstDay} (0 turns them off), with the siren
	 * {@code warningSeconds} before that nightfall.
	 */
	public record Breach(int everyDays, int firstDay, int warningSeconds) {
		public static final Breach DEFAULT = new Breach(7, 7, 150);
		public static final Codec<Breach> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 365).optionalFieldOf("every_days", DEFAULT.everyDays).forGetter(Breach::everyDays),
				Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("first_day", DEFAULT.firstDay).forGetter(Breach::firstDay),
				Codec.intRange(0, 600).optionalFieldOf("warning_seconds", DEFAULT.warningSeconds).forGetter(Breach::warningSeconds)
		).apply(i, Breach::new));
	}

	public static final HordeSchedule DEFAULT = new HordeSchedule(5, 1, 5, 12_000L, 23_000L,
			List.of(new DayTier(1, 1)), List.of(),
			List.of(new Tier(1, List.of(new Wave(6, 0, Map.of(Identifier.fromNamespaceAndPath("ashfall", "walker"), 1), Optional.empty())))),
			Spawn.DEFAULT, Breach.DEFAULT);

	public static final Codec<HordeSchedule> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 365).optionalFieldOf("every_nights", DEFAULT.everyNights).forGetter(HordeSchedule::everyNights),
			Codec.intRange(0, 100).optionalFieldOf("jitter_nights", DEFAULT.jitterNights).forGetter(HordeSchedule::jitterNights),
			Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("first_night", DEFAULT.firstNight).forGetter(HordeSchedule::firstNight),
			Codec.LONG.optionalFieldOf("night_start", DEFAULT.nightStart).forGetter(HordeSchedule::nightStart),
			Codec.LONG.optionalFieldOf("dawn", DEFAULT.dawn).forGetter(HordeSchedule::dawn),
			DayTier.CODEC.listOf().optionalFieldOf("day_tiers", DEFAULT.dayTiers).forGetter(HordeSchedule::dayTiers),
			Unlock.CODEC.listOf().optionalFieldOf("unlocks", DEFAULT.unlocks).forGetter(HordeSchedule::unlocks),
			Tier.CODEC.listOf().optionalFieldOf("tiers", DEFAULT.tiers).forGetter(HordeSchedule::tiers),
			Spawn.CODEC.optionalFieldOf("spawn", Spawn.DEFAULT).forGetter(HordeSchedule::spawn),
			Breach.CODEC.optionalFieldOf("breach", Breach.DEFAULT).forGetter(HordeSchedule::breach)
	).apply(i, HordeSchedule::new));

	// ---- when -------------------------------------------------------------------------------------------------

	/** World day of a day-clock tick, 1 on the first day (the siren and the siege use the same day clock). */
	public static int day(long clock) {
		return (int) Math.min(Integer.MAX_VALUE, Math.floorDiv(Math.max(0, clock), DAY_TICKS) + 1);
	}

	/** Whether hordes are scheduled at all. */
	public boolean enabled() {
		return everyNights > 0;
	}

	/** The jitter actually used: below half the interval, so consecutive horde nights keep their order. */
	public int effectiveJitter() {
		return Math.max(0, Math.min(jitterNights, (everyNights - 1) / 2));
	}

	/**
	 * Days the {@code k}-th horde night (0-based) moves from its slot: within ±{@link #effectiveJitter()}, and never
	 * negative for the first one. A stable hash of the world seed and {@code k}.
	 */
	public int jitter(int k, long seed) {
		int j = effectiveJitter();
		if (j == 0) {
			return 0;
		}
		long h = mix(seed ^ (0x9E3779B97F4A7C15L * (k + 1L)));
		int span = k == 0 ? j + 1 : 2 * j + 1;
		int offset = (int) Math.floorMod(h, (long) span);
		return k == 0 ? offset : offset - j;
	}

	/** World day of the {@code k}-th horde night (0-based). */
	public int hordeDay(int k, long seed) {
		return firstNight + k * everyNights + jitter(k, seed);
	}

	/** Whether the night of world day {@code day} is a horde night. */
	public boolean isHordeDay(int day, long seed) {
		if (!enabled() || day < firstNight) {
			return false;
		}
		int k0 = (day - firstNight) / everyNights;
		for (int k = Math.max(0, k0 - 1); k <= k0 + 1; k++) {
			if (hordeDay(k, seed) == day) {
				return true;
			}
		}
		return false;
	}

	/** The first horde night on or after world day {@code fromDay}; {@link Integer#MAX_VALUE} when hordes are off. */
	public int nextHordeDay(int fromDay, long seed) {
		if (!enabled()) {
			return Integer.MAX_VALUE;
		}
		int k = Math.max(0, (fromDay - firstNight) / everyNights - 1);
		while (hordeDay(k, seed) < fromDay) {
			k++;
		}
		return hordeDay(k, seed);
	}

	/** Whether {@code clock} is inside a night's horde window (nightfall to dawn), whatever the day. */
	public boolean inNightWindow(long clock) {
		long timeOfDay = Math.floorMod(clock, DAY_TICKS);
		return timeOfDay >= nightStart && timeOfDay < dawn;
	}

	/** Whether a horde night is running at day-clock tick {@code clock}. */
	public boolean hordeNightAt(long clock, long seed) {
		return inNightWindow(clock) && isHordeDay(day(clock), seed);
	}

	/**
	 * Whether beds refuse sleep: from sunset (or {@code night_start}, if earlier) to dawn on a horde day. It starts
	 * before the waves do, because beds work from dusk and sleeping through would skip the whole horde night.
	 */
	public boolean noSleepAt(long clock, long seed) {
		long timeOfDay = Math.floorMod(clock, DAY_TICKS);
		return timeOfDay >= Math.min(SUNSET, nightStart) && timeOfDay < dawn && isHordeDay(day(clock), seed);
	}

	/**
	 * Whether {@code clock} lies between the horde siren and dawn on a horde day: from {@code warnTicks} before the
	 * siren's nightfall ({@link SirenLogic#NIGHTFALL}, or {@code night_start} if earlier) until {@code dawn}. Survivors
	 * refuse new jobs in this window (arms {@code Survivors.roleChangeRefusal}).
	 */
	public boolean sirenToDawnAt(long clock, long seed, long warnTicks) {
		long timeOfDay = Math.floorMod(clock, DAY_TICKS);
		long from = Math.min(nightStart, SirenLogic.NIGHTFALL - Math.max(0L, warnTicks));
		return timeOfDay >= from && timeOfDay < dawn && isHordeDay(day(clock), seed);
	}

	// ---- how hard ---------------------------------------------------------------------------------------------

	/** The day tier on world day {@code day}. */
	public int dayTier(int day) {
		int tier = 1;
		for (DayTier step : dayTiers) {
			if (day >= step.fromDay()) {
				tier = Math.max(tier, step.tier());
			}
		}
		return tier;
	}

	/** Horde tier = max(day tier, Grid tier). */
	public int tier(int day, int gridTier) {
		return Math.max(dayTier(day), gridTier);
	}

	/** The table row for {@code tier}: that tier, else the highest below it, else the lowest there is. */
	public Tier tierRow(int tier) {
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

	// ---- who --------------------------------------------------------------------------------------------------

	/** The day {@code variant} unlocks: the earliest unlock naming it, or 1 when none does. */
	public int unlockDay(Identifier variant) {
		int day = Integer.MAX_VALUE;
		for (Unlock unlock : unlocks) {
			if (unlock.variant().equals(variant)) {
				day = Math.min(day, unlock.day());
			}
		}
		return day == Integer.MAX_VALUE ? 1 : day;
	}

	public boolean isUnlocked(Identifier variant, int day) {
		return day >= unlockDay(variant);
	}

	/** Every class the schedule names that is unlocked on {@code day} (for the status command). */
	public Set<Identifier> unlockedOn(int day) {
		Set<Identifier> out = new TreeSet<>();
		for (Unlock unlock : unlocks) {
			if (day >= unlock.day()) {
				out.add(unlock.variant());
			}
		}
		return out;
	}

	/** A wave's spawn table with the classes still locked on {@code day} taken out (order kept). */
	public Map<Identifier, Integer> pool(Wave wave, int day) {
		Map<Identifier, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Integer> e : wave.spawns().entrySet()) {
			if (isUnlocked(e.getKey(), day)) {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	// ---- breach -----------------------------------------------------------------------------------------------

	public boolean isBreachDay(int day) {
		return breach.everyDays() > 0 && day >= breach.firstDay() && (day - breach.firstDay()) % breach.everyDays() == 0;
	}

	/** The latest scheduled breach day on or before {@code day}; -1 if there is none yet or breaches are off. */
	public int dueBreachDay(int day) {
		if (breach.everyDays() <= 0 || day < breach.firstDay()) {
			return -1;
		}
		return breach.firstDay() + (day - breach.firstDay()) / breach.everyDays() * breach.everyDays();
	}

	/**
	 * Whether a due Breach may come on {@code day}: never on a horde night or the night after one, so the two never
	 * stack (a Breach every 7 days and a horde about every 5 used to land together). A Breach moves to the next free
	 * night instead; the grid can also hold it back while a District Siege is set ({@link BreachHook#busy}).
	 */
	public boolean breachNightAllowed(int day, long seed) {
		return !isHordeDay(day, seed) && !isHordeDay(day - 1, seed);
	}

	/** The first breach day on or after {@code fromDay}; {@link Integer#MAX_VALUE} when breaches are off. */
	public int nextBreachDay(int fromDay) {
		if (breach.everyDays() <= 0) {
			return Integer.MAX_VALUE;
		}
		if (fromDay <= breach.firstDay()) {
			return breach.firstDay();
		}
		int since = fromDay - breach.firstDay();
		int steps = (since + breach.everyDays() - 1) / breach.everyDays();
		return breach.firstDay() + steps * breach.everyDays();
	}

	/** SplitMix64's finaliser: spreads seed bits so neighbouring indices get unrelated jitters. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	// ---- loading ----------------------------------------------------------------------------------------------

	private static volatile HordeSchedule current = DEFAULT;
	private static volatile HordeSchedule testOverride;

	public static HordeSchedule get() {
		HordeSchedule test = testOverride;
		return test != null ? test : current;
	}

	/**
	 * The schedule as it runs on {@code server}: {@link #get()} with the story's horde rules applied
	 * ({@link HordeHooks#intervalMultiplier}, {@link HordeHooks#waveSizeMultiplier}). Everything that decides when a
	 * horde comes or how big it is (the night loop, the siren, the status command) reads this, so they always agree.
	 */
	public static HordeSchedule get(MinecraftServer server) {
		return get().scaled(HordeHooks.intervalMultiplier(server), HordeHooks.waveSizeMultiplier(server));
	}

	/**
	 * This schedule with horde nights {@code intervalMultiplier} times rarer and every wave {@code sizeMultiplier}
	 * times larger (rounded up, at most 100, the codec's limit). The first night stays where it was, so a schedule
	 * that changes after the ending only stretches the nights still to come. Returns {@code this} when nothing
	 * changes, and caches the last result because the night loop asks every second.
	 */
	public HordeSchedule scaled(int intervalMultiplier, double sizeMultiplier) {
		int m = Math.max(1, intervalMultiplier);
		double size = Math.max(1.0, sizeMultiplier);
		if (m == 1 && size == 1.0) {
			return this;
		}
		Scaled cached = lastScaled;
		if (cached != null && cached.base == this && cached.interval == m && cached.size == size) {
			return cached.result;
		}
		List<Tier> grown = new ArrayList<>();
		for (Tier row : tiers) {
			List<Wave> waves = new ArrayList<>();
			for (Wave wave : row.waves()) {
				waves.add(new Wave(scaleWave(wave.size(), size), wave.delaySeconds(), wave.spawns(), wave.lootTable()));
			}
			grown.add(new Tier(row.tier(), List.copyOf(waves)));
		}
		HordeSchedule result = new HordeSchedule(everyNights * m, jitterNights, firstNight, nightStart, dawn, dayTiers, unlocks,
				List.copyOf(grown), spawn, breach);
		lastScaled = new Scaled(this, m, size, result);
		return result;
	}

	/** A wave of {@code size} grown by {@code multiplier}: rounded up, at least the original, at most 100. Pure. */
	public static int scaleWave(int size, double multiplier) {
		return Math.min(100, Math.max(size, (int) Math.ceil(size * Math.max(1.0, multiplier) - 1e-9)));
	}

	private record Scaled(HordeSchedule base, int interval, double size, HordeSchedule result) {
	}

	private static volatile Scaled lastScaled;

	/** Test hook: a schedule that wins over the datapack; null goes back to it. */
	public static void setForTesting(HordeSchedule schedule) {
		testOverride = schedule;
	}

	/** Reload listener for {@code data/<ns>/ashfall/horde/schedule.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<HordeSchedule> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "horde_schedule");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "schedule");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/horde"));
		}

		@Override
		protected void apply(Map<Identifier, HordeSchedule> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallDirector.LOGGER.info("Horde schedule loaded: every {} nights (±{}), first night {}, {} tiers, {} unlocks",
					current.everyNights, current.effectiveJitter(), current.firstNight, current.tiers.size(), current.unlocks.size());
		}
	}
}
