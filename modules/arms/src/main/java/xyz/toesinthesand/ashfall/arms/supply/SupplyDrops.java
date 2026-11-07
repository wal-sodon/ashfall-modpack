package xyz.toesinthesand.ashfall.arms.supply;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.spawn.SpawnContext;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariant;
import xyz.toesinthesand.ashfall.director.spawn.ZombieVariants;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.atlas.RelayPlan;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Supply drops: every {@link #MIN_GAP_DAYS}-{@link #MAX_GAP_DAYS} days, on the morning clock, a crate comes down at one
 * of the fixed {@link #POINTS} in the city. Everyone hears where, and a boss bar counts down {@link #DEADLINE_TICKS};
 * reach it in time and the loot is yours, miss it and the crate is lost.
 * <p>
 * Medium risk: the crate only lands once a player is within {@link #LAND_RADIUS} (its chunk must be loaded to find
 * the street), and when it does it crashes loudly ({@link #NOISE_RADIUS}) with {@link #GUARDS_MIN}-{@link #GUARDS_MAX}
 * zombies already around it. Drops come by day, so they never meet a horde night or a siege.
 */
public final class SupplyDrops {
	/** A fixed landing point, by district. */
	public record Point(String district, int x, int z) {
	}

	/** Three points per district, away from its substation, so drops pull players across the whole map. */
	public static final List<Point> POINTS = List.of(
			new Point("river_arts", -950, 120), new Point("river_arts", -780, 260), new Point("river_arts", -1000, -80),
			new Point("south_slope", 60, 880), new Point("south_slope", 220, 760), new Point("south_slope", -60, 720),
			new Point("downtown", -120, 60), new Point("downtown", 180, -120), new Point("downtown", 40, 180),
			new Point("mission", 700, 600), new Point("mission", 880, 700), new Point("mission", 760, 520));
	public static final int FIRST_DAY = 2;
	public static final int MIN_GAP_DAYS = 2, MAX_GAP_DAYS = 3;
	/** Time of day a drop may start (morning), and the latest, so the deadline runs out before dusk. */
	public static final long START_FROM = 1_000L, START_UNTIL = 6_000L;
	public static final long DEADLINE_TICKS = 10 * 60 * 20L;
	public static final int LAND_RADIUS = 96;
	public static final float NOISE_RADIUS = 64f;
	public static final int GUARDS_MIN = 6, GUARDS_MAX = 10, GUARD_RADIUS = 10;
	public static final Identifier TABLE = Identifier.fromNamespaceAndPath("ashfall", "supply_drop");
	private static final int UPDATE_TICKS = 20;
	private static final UUID BAR_ID = UUID.fromString("0b6f6f3e-2b7d-4f0a-9a3b-5d2a1e7c9f10");

	private static ServerBossEvent bar;

	private SupplyDrops() {
	}

	public static void init() {
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
				(dispatcher, context, selection) -> SupplyDropCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_TICKS == 13) {
				tick(server.overworld());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (bar != null) {
				bar.removeAllPlayers();
				bar = null;
			}
		});
	}

	public static void tick(ServerLevel level) {
		SupplyDropState state = SupplyDropState.get(level.getServer());
		Optional<SupplyDropState.Active> active = state.active();
		if (active.isEmpty()) {
			schedule(level, state);
			hideBar();
			return;
		}
		SupplyDropState.Active drop = active.get();
		long left = drop.deadline() - level.getGameTime();
		if (drop.crate().isPresent() && claimed(level, drop.crate().get())) {
			finish(level, state, Component.translatable("message.ashfall.supply_drop.claimed").withStyle(ChatFormatting.GREEN));
			return;
		}
		if (left <= 0) {
			drop.crate().filter(pos -> level.isLoaded(pos) && !claimed(level, pos))
					.ifPresent(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
			finish(level, state, Component.translatable("message.ashfall.supply_drop.lost").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (drop.crate().isEmpty() && playerNear(level, drop.x(), drop.z(), LAND_RADIUS)) {
			land(level, drop).ifPresent(pos -> state.setActive(Optional.of(drop.landed(pos))));
		}
		drop.crate().filter(level::isLoaded).ifPresent(pos ->
				level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01));
		showBar(level, drop, left);
	}

	/** Starts today's drop when it is due, and plans the next one. */
	private static void schedule(ServerLevel level, SupplyDropState state) {
		int day = DirectorDay.today(level);
		if (state.nextDay() <= 0) {
			state.setNextDay(FIRST_DAY);
		}
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24_000L);
		// Nobody online: wait, so a drop is never wasted on an empty server.
		if (level.players().isEmpty() || day < state.nextDay() || timeOfDay < START_FROM || timeOfDay >= START_UNTIL) {
			return;
		}
		RandomSource random = level.getRandom();
		state.setNextDay(day + MIN_GAP_DAYS + random.nextInt(MAX_GAP_DAYS - MIN_GAP_DAYS + 1));
		Point point = POINTS.get(random.nextInt(POINTS.size()));
		start(level, point.x(), point.z(), true);
	}

	/**
	 * Announces a drop aimed at ({@code x}, {@code z}). The schedule picks a fixed point; the admin command and tests
	 * may pass any spot. @param snap whether to move to the nearest street (off for tests on a flat test floor)
	 */
	public static SupplyDropState.Active start(ServerLevel level, int x, int z, boolean snap) {
		int[] at = snap ? snapToStreet(x, z) : new int[] {x, z};
		SupplyDropState.Active drop = new SupplyDropState.Active(at[0], at[1], level.getGameTime() + DEADLINE_TICKS, Optional.empty());
		SupplyDropState.get(level.getServer()).setActive(Optional.of(drop));
		Component msg = Component.translatable("message.ashfall.supply_drop.incoming", at[0], at[1], DEADLINE_TICKS / 1200)
				.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(msg);
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0f, 0.7f);
		}
		AshfallArms.LOGGER.info("Supply drop: aimed at {}, {}", at[0], at[1]);
		return drop;
	}

	/** The centre of the street chunk nearest ({@code x}, {@code z}), or the point itself without Lost Cities plans. */
	private static int[] snapToStreet(int x, int z) {
		return RelayPlan.nearestStreet(AtlasProcessor.buildings()::chunk, x, z, Set.of())
				.map(c -> new int[] {(c.chunkX() << 4) + 8, (c.chunkZ() << 4) + 8})
				.orElse(new int[] {x, z});
	}

	/** Brings the crate down: chest, one-time loot, the crash, and its guard. @return where it landed */
	public static Optional<BlockPos> land(ServerLevel level, SupplyDropState.Active drop) {
		if (!level.hasChunk(drop.x() >> 4, drop.z() >> 4)) {
			return Optional.empty();
		}
		BlockPos pos = surface(level, drop.x(), drop.z());
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof RandomizableContainer container) {
			container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, TABLE), level.getRandom().nextLong());
			be.setAttached(AshfallSalvage.LOOT_STATE, new LootState(TABLE, Optional.empty(), Optional.empty(), -1, 0));
			be.setChanged();
		}
		level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 1.4f);
		level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		ArmsNoise.emit(level, pos, NOISE_RADIUS);
		int guards = guard(level, pos);
		AshfallArms.LOGGER.info("Supply drop: landed at {} with {} guards", pos, guards);
		return Optional.of(pos);
	}

	/** Spawns the crate's guard around it. @return how many spawned */
	static int guard(ServerLevel level, BlockPos crate) {
		RandomSource random = level.getRandom();
		int day = DirectorDay.today(level);
		int count = GUARDS_MIN + random.nextInt(GUARDS_MAX - GUARDS_MIN + 1), spawned = 0;
		for (int i = 0; i < count; i++) {
			int x = crate.getX() + random.nextIntBetweenInclusive(-GUARD_RADIUS, GUARD_RADIUS);
			int z = crate.getZ() + random.nextIntBetweenInclusive(-GUARD_RADIUS, GUARD_RADIUS);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			Map.Entry<Identifier, ZombieVariant> variant = ZombieVariants.pick(ZombieVariants.all(), random.nextDouble(), day,
					ZombieVariant.Environment.LAND, null, SpawnContext.NONE);
			if (DirectorSpawns.spawn(level, surface(level, x, z), variant, DirectorZombie.Source.AMBIENT, DirectorZombie.NO_BUILDING) != null) {
				spawned++;
			}
		}
		return spawned;
	}

	/** The open spot on top of the ground at ({@code x}, {@code z}). */
	private static BlockPos surface(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** Opened: the loot table was rolled into the chest, or the chest is gone. */
	private static boolean claimed(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.is(Blocks.CHEST) || level.getBlockEntity(pos) instanceof RandomizableContainer c && c.getLootTable() == null;
	}

	private static boolean playerNear(ServerLevel level, int x, int z, int radius) {
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - x, dz = player.getZ() - z;
			if (!player.isSpectator() && dx * dx + dz * dz <= (double) radius * radius) {
				return true;
			}
		}
		return false;
	}

	/** Ends the current drop with a message to everyone. */
	public static void finish(ServerLevel level, SupplyDropState state, Component message) {
		state.setActive(Optional.empty());
		hideBar();
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(message);
		}
	}

	private static void showBar(ServerLevel level, SupplyDropState.Active drop, long left) {
		if (bar == null) {
			bar = new ServerBossEvent(BAR_ID, Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		long seconds = (left + 19) / 20;
		bar.setName(Component.translatable("bossbar.ashfall.supply_drop", drop.x(), drop.z(),
				String.format("%d:%02d", seconds / 60, seconds % 60)));
		bar.setProgress(Math.clamp(left / (float) DEADLINE_TICKS, 0f, 1f));
		Set<ServerPlayer> wanted = new HashSet<>(level.players());
		for (ServerPlayer player : Set.copyOf(bar.getPlayers())) {
			if (!wanted.remove(player)) {
				bar.removePlayer(player);
			}
		}
		wanted.forEach(bar::addPlayer);
	}

	private static void hideBar() {
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}
}
