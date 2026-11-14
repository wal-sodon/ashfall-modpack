package xyz.toesinthesand.ashfall.director.horde;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;
import xyz.toesinthesand.ashfall.director.haven.HavenSettings;
import xyz.toesinthesand.ashfall.director.haven.HordeSiren;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.spawn.DirectorContexts;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorTracker;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.director.spawn.SpawnRules;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;

/**
 * Runs horde nights and Breach events from our own schedule ({@link HordeSchedule}).
 * <p>
 * <b>Horde nights.</b> From nightfall on a horde day each overworld player gets the tier's waves one after another
 * ({@code delay_seconds} apart). A wave is one group: it appears at the edge of the area the player holds (40-64
 * blocks out from their haven if they are in it, else from them), out of their sight, never inside a claim or safe
 * zone, and becomes a {@link Horde} sent after the player, so it converges on their haven (or on them) and is
 * pulled aside by any noise or light on the way. All zombies are added with {@code addFreshEntity} through
 * {@link DirectorSpawns} and stay inside the Director's hard caps (120 per player, 150 per world) and the schedule's
 * own per-player horde cap. A wave the caps or the ground only let partly spawn owes the rest, which comes as its own
 * group on a retry ({@link #RETRY_TICKS}) before the next wave's delay starts. Every member first rolls for a special infected at siege weights
 * ({@link SpecialInfected#roll}), so horde nights bring specials in the same proportion as sieges and Breaches.
 * <p>
 * <b>Breach.</b> On a breach day the siren names a random reclaimed substation before nightfall, and at nightfall
 * the horde hits it: the grid (arms, through {@link BreachHook}) runs the attack and decides what a lost core costs.
 * <p>
 * At dawn after a horde night every Bunker Core owner online hears about blocks in their claim worn below half
 * ({@link FortifyWear#warnAfterHorde}).
 * <p>
 * Wave progress is kept in memory: after a restart in the middle of a horde night the waves start over, which only
 * matters to an admin restarting at night, and cannot exceed the caps anyway.
 */
public final class HordeNights {
	private static final int UPDATE_TICKS = 20;
	/** How soon a wave that could not spawn tries again. */
	static final long RETRY_TICKS = 5 * 20L;
	/** Spawn-spot tries per wave: the first half must be out of sight, the second half only out of claims. */
	static final int EDGE_ATTEMPTS = 48;
	/** Game time of the last "could not spawn" log line: at most one a minute, not one per retry. */
	private static long lastFailLog = Long.MIN_VALUE / 2;

	/**
	 * Per player: which night their waves belong to, how many have come, when the next one (or the rest of this one) is
	 * due (game time), and how many of the current wave are still owed (0: all of it, nothing spawned yet).
	 */
	record Progress(int day, int waves, long nextAt, int owed) {
	}

	private static final Map<UUID, Progress> PROGRESS = new HashMap<>();
	/** Whether the last update was inside a horde night, so dawn after one can warn Bunker Core owners about wear. */
	private static boolean wasHordeNight;

