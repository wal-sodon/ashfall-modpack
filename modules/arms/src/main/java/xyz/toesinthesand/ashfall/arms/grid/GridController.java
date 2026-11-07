package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlockEntity;
import xyz.toesinthesand.ashfall.director.haven.HordeSiren;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the district loop once a second: the energize hold (with its hum, spawn pressure and bossbar), the
 * switch to ENERGIZED, the siege warning and start, and the siege itself ({@link GridSiege}).
 * <p>
 * The loop works from {@link GridState} and the core's saved position, not from the core block entity, so a
 * scheduled siege still fires on time when nobody is at the substation (it then waits for players, see
 * {@link DistrictLoop#siegeOutcome}). Everything in-world (hum, spawns, damage) needs the core's chunk loaded.
 * <p>
 * Cost: per district one record read per second; while a district holds or is besieged, one player scan and
 * one entity box query around its core.
 */
public final class GridController {
	public static final int UPDATE_TICKS = 20;
	/** Entity tag on spawn-pressure zombies, so their count near the core can be capped. */
	public static final String PRESSURE_TAG = "ashfall_grid_pressure";

	private static final Map<String, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<String, Long> LAST_HUM = new HashMap<>();
	private static final Map<String, Long> LAST_PRESSURE = new HashMap<>();

	private GridController() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 11) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			LAST_HUM.clear();
			LAST_PRESSURE.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long clock = level.getOverworldClockTime();
		GridState grid = GridState.get(server);
		for (String id : Set.copyOf(grid.records().keySet())) {
			if (!GridDistricts.isManual(id)) {
				tickDistrict(level, id, clock, UPDATE_TICKS);
			}
		}
	}

	/**
	 * One loop step for one district. Public so gametests drive their own districts with explicit clocks.
	 *
	 * @param clock the overworld day clock
	 * @param step  ticks this step stands for (the hold and siege counters advance by it)
	 */
	public static void tickDistrict(ServerLevel level, String id, long clock, long step) {
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id);
		if (r == null) {
			return;
		}
		GridSettings settings = GridDistricts.settings(id);
		LoopRules rules = settings.rules();
		BlockPos core = r.hasCore() ? BlockPos.of(r.corePos) : null;
		boolean loaded = core != null && level.hasChunkAt(core);
		switch (r.state) {
			case REPAIRED -> {
				if (!r.energizing(rules)) {
					hideBar(id);
					return;
				}
				if (!r.holdDone(rules) && loaded) {
					boolean near = playersNear(level, core, settings.holdRadius());
					grid.update(level, id, rec -> DistrictLoop.tickHold(rec, rules, near, step));
					hum(level, id, core, settings, level.getGameTime());
					if (near) {
						pressure(level, id, core, settings, level.getGameTime());
					}
				}
				holdBar(level, id, r, rules, core, settings);
				checkEnergized(level, id, clock);
			}
			case ENERGIZED -> {
				hideBar(id);
				if (DistrictLoop.shouldWarn(r, rules, clock)) {
					grid.update(level, id, rec -> rec.warned = true);
					HordeSiren.alert(level.getServer(), Component.translatable("title.ashfall.grid.siege_warning"),
							Component.translatable("subtitle.ashfall.grid.siege_warning", GridDistricts.name(id)));
				}
				boolean[] start = new boolean[1];
				grid.update(level, id, rec -> start[0] = DistrictLoop.shouldStart(rec, rules, clock));
				if (start[0]) {
					int coreHp = GridSiege.coreHp(level, id);
					grid.update(level, id, rec -> DistrictLoop.startSiege(rec, rules, coreHp));
					HordeSiren.alert(level.getServer(), Component.translatable("title.ashfall.grid.siege"),
							Component.translatable("subtitle.ashfall.grid.siege", GridDistricts.name(id)));
					AshfallArms.LOGGER.info("Grid: District Siege started at {} (tier {})", id, GridSiege.tier(level, id));
				}
			}
			case SIEGE -> GridSiege.tick(level, id, clock, step);
			default -> hideBar(id);
		}
	}

	/** Switches a holding district to ENERGIZED once its hold is done and every key relay is linked. */
	public static void checkEnergized(ServerLevel level, String id, long clock) {
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id);
		if (r == null) {
			return;
		}
		LoopRules rules = GridDistricts.settings(id).rules();
		if (!DistrictLoop.readyToEnergize(r, rules, GridDistricts.keyRelayChunks(id))) {
			return;
		}
		grid.update(level, id, rec -> DistrictLoop.energize(rec, clock));
		hideBar(id);
		GridActions.broadcast(level, Component.translatable("message.ashfall.grid.energized", GridDistricts.name(id))
				.withStyle(ChatFormatting.GOLD));
		AshfallArms.LOGGER.info("Grid: {} energized, siege at clock {}", id, grid.record(id).siegeAt);
	}

	/** Whether a living, non-spectator player is within {@code radius} blocks of {@code pos}. */
	public static boolean playersNear(ServerLevel level, BlockPos pos, double radius) {
		Vec3 c = Vec3.atCenterOf(pos);
		double r2 = radius * radius;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(c) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** The core's hum: the relay hum sound and a SOUND stimulus at {@code hum_radius}, every few seconds. */
	static void hum(ServerLevel level, String id, BlockPos core, GridSettings settings, long now) {
		Long last = LAST_HUM.get(id);
		if (last != null && now - last < settings.humIntervalSeconds() * 20L && now >= last) {
			return;
		}
		LAST_HUM.put(id, now);
		level.playSound(null, core, ChargerBlockEntity.RELAY_HUM, SoundSource.BLOCKS, 2.0f, 0.7f);
		Stimuli.emit(level, Vec3.atCenterOf(core), StimulusType.SOUND, settings.humRadius(), null);
	}

	/** A few Director zombies, out of sight, around a holding core (capped by the pressure zombies already there). */
	static void pressure(ServerLevel level, String id, BlockPos core, GridSettings settings, long now) {
		GridSettings.Pressure p = settings.pressure();
		if (p.count() <= 0 || !SpawnDirector.active(level)) {
			return;
		}
		Long last = LAST_PRESSURE.get(id);
		if (last != null && now - last < p.intervalSeconds() * 20L && now >= last) {
			return;
		}
		LAST_PRESSURE.put(id, now);
		AABB box = new AABB(core).inflate(p.radiusMax() + 16);
		int alive = level.getEntitiesOfClass(Zombie.class, box, z -> z.isAlive() && z.entityTags().contains(PRESSURE_TAG)).size();
		int room = Math.min(p.count(), p.maxAlive() - alive);
		for (int i = 0; i < room; i++) {
			Zombie zombie = GridSiege.spawnAround(level, core, p.radiusMin(), p.radiusMax(), GridSiege.pickVariant(level, id),
					DirectorZombie.Source.AMBIENT, true);
			if (zombie != null) {
				zombie.addTag(PRESSURE_TAG);
				zombie.getNavigation().moveTo(core.getX() + 0.5, core.getY(), core.getZ() + 0.5, 1.0);
			}
		}
	}

	private static void holdBar(ServerLevel level, String id, DistrictRecord r, LoopRules rules, BlockPos core, GridSettings settings) {
		if (core == null) {
			return;
		}
		ServerBossEvent bar = bar(id);
		Set<Long> keySet = GridDistricts.keyRelayChunks(id);
		int keys = rules.relaysRequired(keySet);
		float progress = rules.holdTicks() <= 0 ? 1f : Math.clamp(r.holdTicks / (float) rules.holdTicks(), 0f, 1f);
		boolean paused = !r.holdDone(rules) && !playersNear(level, core, settings.holdRadius());
		bar.setName(Component.translatable(paused ? "bossbar.ashfall.grid.hold_paused" : "bossbar.ashfall.grid.hold",
				GridDistricts.name(id), Math.round(progress * 100), LoopRules.linkedCount(r.linked, keySet), keys));
		bar.setColor(paused ? BossEvent.BossBarColor.WHITE : BossEvent.BossBarColor.YELLOW);
		bar.setProgress(progress);
		showNear(level, bar, core, settings.holdRadius() * 2.0);
	}

	/** The district's bossbar (one per district: hold progress, then siege core health). */
	static ServerBossEvent bar(String id) {
		return BARS.computeIfAbsent(id, k -> new ServerBossEvent(
				UUID.nameUUIDFromBytes(("ashfall:grid/" + k).getBytes(StandardCharsets.UTF_8)), Component.empty(),
				BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS));
	}

	/** Shows {@code bar} to the players within {@code radius} of {@code pos} and hides it from everyone else. */
	static void showNear(ServerLevel level, ServerBossEvent bar, BlockPos pos, double radius) {
		Vec3 c = Vec3.atCenterOf(pos);
		Set<ServerPlayer> wanted = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(c) <= radius * radius) {
				wanted.add(p);
			}
		}
		for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
			if (!wanted.remove(p)) {
				bar.removePlayer(p);
			}
		}
		wanted.forEach(bar::addPlayer);
	}

	static void hideBar(String id) {
		ServerBossEvent bar = BARS.get(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** The district's bossbar if it exists (gametests). */
	public static ServerBossEvent barForTesting(String id) {
		return BARS.get(id);
	}
}
