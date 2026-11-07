package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A reproducible "lag near zombies" benchmark: a survival player standing in a small multi-storey city block at
 * night while N Director zombies (walkers, runners, armored, patients and every special infected kind) hunt them,
 * with the server tick time measured per phase.
 * <p>
 * Why it exists: earlier headless soak tests ran 120 zombies at 20 TPS, but with no player connected, so none of the
 * AI that only wakes up near a player (senses, targeting, pathing into buildings, hordes, specials, the spawn loop's
 * sight checks) was ever measured. This puts a real {@link ServerPlayer} in the player list so chunk tracking,
 * entity tracking and every player-driven system run as they do in singleplayer.
 * <p>
 * Off by default: without {@code -Dashfall.bench=true} the test succeeds on its first tick, so the normal
 * {@code ./gradlew build} pays nothing for it. Run it with {@code ./gradlew :arms:runGameTest -PashfallBench}
 * (see {@code arms/build.gradle}), which also limits the run to this class and can attach Java Flight Recorder.
 * <p>
 * Output: one log line per phase ({@code [ashfall-bench]}) and a CSV at {@code -Dashfall.bench.out} (default
 * {@code bench-results.csv} in the run directory) with wall-clock phase windows, so a JFR recording can be cut
 * per phase.
 */
public class ZombieLoadBenchmark {
	private static final Logger LOGGER = LoggerFactory.getLogger("ashfall-bench");

	/** Ticks to let pathing and targeting settle after spawning before measuring. */
	private static final int WARMUP_TICKS = Integer.getInteger("ashfall.bench.warmup", 100);
	/** Measured ticks per phase (~30 s of game time, the user's "near zombies" window). */
	private static final int MEASURE_TICKS = Integer.getInteger("ashfall.bench.measure", 600);
	/** Singleplayer's default render distance and simulation distance. */
	private static final int VIEW_DISTANCE = Integer.getInteger("ashfall.bench.viewDistance", 12);
	private static final int SIMULATION_DISTANCE = Integer.getInteger("ashfall.bench.simulationDistance", 12);
	/** Ticks between blaster shots in the noisy phases. */
	private static final int SHOT_INTERVAL = 20;
	/** Half the side of the street grid around the player. */
	private static final int CITY_HALF = 44;
	/** Building pitch (footprint plus street) and footprint. */
	private static final int PITCH = 22;
	private static final int FOOTPRINT = 14;
	private static final int STOREYS = 3;
	private static final int STOREY_HEIGHT = 4;

	/** One measured phase: zombie count and whether the player makes noise and light. */
	private record Phase(String name, int zombies, boolean noisy) {
	}

	/** Tick timer shared with the server tick events; registered once per JVM because events cannot unregister. */
	private static final class TickTimer {
		private static boolean registered;
		private static long start;
		private static long[] samples = new long[0];
		private static int count;
		private static boolean recording;
		/** The running benchmark's per-tick step, or null. */
		private static Runnable driver;