	private HordeNights() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 9) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PROGRESS.clear();
			lastFailLog = Long.MIN_VALUE / 2;
			wasHordeNight = false;
		});
		// No sleeping through a horde night: the waves only come while it is night.
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (!(player.level() instanceof ServerLevel level) || level != level.getServer().overworld()) {
				return null;
			}
			return HordeSchedule.get(level.getServer()).noSleepAt(level.getOverworldClockTime(), level.getSeed())
					? new Player.BedSleepingProblem(Component.translatable("message.ashfall.no_sleep.horde"))
					: null;
		});
	}

	/** Today's horde tier: max(day tier, Grid tier, story tier). */
	public static int tier(MinecraftServer server) {
		return HordeHooks.tier(HordeSchedule.get(server), HordeSchedule.day(server.overworld().getOverworldClockTime()), server);
	}

	/** Whether a horde night is running right now. */
	public static boolean hordeNight(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		return HordeSchedule.get(server).hordeNightAt(overworld.getOverworldClockTime(), overworld.getSeed());
	}

	/**
	 * Whether the horde siren has gone for tonight's horde night and dawn has not come yet, at day-clock tick
	 * {@code clock} (the overworld's, or a test's): the warning window ({@code siren_warning_seconds}) through the night.
	 */
	public static boolean sirenToDawn(MinecraftServer server, long clock) {
		return HordeSchedule.get(server).sirenToDawnAt(clock, server.overworld().getSeed(), HavenSettings.get().sirenWarningTicks());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HordeSchedule schedule = HordeSchedule.get(server);
		long clock = level.getOverworldClockTime();
		breachTick(level, schedule, clock);
		boolean night = schedule.hordeNightAt(clock, level.getSeed());
		if (wasHordeNight && !night) {
			FortifyWear.warnAfterHorde(server);
		}
		wasHordeNight = night;
		if (!night) {
			PROGRESS.clear();
			return;
		}
		if (!SpawnDirector.active(level)) {
			return;
		}
		int day = HordeSchedule.day(clock);
		HordeSchedule.Tier row = schedule.tierRow(HordeHooks.tier(schedule, day, server));
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			Progress p = PROGRESS.get(player.getUUID());
			if (p == null || p.day() != day) {
				p = new Progress(day, 0, now + delayTicks(row, 0), 0);
			}
			if (p.waves() < row.waves().size() && now >= p.nextAt()) {
				HordeSchedule.Wave wave = row.waves().get(p.waves());
				int size = p.owed() > 0 ? p.owed() : wave.size();
				Horde horde = spawnWave(level, player, schedule,
						new HordeSchedule.Wave(size, wave.delaySeconds(), wave.spawns(), wave.lootTable()), day);
				p = afterAttempt(p, size, horde == null ? 0 : horde.size(), now, delayTicks(row, p.waves() + 1));
			}
			PROGRESS.put(player.getUUID(), p);
		}
	}

	/**
	 * Progress after trying to spawn {@code size} zombies of the current wave, {@code spawned} of which came. All of
	 * them: on to the next wave after {@code nextDelay}. Fewer (caps nearly full, members with no free spot) or none
	 * (caps full, no spot at all: claims, sight, unloaded ground): the rest stays owed and is tried again after
	 * {@link #RETRY_TICKS}, so one bad moment no longer costs the night its zombies.
	 */
	static Progress afterAttempt(Progress p, int size, int spawned, long now, long nextDelay) {
		if (spawned >= size) {
			return new Progress(p.day(), p.waves() + 1, now + nextDelay, 0);
		}
		return new Progress(p.day(), p.waves(), now + RETRY_TICKS, spawned > 0 ? size - spawned : p.owed());
	}

	private static long delayTicks(HordeSchedule.Tier row, int wave) {
		return wave < row.waves().size() ? row.waves().get(wave).delaySeconds() * 20L : 0L;
	}

	/**
	 * Spawns one wave for {@code player} as a single horde heading for their haven or them. Public for gametests and
	 * the admin command. @return the horde, or null if nothing could spawn (caps, no room)
	 */
	public static @Nullable Horde spawnWave(ServerLevel level, ServerPlayer player, HordeSchedule schedule, HordeSchedule.Wave wave, int day) {
		HordeSchedule.Spawn spawn = schedule.spawn();
		int room = Math.min(wave.size(), room(level, player, spawn));
		if (room <= 0) {
			Vec3 at = player.position();
			logFailure(level, "no room for " + player.getName().getString() + " (zombie caps full: "
					+ DirectorTracker.countNear(at.x, at.y, at.z, SpawnRules.PLAYER_CAP_RADIUS)[1] + " Director zombies near, "
					+ DirectorTracker.countNear(at.x, at.y, at.z, SpawnRules.PLAYER_CAP_RADIUS, DirectorZombie.Source.HORDE)
					+ " of them horde, " + DirectorTracker.total() + " loaded)");
			return null;
		}
		Vec3 target = Hordes.chaseTarget(level, player.getUUID(), spawn.havenRadius());
		if (target == null) {
			return null;
		}
		return spawnGroup(level, target, player.getUUID(), player.getName().getString(), room, schedule, wave, day);
	}

	/**
	 * Spawns one wave aimed at a place instead of a player ({@code /ashfall horde wave at}): the same ring, sight and
	 * claim rules and the same specials roll, with the room counted around {@code target} as if a player stood there.
	 * It exists because a dedicated server with nobody online (a headless playtest, an admin preparing an event) has
	 * no player to aim the scheduled waves at. @return the horde, or null if nothing could spawn
	 */
	public static @Nullable Horde spawnWaveAt(ServerLevel level, Vec3 target, HordeSchedule schedule, HordeSchedule.Wave wave, int day) {
		return spawnWaveAt(level, target, schedule, wave, day, schedule.spawn().maxAlivePerPlayer());
	}

	/**
	 * {@link #spawnWaveAt(ServerLevel, Vec3, HordeSchedule, HordeSchedule.Wave, int)} with the horde cap around
	 * {@code target} set by the caller. Why: the per-player cap ({@code max_alive_per_player}, 60) assumes one player
	 * per place, but a place a whole party defends (the Black Start's Uplink) would otherwise hold the same 60 for
	 * four players as for one. The Director's hard caps (120 near the place, the world cap) still apply on top.
	 *
	 * @param maxHordeAlive horde zombies allowed alive within the player cap radius of {@code target}
	 */
	public static @Nullable Horde spawnWaveAt(ServerLevel level, Vec3 target, HordeSchedule schedule, HordeSchedule.Wave wave, int day,
			int maxHordeAlive) {
		int room = Math.min(wave.size(), room(level, target, schedule.spawn(), maxHordeAlive));
		if (room <= 0) {
			return null;
		}
		return spawnGroup(level, target, null, BlockPos.containing(target).toShortString(), room, schedule, wave, day);
	}

	private static @Nullable Horde spawnGroup(ServerLevel level, Vec3 target, @Nullable UUID chase, String forName, int room,
			HordeSchedule schedule, HordeSchedule.Wave wave, int day) {
		HordeSchedule.Spawn spawn = schedule.spawn();
		RandomSource random = level.getRandom();
		BlockPos centre = edgeSpot(level, target, spawn, random);
		if (centre == null) {
			logFailure(level, "no spawn spot around " + BlockPos.containing(target).toShortString() + " for " + forName);
			return null;
		}
		Map<Identifier, Integer> pool = schedule.pool(wave, day);
		String zone = DirectorContexts.zoneAt(centre).id();
		List<Zombie> spawned = new ArrayList<>();
		int specials = 0;
		for (int i = 0; i < room; i++) {
			BlockPos spot = i == 0 ? centre : near(level, centre, spawn.groupSpread(), random);
			if (spot == null) {
				continue;
			}
			// Special infected join at their files' siege weights (their min_day is the unlock), capped per player
			// and only where their hitbox fits; otherwise the wave's own table picks the member.
			Map.Entry<Identifier, ZombieVariant> variant = SpecialInfected.roll(level, spot, day, zone, true, random);
			boolean special = variant != null;
			if (variant == null) {
				variant = pick(pool, random);
			}
			Zombie zombie = DirectorSpawns.spawn(level, spot, variant, DirectorZombie.Source.HORDE, DirectorZombie.NO_BUILDING);
			if (zombie != null) {
				wave.lootTable().ifPresent(table -> zombie.setAttached(HordeLoot.TABLE, table));
				spawned.add(zombie);
				if (special) {
					specials++;
				}
			}
		}
		if (spawned.isEmpty()) {
			return null;
		}
		Horde horde = Hordes.create(spawned, target, chase, Long.MAX_VALUE);
		AshfallDirector.LOGGER.info("Horde night: {} zombies ({} special) for {} at {} (horde {})", spawned.size(), specials, forName,
				centre.toShortString(), horde.id());
		return horde;
	}

	private static void logFailure(ServerLevel level, String why) {
		long now = level.getGameTime();
		if (now - lastFailLog >= 1200) {
			lastFailLog = now;
			AshfallDirector.LOGGER.info("Horde night: {}, retrying every {} s", why, RETRY_TICKS / 20);
		}
	}

	/** Horde zombies one player may still get: the Director's hard caps and the schedule's per-player horde cap. */
	public static int room(ServerLevel level, ServerPlayer player, HordeSchedule.Spawn spawn) {
		return room(level, player.position(), spawn);
	}

	/** {@link #room(ServerLevel, ServerPlayer, HordeSchedule.Spawn)} counted around a place. */
	public static int room(ServerLevel level, Vec3 at, HordeSchedule.Spawn spawn) {
		return room(level, at, spawn, spawn.maxAlivePerPlayer());
	}

	/**
	 * {@link #room(ServerLevel, Vec3, HordeSchedule.Spawn)} with an explicit horde cap around the place. Building
	 * populations don't count against a wave (10.3.1): they stay inside their buildings, and a base in the dense city
	 * is surrounded by enough of them to fill the Director's hard caps on their own, which left horde nights with no
	 * zombies at all. So the hard caps here weigh only the zombies that roam (ambient, horde, siege); the wave's own
	 * {@code maxHordeAlive} still bounds it.
	 */
	public static int room(ServerLevel level, Vec3 at, HordeSchedule.Spawn spawn, int maxHordeAlive) {
		int near = DirectorTracker.countNear(at.x, at.y, at.z, SpawnRules.PLAYER_CAP_RADIUS)[1];
		int buildingNear = DirectorTracker.countNear(at.x, at.y, at.z, SpawnRules.PLAYER_CAP_RADIUS,
				DirectorZombie.Source.BUILDING);
		int hordeNear = DirectorTracker.countNear(at.x, at.y, at.z, SpawnRules.PLAYER_CAP_RADIUS,
				DirectorZombie.Source.HORDE);
		int roamingTotal = DirectorTracker.total() - DirectorTracker.count(DirectorZombie.Source.BUILDING);
		int hard = SpawnRules.hardCapRoom(near - buildingNear, SpawnRules.PLAYER_HARD_CAP, roamingTotal, SpawnDirector.worldCap());
		return Math.max(0, Math.min(hard, maxHordeAlive - hordeNear));
	}

	/** A weighted pick from an unlocked pool; null (a plain walker) when nothing in it is loaded. */
	static Map.@Nullable Entry<Identifier, ZombieVariant> pick(Map<Identifier, Integer> pool, RandomSource random) {
		int total = 0;
		for (Map.Entry<Identifier, Integer> e : pool.entrySet()) {
			if (ZombieVariants.get(e.getKey()) != null) {
				total += e.getValue();
			}
		}
		if (total > 0) {
			int roll = random.nextInt(total);
			for (Map.Entry<Identifier, Integer> e : pool.entrySet()) {
				ZombieVariant variant = ZombieVariants.get(e.getKey());
				if (variant == null) {
					continue;
				}
				roll -= e.getValue();
				if (roll < 0) {
					return Map.entry(e.getKey(), variant);
				}
			}
		}
		Identifier walker = Identifier.fromNamespaceAndPath("ashfall", "walker");
		ZombieVariant fallback = ZombieVariants.get(walker);
		return fallback == null ? null : Map.entry(walker, fallback);
	}

	/**
	 * A spawn point in the ring around {@code target}: loaded, valid ground (street level first), outside every claim
	 * and haven, and out of every player's sight, so the horde is heard before it is seen. Later tries widen the ring
	 * (a claim can cover much of it: a Bunker Core's 5x5 chunks reach up to 47 blocks out) and the last half give up
	 * on sight, since a horde seen coming is better than no horde at all.
	 */
	static @Nullable BlockPos edgeSpot(ServerLevel level, Vec3 target, HordeSchedule.Spawn spawn, RandomSource random) {
		int lo = Math.min(spawn.radiusMin(), spawn.radiusMax()), hi = Math.max(spawn.radiusMin(), spawn.radiusMax());
		for (int attempt = 0; attempt < EDGE_ATTEMPTS; attempt++) {
			boolean late = attempt >= EDGE_ATTEMPTS / 2;
			int widen = late ? (hi - lo) : 0;
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = lo + widen + random.nextDouble() * (hi - lo);
			int x = Mth.floor(target.x + Math.cos(angle) * dist);
			int z = Mth.floor(target.z + Math.sin(angle) * dist);
			BlockPos spot = floor(level, x, z, Mth.floor(target.y));
			if (spot == null || Claims.isClaimed(level, spot) || Havens.protects(level, spot)
					|| (!late && SpawnDirector.visibleToAnyPlayer(level, spot))) {
				continue;
			}
			return spot;
		}
		return null;
	}

	/** Another member's spot within {@code spread} of the wave's centre. */
	private static @Nullable BlockPos near(ServerLevel level, BlockPos centre, int spread, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = centre.getX() + random.nextInt(2 * spread + 1) - spread;
			int z = centre.getZ() + random.nextInt(2 * spread + 1) - spread;
			BlockPos spot = floor(level, x, z, centre.getY());
			if (spot != null && Math.abs(spot.getY() - centre.getY()) <= 3) {
				return spot;
			}
		}
		return centre;
	}

	/** Valid ground in a loaded column: the street surface first, then the nearest floor to {@code aroundY}. */
	private static @Nullable BlockPos floor(ServerLevel level, int x, int z, int aroundY) {
		if (!LoadedChunks.ready(level, x, z)) {
			return null;
		}
		BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		if (Math.abs(surface.getY() - aroundY) <= 12 && SpawnDirector.validLand(level, surface)) {
			return surface;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, aroundY, z);
		for (int i = 0; i <= 16; i++) {
			int dy = (i & 1) == 1 ? -((i + 1) / 2) : i / 2;
			pos.setY(aroundY + dy);
			if (SpawnDirector.validLand(level, pos)) {
				return pos.immutable();
			}
		}
		return null;
	}

	// ---- breach -----------------------------------------------------------------------------------------------

	/** The siren before a breach day's nightfall, and the attack at nightfall. */
	static void breachTick(ServerLevel level, HordeSchedule schedule, long clock) {
		BreachHook hook = HordeHooks.breach();
		if (hook == BreachHook.NONE) {
			return;
		}
		int day = HordeSchedule.day(clock);
		MinecraftServer server = level.getServer();
		HordeState state = HordeState.get(server);
		int due = schedule.dueBreachDay(day);
		if (due < 0 || state.lastBreachDay() >= due || !schedule.breachNightAllowed(day, level.getSeed()) || hook.busy(server)) {
			return;
		}
		long timeOfDay = Math.floorMod(clock, HordeSchedule.DAY_TICKS);
		long warnFrom = schedule.nightStart() - schedule.breach().warningSeconds() * 20L;
		if (timeOfDay >= warnFrom && timeOfDay < schedule.nightStart() && state.breachWarnedDay() != day) {
			String target = pickTarget(server, hook, level.getRandom());
			state.warnBreach(day, target);
			if (!target.isEmpty()) {
				HordeSiren.alert(server, Component.translatable("title.ashfall.breach.warning"),
						Component.translatable("subtitle.ashfall.breach.warning", hook.name(target)));
				hook.announce(server, target);
			}
		}
		if (schedule.inNightWindow(clock)) {
			String target = state.breachWarnedDay() == day ? state.breachTarget() : "";
			if (target.isEmpty() || !hook.targets(server).contains(target)) {
				target = pickTarget(server, hook, level.getRandom());
			}
			state.setLastBreachDay(day);
			if (!target.isEmpty()) {
				begin(level, hook, target);
			}
		}
	}

	/**
	 * Starts a Breach now, outside the schedule (admin command and gametests): at {@code target}, or a random
	 * reclaimed substation when null. @return the substation hit, or null if there was none
	 */
	public static @Nullable String startBreach(ServerLevel level, @Nullable String target) {
		BreachHook hook = HordeHooks.breach();
		String id = target != null ? target : pickTarget(level.getServer(), hook, level.getRandom());
		if (id.isEmpty() || !hook.targets(level.getServer()).contains(id)) {
			return null;
		}
		return begin(level, hook, id) ? id : null;
	}

	private static boolean begin(ServerLevel level, BreachHook hook, String target) {
		if (!hook.begin(level, target)) {
			return false;
		}
		HordeSiren.alert(level.getServer(), Component.translatable("title.ashfall.breach"),
				Component.translatable("subtitle.ashfall.breach", hook.name(target)));
		hook.announce(level.getServer(), target);
		AshfallDirector.LOGGER.info("Breach: a horde hits the {} substation", target);
		return true;
	}

	private static String pickTarget(MinecraftServer server, BreachHook hook, RandomSource random) {
		List<String> targets = hook.targets(server);
		return targets.isEmpty() ? "" : targets.get(random.nextInt(targets.size()));
	}
}
