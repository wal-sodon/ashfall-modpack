package xyz.toesinthesand.ashfall.director.test;

import mcjty.lostcities.setup.Config;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorTracker;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The user's situation as closely as a test can get: a real client on the integrated server, a fresh Ashfall-profile
 * Lost Cities world, night, survival, the Director's own spawning on, then extra Director zombies around the player,
 * then flying through new city chunks the way a creative player scouting (or running Chunky) does. Each segment's
 * server tick times are recorded like {@code ZombieLoadBenchmark}'s.
 * <p>
 * Why a client test as well as the server gametest: the gametest server builds a superflat level, so the Lost Cities
 * generator and every Ashfall system that reads city data (zones, building populations, the city atlas) never run
 * there. Here they do.
 * <p>
 * Lives in the director module because Fabric only filters client gametests by mod id, and director has no other
 * client gametest, so {@code ./gradlew :director:runClientGameTest -PashfallBench} runs this alone. It does nothing
 * unless {@code -Dashfall.bench=true} is set.
 */
public class ZombieLoadClientBenchmark implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("ashfall-bench");
	private static final int STAND_TICKS = Integer.getInteger("ashfall.bench.standTicks", 1200);
	private static final int MEASURE_TICKS = Integer.getInteger("ashfall.bench.measure", 600);
	private static final int EXTRA_ZOMBIES = Integer.getInteger("ashfall.bench.extra", 40);
	/** A tick at least this long is logged individually (two and a half ticks' budget). */
	private static final long SLOW_TICK_MS = 125;
	/** Blocks per tick while "flying": 1 = 20 blocks/s, a sprint-flying creative player. */
	private static final int FLY_STEP = Integer.getInteger("ashfall.bench.flyStep", 1);

	/** Server tick timer; registered once (events cannot unregister), records only between begin and end. */
	private static final class TickTimer {
		private static boolean registered;
		private static long start;
		private static long[] samples = new long[0];
		private static volatile int count;
		private static volatile boolean recording;
		/** Ticks over {@link #SLOW_TICK_MS}: wall-clock end and duration, so a JFR recording can be cut to just them. */
		private static final List<long[]> slow = new ArrayList<>();

		static void ensureRegistered() {
			if (registered) {
				return;
			}
			registered = true;
			ServerTickEvents.START_SERVER_TICK.register(server -> start = System.nanoTime());
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				if (recording && count < samples.length) {
					long took = System.nanoTime() - start;
					samples[count] = took;
					count = count + 1;
					if (took >= SLOW_TICK_MS * 1_000_000L) {
						synchronized (slow) {
							slow.add(new long[]{System.currentTimeMillis(), took / 1_000_000L});
						}
					}
				}
			});
		}

		static synchronized void begin(int max) {
			samples = new long[max];
			count = 0;
			recording = true;
		}

		static synchronized long[] end() {
			recording = false;
			return Arrays.copyOf(samples, count);
		}
	}

	private final List<String> csv = new ArrayList<>();
	/** One row per slow tick, same window columns as the main CSV so jfr_hotspots.py can filter to them. */
	private final List<String> slowCsv = new ArrayList<>(List.of("phase,start_epoch_ms,end_epoch_ms,dur_ms"));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("ashfall.bench")) {
			return;
		}
		TickTimer.ensureRegistered();
		csv.add("segment,ticks,avg_ms,p50_ms,p95_ms,p99_ms,max_ms,director_zombies_total,zombies_within_48,targeting_player,"
				+ "start_epoch_ms,end_epoch_ms");
		Config.profileFromClient = LostCityContext.PROFILE;
		Config.resetProfileCache();
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(ui -> ui.setSeed(System.getProperty("ashfall.bench.seed", "ashfall-bench")))
				.create()) {
			TestServerContext server = world.getServer();
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("time set midnight");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("gamerule minecraft:advance_weather false");
			// Keep the player alive and targetable (Resistance V cancels damage; creative/invulnerable would not be hunted).
			server.runCommand("effect give @a minecraft:resistance infinite 4 true");
			server.runCommand("effect give @a minecraft:saturation infinite 0 true");
			context.waitTicks(200);

			measure(context, server, "director_ambient_stand", STAND_TICKS, null);

			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
				int spawned = spawnMix(s.overworld(), player.blockPosition(), EXTRA_ZOMBIES, RandomSource.create(42L));
				LOGGER.info("[ashfall-bench] spawned {} extra zombies around the player", spawned);
			});
			context.waitTicks(100);
			measure(context, server, "plus_" + EXTRA_ZOMBIES + "_zombies_torch", MEASURE_TICKS, null);

			// Scouting: creative flight through new Lost Cities chunks, as in the user's log (creative, Chunky, flying).
			server.runCommand("gamemode creative @a");
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.teleportTo(p.getX(), p.getY() + 20, p.getZ());
			});
			measure(context, server, "creative_fly_new_chunks", MEASURE_TICKS, s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				p.teleportTo(p.getX() + FLY_STEP, p.getY(), p.getZ());
			});

			server.runCommand("gamemode survival @a");
			measure(context, server, "after_fly_stand", MEASURE_TICKS, null);
		}
		writeCsv();
	}

	/** Runs {@code ticks} client ticks (the server is lock-stepped to them), optionally doing {@code each} per tick. */
	private void measure(ClientGameTestContext context, TestServerContext server, String name, int ticks,
						 java.util.function.Consumer<MinecraftServer> each) {
		long startMs = System.currentTimeMillis();
		TickTimer.begin(ticks * 4 + 100);
		if (each == null) {
			context.waitTicks(ticks);
		} else {
			for (int i = 0; i < ticks; i++) {
				server.runOnServer(each::accept);
				context.waitTick();
			}
		}
		long[] nanos = TickTimer.end();
		long endMs = System.currentTimeMillis();
		int[] counts = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
			ServerLevel level = s.overworld();
			List<Zombie> near = level.getEntitiesOfClass(Zombie.class, new AABB(p.blockPosition()).inflate(48), Zombie::isAlive);
			int targeting = 0;
			for (Zombie z : near) {
				if (z.getTarget() == p) {
					targeting++;
				}
			}
			return new int[]{DirectorTracker.total(), near.size(), targeting};
		});
		String row = summarise(name, nanos, counts, startMs, endMs);
		csv.add(row);
		synchronized (TickTimer.slow) {
			for (long[] tick : TickTimer.slow) {
				slowCsv.add(String.format(Locale.ROOT, "%s,%d,%d,%d", name, tick[0] - tick[1], tick[0], tick[1]));
			}
			TickTimer.slow.clear();
		}
		LOGGER.info("[ashfall-bench] {}", row);
	}

	private static String summarise(String name, long[] nanos, int[] counts, long startMs, long endMs) {
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		double sum = 0;
		for (long n : sorted) {
			sum += n;
		}
		double avg = sorted.length == 0 ? 0 : sum / sorted.length / 1e6;
		return String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d", name, sorted.length, avg, pct(sorted, 0.5),
				pct(sorted, 0.95), pct(sorted, 0.99), sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6, counts[0], counts[1],
				counts[2], startMs, endMs);
	}

	private static double pct(long[] sorted, double p) {
		if (sorted.length == 0) {
			return 0;
		}
		return sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1))] / 1e6;
	}

	private void writeCsv() {
		Path out = Path.of(System.getProperty("ashfall.bench.out", "bench-client-results.csv"));
		try {
			if (out.getParent() != null) {
				Files.createDirectories(out.getParent());
			}
			Files.write(out, csv);
			Files.write(out.resolveSibling(out.getFileName() + ".slow.csv"), slowCsv);
			LOGGER.info("[ashfall-bench] wrote {}", out.toAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("[ashfall-bench] could not write {}", out, e);
		}
	}

	/** Same mix as the server benchmark: one of each special per 20, then walker/runner/armored/patient 50/25/15/10. */
	private static int spawnMix(ServerLevel level, BlockPos centre, int n, RandomSource random) {
		List<Object> plan = new ArrayList<>();
		int sets = Math.min(2, Math.max(1, n / 20));
		for (int s = 0; s < sets; s++) {
			plan.addAll(Arrays.asList(SpecialKind.values()));
		}
		while (plan.size() > n) {
			plan.removeLast();
		}
		String[] names = {"walker", "runner", "armored", "patient"};
		double[] weights = {0.50, 0.25, 0.15, 0.10};
		while (plan.size() < n) {
			double roll = random.nextDouble();
			int i = 0;
			while (i < weights.length - 1 && roll >= weights[i]) {
				roll -= weights[i];
				i++;
			}
			plan.add(names[i]);
		}
		int spawned = 0;
		for (Object entry : plan) {
			BlockPos pos = spot(level, centre, random);
			if (pos == null) {
				continue;
			}
			Zombie z;
			if (entry instanceof SpecialKind kind) {
				z = SpecialInfected.spawn(level, pos, kind, DirectorZombie.Source.AMBIENT);
			} else {
				Map.Entry<Identifier, ZombieVariant> variant = ZombieVariants.byName((String) entry);
				z = DirectorSpawns.spawn(level, pos, variant, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING);
			}
			if (z != null) {
				spawned++;
			}
		}
		return spawned;
	}

	/** A Director-valid floor 8-40 blocks away, scanning 12 blocks either side of the player's height. */
	private static BlockPos spot(ServerLevel level, BlockPos centre, RandomSource random) {
		for (int attempt = 0; attempt < 60; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 8 + random.nextDouble() * 32;
			int x = centre.getX() + (int) Math.round(Math.cos(angle) * dist);
			int z = centre.getZ() + (int) Math.round(Math.sin(angle) * dist);
			if (!level.hasChunkAt(new BlockPos(x, centre.getY(), z))) {
				continue;
			}
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
			for (int y = centre.getY() + 12; y >= centre.getY() - 12; y--) {
				pos.setY(y);
				if (SpawnDirector.validLand(level, pos)) {
					return pos.immutable();
				}
			}
		}
		return null;
	}
}