		static void ensureRegistered() {
			if (registered) {
				return;
			}
			registered = true;
			ServerTickEvents.START_SERVER_TICK.register(server -> start = System.nanoTime());
			// The step runs inside the measured span (it is part of what a player's tick costs: healing, shooting).
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				Runnable step = driver;
				if (step != null) {
					step.run();
				}
				if (recording && count < samples.length) {
					samples[count++] = System.nanoTime() - start;
				}
			});
		}

		static void begin(int ticks) {
			samples = new long[ticks];
			count = 0;
			recording = true;
		}

		static long[] end() {
			recording = false;
			return Arrays.copyOf(samples, count);
		}
	}

	@GameTest(maxTicks = 40000, skyAccess = true)
	public void zombieLoad(GameTestHelper helper) {
		if (!Boolean.getBoolean("ashfall.bench")) {
			helper.succeed();
			return;
		}
		TickTimer.ensureRegistered();
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Difficulty oldDifficulty = server.getWorldData().getDifficulty();
		server.setDifficulty(Difficulty.NORMAL, true);
		GameRules rules = level.getGameRules();
		// Fixed N per phase: the Director's own ambient loop would add zombies and blur the numbers.
		rules.set(SpawnDirector.ENABLED, false, server);
		rules.set(GameRules.ADVANCE_TIME, false, server);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set midnight");

		// The gametest server never sets view/simulation distance (a dedicated server reads them from
		// server.properties), so a player there has no entity-ticking ring at all. Use the singleplayer defaults.
		int oldView = server.getPlayerList().getViewDistance();
		int oldSim = server.getPlayerList().getSimulationDistance();
		LOGGER.info("[ashfall-bench] gametest view distance {}, simulation distance {}; using {} / {}", oldView, oldSim,
				VIEW_DISTANCE, SIMULATION_DISTANCE);
		server.getPlayerList().setViewDistance(VIEW_DISTANCE);
		server.getPlayerList().setSimulationDistance(SIMULATION_DISTANCE);

		BlockPos centre = helper.absolutePos(new BlockPos(0, 0, 0));
		buildCity(level, centre);
		ServerPlayer player = survivalPlayer(level);
		// Ground floor of the middle building, north room, a few blocks inside its open door: reachable from the street.
		player.snapTo(centre.getX() + 0.5, centre.getY() + 1, centre.getZ() - 3.5, 0f, 0f);
		// A real client's move packet is what moves a player's chunk tickets; this mock has no client, so without
		// this its tickets stay at world spawn and the zombies around it are never entity-ticked (they just stand).
		level.getChunkSource().move(player);
		toughen(player);

		List<Phase> phases = phases();
		List<String> csv = new ArrayList<>();
		csv.add("phase,zombies_requested,zombies_spawned,zombies_alive_end,targeting_player,ticks,avg_ms,p50_ms,p95_ms,p99_ms,max_ms,"
				+ "tps_equivalent,start_epoch_ms,end_epoch_ms,entity_ticking,navigating,mean_dist_to_player");
		RandomSource random = RandomSource.create(42L);
		int[] state = {0, -1, 0, 0}; // phase index, tick within phase (-1 = set up), spawned, shot counter
		long[] window = {0L};
		// Driven from our own tick event, not GameTestHelper.onEachTick: that schedules one runAtTickTime entry for
		// every tick up to maxTicks (40 000 here), and GameTestInfo walks that whole map every tick, which showed up
		// as a third of the server thread in the first JFR run.
		Runnable finish = () -> {
			TickTimer.driver = null;
			clearZombies(level, centre);
			server.getPlayerList().remove(player);
			server.setDifficulty(oldDifficulty, true);
			rules.set(SpawnDirector.ENABLED, true, server);
			server.getPlayerList().setViewDistance(oldView);
			server.getPlayerList().setSimulationDistance(oldSim);
			writeCsv(csv);
			helper.succeed();
		};
		TickTimer.driver = () -> {
			try {
				step(level, player, centre, phases, state, window, csv, random, finish);
			} catch (RuntimeException e) {
				TickTimer.driver = null;
				TickTimer.end();
				LOGGER.error("[ashfall-bench] benchmark step failed", e);
				helper.fail("benchmark step failed: " + e);
			}
		};
	}

	private static void step(ServerLevel level, ServerPlayer player, BlockPos centre, List<Phase> phases, int[] state, long[] window,
							 List<String> csv, RandomSource random, Runnable finish) {
		if (state[0] >= phases.size()) {
			finish.run();
			return;
		}
		toughen(player);
		level.getChunkSource().move(player);
		Phase phase = phases.get(state[0]);
		int t = state[1];
		if (t < 0) {
			// The whole spawn ring must be entity-ticking before a phase starts (the chunk tickets settle over a few
			// ticks after the move above), or the first phase would measure idle zombies.
			if (!cityTicking(level, centre)) {
				return;
			}
			clearZombies(level, centre);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
					phase.noisy() ? new ItemStack(Items.TORCH) : ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.MAIN_HAND, phase.noisy() ? loadedBlaster() : ItemStack.EMPTY);
			state[2] = spawnMix(level, centre, phase.zombies(), random);
			state[1] = 0;
			return;
		}
		if (phase.noisy() && t % SHOT_INTERVAL == 0) {
			fire(level, player, random);
		}
		if (t == WARMUP_TICKS) {
			TickTimer.begin(MEASURE_TICKS);
			window[0] = System.currentTimeMillis();
		}
		if (t == WARMUP_TICKS + MEASURE_TICKS) {
			long[] nanos = TickTimer.end();
			long endMs = System.currentTimeMillis();
			List<Zombie> alive = zombies(level, centre);
			int targeting = 0;
			int ticking = 0;
			int navigating = 0;
			double toPlayer = 0;
			for (Zombie z : alive) {
				if (z.getTarget() == player) {
					targeting++;
				}
				if (level.isPositionEntityTicking(z.blockPosition())) {
					ticking++;
				}
				if (!z.getNavigation().isDone()) {
					navigating++;
				}
				toPlayer += z.distanceTo(player);
			}
			// Sanity columns: a phase whose zombies are not entity-ticking, or never move, measures nothing.
			String row = summarise(phase, state[2], alive.size(), targeting, nanos, window[0], endMs)
					+ String.format(Locale.ROOT, ",%d,%d,%.1f", ticking, navigating, alive.isEmpty() ? 0 : toPlayer / alive.size());
			csv.add(row);
			LOGGER.info("[ashfall-bench] {}", row);
			state[0]++;
			state[1] = -1;
			return;
		}
		state[1]++;
	}

	/** Phases from {@code -Dashfall.bench.counts} (default 0,20,40,80) quiet, then 0,40,80 with torch and blaster. */
	private static List<Phase> phases() {
		String counts = System.getProperty("ashfall.bench.counts", "0,20,40,80");
		String noisy = System.getProperty("ashfall.bench.noisyCounts", "0,40,80");
		List<Phase> out = new ArrayList<>();
		for (String c : counts.split(",")) {
			if (!c.isBlank()) {
				out.add(new Phase("quiet_" + c.trim(), Integer.parseInt(c.trim()), false));
			}
		}
		for (String c : noisy.split(",")) {
			if (!c.isBlank()) {
				out.add(new Phase("noisy_" + c.trim(), Integer.parseInt(c.trim()), true));
			}
		}
		return out;
	}

	private static String summarise(Phase phase, int spawned, int alive, int targeting, long[] nanos, long startMs, long endMs) {
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		double sum = 0;
		for (long n : sorted) {
			sum += n;
		}
		double avg = sorted.length == 0 ? 0 : sum / sorted.length / 1e6;
		double tps = avg <= 50.0 ? 20.0 : 1000.0 / avg;
		return String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%d,%d", phase.name(), phase.zombies(), spawned,
				alive, targeting, sorted.length, avg, pct(sorted, 0.50), pct(sorted, 0.95), pct(sorted, 0.99),
				sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6, tps, startMs, endMs);
	}

	private static double pct(long[] sorted, double p) {
		if (sorted.length == 0) {
			return 0;
		}
		int i = Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1);
		return sorted[Math.max(0, i)] / 1e6;
	}

	private static void writeCsv(List<String> rows) {
		Path out = Path.of(System.getProperty("ashfall.bench.out", "bench-results.csv"));
		try {
			if (out.getParent() != null) {
				Files.createDirectories(out.getParent());
			}
			Files.write(out, rows);
			LOGGER.info("[ashfall-bench] wrote {}", out.toAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("[ashfall-bench] could not write {}", out, e);
		}
	}

	/**
	 * A survival player in the player list, as in {@code HordeGameTest}: placed through {@code placeNewPlayer} so the
	 * chunk map, entity tracker and every per-player system see a real player.
	 */
	private static ServerPlayer survivalPlayer(ServerLevel level) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "bench-player"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.getAbilities().invulnerable = false;
		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.getAbilities().instabuild = false;
		return player;
	}

	/**
	 * Keeps the player alive without making them invulnerable: an invulnerable player is not a valid target, which
	 * would switch off exactly the hunting AI this benchmark is about. Resistance V cancels all damage instead.
	 */
	private static void toughen(ServerPlayer player) {
		if (!player.hasEffect(MobEffects.RESISTANCE)) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 4, false, false));
		}
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
	}

	private static ItemStack loadedBlaster() {
		ItemStack gun = new ItemStack(ArmsItems.SPARK_PISTOL);
		gun.set(ArmsComponents.LOADED_PACK,
				ItemStackTemplate.fromNonEmptyStack(EnergyItems.withEnergy(new ItemStack(ArmsItems.CELL_PACK_HEAVY), 120)));
		return gun;
	}

	/** One shot into the sky in a random direction: all the noise of a gunshot without thinning the horde. */
	private static void fire(ServerLevel level, ServerPlayer player, RandomSource random) {
		player.setYRot(random.nextFloat() * 360f);
		player.setXRot(-50f);
		ItemStack gun = player.getMainHandItem();
		if (GunLogic.tryFire(level, player, gun) == GunLogic.FireResult.EMPTY) {
			player.setItemInHand(InteractionHand.MAIN_HAND, loadedBlaster());
		}
	}

	private static boolean cityTicking(ServerLevel level, BlockPos centre) {
		for (int dx = -40; dx <= 40; dx += 40) {
			for (int dz = -40; dz <= 40; dz += 40) {
				if (!level.isPositionEntityTicking(centre.offset(dx, 1, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	private static AABB area(BlockPos centre) {
		return new AABB(centre).inflate(CITY_HALF + 16, 32, CITY_HALF + 16);
	}

	private static List<Zombie> zombies(ServerLevel level, BlockPos centre) {
		return level.getEntitiesOfClass(Zombie.class, area(centre), Entity::isAlive);
	}

	private static void clearZombies(ServerLevel level, BlockPos centre) {
		for (Zombie z : zombies(level, centre)) {
			z.discard();
		}
	}

	/**
	 * Spawns {@code n} zombies through the real Director path at 8-40 blocks: one of each special infected kind per
	 * 20 zombies (capped at two of each), the rest drawn from walker 50 %, runner 25 %, armored 15 %, patient 10 %.
	 * Spots are found like the Director's column scan, so some land inside the closed buildings and on roofs, as they
	 * do in a Lost Cities block.
	 */
	private static int spawnMix(ServerLevel level, BlockPos centre, int n, RandomSource random) {
		if (n <= 0) {
			return 0;
		}
		List<Object> plan = new ArrayList<>();
		int specialSets = Math.min(2, Math.max(1, n / 20));
		for (int s = 0; s < specialSets; s++) {
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
				z.setPersistenceRequired();
				spawned++;
			}
		}
		return spawned;
	}

	/** A valid spawn floor 8-40 blocks from the centre, scanning each column top down; null after 40 misses. */
	private static BlockPos spot(ServerLevel level, BlockPos centre, RandomSource random) {
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 8 + random.nextDouble() * 32;
			int x = centre.getX() + (int) Math.round(Math.cos(angle) * dist);
			int z = centre.getZ() + (int) Math.round(Math.sin(angle) * dist);
			List<BlockPos> floors = new ArrayList<>();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
			for (int y = centre.getY() + STOREYS * STOREY_HEIGHT + 1; y > centre.getY(); y--) {
				pos.setY(y);
				if (SpawnDirector.validLand(level, pos)) {
					floors.add(pos.immutable());
				}
			}
			if (!floors.isEmpty()) {
				return floors.get(random.nextInt(floors.size()));
			}
		}
		return null;
	}

	/**
	 * A 5x5 block of 3-storey buildings on a stone street grid: brick walls, plank floors, glass-pane windows, a
	 * staircase per building and oak doors (closed, except the player's building), with no lights anywhere.
	 * {@code centre} is street level (the floor's top face is centre.y + 1 minus one, i.e. blocks sit at centre.y).
	 */
	private static void buildCity(ServerLevel level, BlockPos centre) {
		int y0 = centre.getY();
		int top = y0 + STOREYS * STOREY_HEIGHT + 6;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState street = Blocks.STONE.defaultBlockState();
		for (int x = -CITY_HALF - 8; x <= CITY_HALF + 8; x++) {
			for (int z = -CITY_HALF - 8; z <= CITY_HALF + 8; z++) {
				set(level, centre.getX() + x, y0, centre.getZ() + z, street);
				for (int y = y0 + 1; y <= top; y++) {
					set(level, centre.getX() + x, y, centre.getZ() + z, air);
				}
			}
		}
		int half = FOOTPRINT / 2;
		for (int gx = -2; gx <= 2; gx++) {
			for (int gz = -2; gz <= 2; gz++) {
				int bx = centre.getX() + gx * PITCH;
				int bz = centre.getZ() + gz * PITCH;
				boolean playerHouse = gx == 0 && gz == 0;
				building(level, bx - half, y0, bz - half, playerHouse);
			}
		}
	}

	private static void building(ServerLevel level, int x0, int y0, int z0, boolean openDoors) {
		BlockState wall = Blocks.BRICKS.defaultBlockState();
		BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		int x1 = x0 + FOOTPRINT - 1;
		int z1 = z0 + FOOTPRINT - 1;
		for (int s = 0; s <= STOREYS; s++) {
			int fy = y0 + s * STOREY_HEIGHT;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					set(level, x, fy, z, s == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : floor);
				}
			}
			if (s == STOREYS) {
				break;
			}
			for (int y = fy + 1; y < fy + STOREY_HEIGHT; y++) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
						if (!edge) {
							continue;
						}
						boolean window = y == fy + 2 && ((x - x0) % 3 == 1 || (z - z0) % 3 == 1)
								&& !(x == x0 && z == z0) && !(x == x1 && z == z1) && !(x == x0 && z == z1) && !(x == x1 && z == z0);
						set(level, x, y, z, window ? pane : wall);
					}
				}
			}
			// an inner wall across the middle with a gap, so pathing has rooms to go round
			int mz = z0 + FOOTPRINT / 2;
			for (int x = x0 + 1; x < x1; x++) {
				for (int y = fy + 1; y < fy + STOREY_HEIGHT; y++) {
					boolean gap = x == x0 + 3 || x == x0 + 4;
					set(level, x, y, mz, gap ? Blocks.AIR.defaultBlockState() : wall);
				}
			}
		}
		// Staircases in a second pass, so a later storey's floor does not fill the stairwell cut for the one below.
		for (int s = 0; s < STOREYS; s++) {
			int fy = y0 + s * STOREY_HEIGHT;
			for (int i = 0; i < STOREY_HEIGHT; i++) {
				int sx = x1 - 1;
				int sz = z0 + 2 + i;
				set(level, sx, fy + 1 + i, sz, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
				for (int clear = fy + 2 + i; clear <= fy + 3 + i; clear++) {
					set(level, sx, clear, sz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		// doors in the middle of the north and south walls on the ground floor
		door(level, x0 + FOOTPRINT / 2, y0 + 1, z0, Direction.NORTH, openDoors);
		door(level, x0 + FOOTPRINT / 2, y0 + 1, z1, Direction.SOUTH, openDoors);
	}

	private static void door(ServerLevel level, int x, int y, int z, Direction facing, boolean open) {
		BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, open)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		set(level, x, y, z, lower);
		set(level, x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** Flag 2 (send to clients, no neighbour updates): keeps doors and panes as placed. */
	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}
}
