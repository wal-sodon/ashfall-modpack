package xyz.toesinthesand.ashfall.salvage.atlas;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.damage.DamagePass;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.List;
import java.util.Optional;

/**
 * Post-processes the city one chunk at a time, the first time each chunk is loaded with Ashfall installed:
 * lights off (logged for the relight), loot containers given building loot and topped up per storey, damage
 * applied, safe-zone candidates recorded, Power Relays placed. What stands where comes from the
 * {@link BuildingSource}: Lost Cities' chunk plans in a Lost Cities world (only its city chunks are touched),
 * or the retired Arnis sidecar.
 * <p>
 * The trigger is the first chunk load, not Lost Cities' {@code POST_GEN_CITY_CHUNK} event. That event fires on a
 * worldgen thread with a half-built {@code ProtoChunk}, where neighbours may not exist and level access can
 * deadlock; the passes read neighbours (container walls, light patching) and write saved data, which is only
 * safe on the server thread with the chunk in the level. Lost Cities' only effect we need at generation time,
 * keeping Lootr off its chests, is a pure decision made in its {@code createLoot} instead (see
 * {@link xyz.toesinthesand.ashfall.salvage.loot.LootPlaceholder}).
 * <p>
 * Doing it lazily on load means only the parts people visit pay the cost ({@code /ashfall city processall}
 * converts a radius ahead of play, after a Chunky pregeneration). Each chunk carries an {@link #VERSION}
 * attachment so it is processed exactly once per pipeline version, and every pass is deterministic, so the
 * order in which players load chunks does not matter. Chunks are never modified inside the load callback (the
 * chunk may not be fully wired into the level yet); they are queued and processed at the end of the server
 * tick under a budget.
 * <p>
 * A chunk is only processed once its eight neighbours are loaded too (see {@link #neighboursReady}). The passes
 * read and notify across the chunk border (a container at the edge updates comparators next door, a broken
 * window wakes a hopper in the neighbour, damage looks one block out), and a read into a chunk that is not
 * loaded makes the server thread wait for it to load or, at the edge of explored land, for Lost Cities to
 * generate it. Profiling showed those synchronous waits were most of the "Can't keep up" lag while flying or
 * pregenerating: each forced neighbour loaded, was queued in turn and forced its own neighbour, so the waits
 * cascaded outward. Chunks on the rim of the loaded area wait in {@link #WAITING} until a neighbour load
 * completes their ring.
 */
public final class AtlasProcessor {
	/** Bump when a pass changes; chunks below it are processed again by the passes newer than their version. */
	public static final int CURRENT_VERSION = 3;
	/** First version with Power Relays placed at relay candidates (see {@link RelayPlacer}). */
	public static final int RELAY_VERSION = 2;
	/** First version with exterior loot and found bicycles on streets (see {@link StreetProps}). */
	public static final int STREET_PROPS_VERSION = 3;
	/** Ticks between sweeps for relays whose chunk was processed before the candidate was recorded. */
	private static final int RELAY_SWEEP_TICKS = 100;
	/** Most chunks processed per server tick. */
	public static final int BUDGET_PER_TICK = 4;
	/**
	 * Server-thread time after which no further chunk is started this tick. A downtown chunk can take several
	 * milliseconds, and four of them on top of a busy tick was enough to push it past 50 ms; the queue just
	 * drains over a few more ticks instead.
	 */
	static final long TIME_BUDGET_NANOS = 3_000_000L;
	/** Queue entries examined per tick at most, so a long queue of not-yet-ready chunks never stalls a tick. */
	private static final int CHECKS_PER_TICK = 256;

	/** Pipeline version a chunk was processed with; absent = never. */
	public static final AttachmentType<Integer> VERSION = AttachmentRegistry.createPersistent(AshfallSalvage.id("atlas_version"), Codec.INT);
	/** Keeps chunks loaded while {@code /ashfall atlas processall} works through them. */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, AshfallSalvage.id("atlas"),
			new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

	private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();
	/** Unprocessed loaded chunks with a neighbour not loaded yet; requeued when a neighbour loads. */
	private static final LongOpenHashSet WAITING = new LongOpenHashSet();
	private static volatile BuildingSource buildingsOverride;
	private static int processedThisSession;
	private static boolean warnedNoProfiles;
	private static ProcessAllJob job;

	private AtlasProcessor() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level.OVERWORLD) {
				return;
			}
			ChunkPos pos = chunk.getPos();
			if (needsProcessing(chunk) && active()) {
				QUEUE.add(pos.pack());
			}
			wakeNeighbours(pos.x(), pos.z());
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			if (level.dimension() == Level.OVERWORLD) {
				WAITING.remove(chunk.getPos().pack());
			}
		});
		// Chunks that loaded while the atlas was paused (no profiles yet) were not queued; a /reload that
		// brings profiles in must pick them up rather than wait for them to unload and load again.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> onSourceReady(server));
		ServerTickEvents.END_SERVER_TICK.register(AtlasProcessor::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			WAITING.clear();
			processedThisSession = 0;
			warnedNoProfiles = false;
			job = null;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> AtlasCommands.register(dispatcher));
	}

	/** The building source the atlas works from: the world's, unless a test replaced it. */
	public static BuildingSource buildings() {
		BuildingSource override = buildingsOverride;
		return override != null ? override : AshfallSalvage.buildings();
	}

	/** Test hook: replaces the building source (null restores the world's). */
	public static void setBuildingsForTesting(BuildingSource source) {
		buildingsOverride = source;
	}

	/** True when there is something to process with: a building source and loaded building profiles. */
	public static boolean active() {
		if (buildings().isEmpty()) {
			return false;
		}
		if (BuildingProfiles.all().isEmpty()) {
			// Processing without profiles would empty every chest for good; wait for a working datapack.
			if (!warnedNoProfiles) {
				warnedNoProfiles = true;
				AshfallSalvage.LOGGER.warn("Building source {} present but no building profiles loaded; atlas processing is paused",
						buildings().name());
			}
			return false;
		}
		return true;
	}

	/**
	 * The building source or the data changed (server started, /reload): queue what loaded meanwhile and record
	 * the district key relays, which are known before any chunk is generated.
	 */
	public static void onSourceReady(MinecraftServer server) {
		ServerLevel level = server.overworld();
		buildings().invalidate();
		requeueLoaded(level);
		if (active()) {
			int keys = RelayPlan.recordKeyRelays(level, buildings());
			if (keys > 0) {
				AshfallSalvage.LOGGER.info("Planned {} district key relays", keys);
			}
		}
	}

	public static boolean needsProcessing(LevelChunk chunk) {
		return chunk.getAttachedOrElse(VERSION, 0) < CURRENT_VERSION;
	}

	public static int queueSize() {
		return QUEUE.size();
	}

	/** Chunks waiting for a neighbour to load before they can be processed. */
	public static int waitingSize() {
		return WAITING.size();
	}

	/** A chunk finished loading: any waiting neighbour may now have its full ring, so give it another look. */
	private static void wakeNeighbours(int cx, int cz) {
		if (WAITING.isEmpty()) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long key = ChunkPos.pack(cx + dx, cz + dz);
				if ((dx != 0 || dz != 0) && WAITING.remove(key)) {
					QUEUE.add(key);
				}
			}
		}
	}

	/**
	 * True when all eight neighbours of the chunk are loaded at full status, so nothing a pass reads or notifies
	 * across the border can load or generate a chunk synchronously. {@code getChunkNow} is used rather than
	 * {@code hasChunk}, which is already true while a chunk is still on its way in.
	 */
	public static boolean neighboursReady(ServerLevel level, int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) {
					return false;
				}
			}
		}
		return true;
	}

	public static int processedThisSession() {
		return processedThisSession;
	}

	/** Overworld chunks currently loaded, read from the level (see {@link LoadedChunks}). */
	public static List<LevelChunk> loadedChunks(ServerLevel level) {
		return LoadedChunks.of(level);
	}

	/** Queues a chunk for budgeted processing. @return true if it was newly queued */
	public static boolean enqueue(long chunkPos) {
		return QUEUE.add(chunkPos);
	}

	/** Queues every loaded, unprocessed overworld chunk if the atlas is active. @return chunks newly queued */
	public static int requeueLoaded(ServerLevel level) {
		if (!active()) {
			return 0;
		}
		int queued = 0;
		for (LevelChunk chunk : LoadedChunks.of(level)) {
			if (needsProcessing(chunk) && QUEUE.add(chunk.getPos().pack())) {
				queued++;
			}
		}
		return queued;
	}

	public static ProcessAllJob job() {
		return job;
	}

	static void startJob(ProcessAllJob newJob) {
		job = newJob;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!active()) {
			return; // paused (e.g. a /reload lost the profiles): the queue and a processall job wait
		}
		if (job != null && job.tick(level)) {
			job = null;
		}
		if (server.getTickCount() % RELAY_SWEEP_TICKS == 0) {
			RelayPlacer.placeLoaded(level, null);
		}
		long start = System.nanoTime();
		for (int done = 0, checks = 0; done < BUDGET_PER_TICK && checks < CHECKS_PER_TICK && !QUEUE.isEmpty(); checks++) {
			if (done > 0 && System.nanoTime() - start > TIME_BUDGET_NANOS) {
				break;
			}
			long key = QUEUE.removeFirstLong();
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			if (chunk == null || !needsProcessing(chunk)) {
				continue; // unloaded since, or already done; does not use budget
			}
			if (!neighboursReady(level, cx, cz)) {
				WAITING.add(key); // a neighbour load requeues it (wakeNeighbours)
				continue;
			}
			processChunk(level, chunk);
			done++;
		}
	}

	/** Block state at a position if its chunk is loaded, else null; never loads or generates chunks. */
	public static BlockState peek(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		return chunk == null ? null : chunk.getBlockState(pos);
	}

	/** What one chunk's processing did. */
	public record Result(int lightsStripped, int containersConverted, int containersPlaced, int damaged, int relaysPlaced) {
	}

	/**
	 * Runs every pass the chunk has not had yet, synchronously, and stamps it with
	 * {@link #CURRENT_VERSION}. Call on the server thread with the chunk loaded.
	 */
	public static Result processChunk(ServerLevel level, LevelChunk chunk) {
		int version = chunk.getAttachedOrElse(VERSION, 0);
		BuildingSource source = buildings();
		ChunkPos cp = chunk.getPos();
		Optional<CityChunk> plan = source.chunk(cp.x(), cp.z());
		int lights = 0, damaged = 0, relays = 0;
		ContainerPass.Result containers = new ContainerPass.Result(0, 0);
		try {
			if (version < 1 && source.coversChunk(cp.x(), cp.z())) {
				// Arnis lights were never real fixtures, so only Lost Cities lights are worth logging for the relight.
				lights = LightStripper.apply(level, chunk, plan.isPresent() ? RelightLog.get(level.getServer()) : null);
				containers = ContainerPass.apply(level, chunk, source);
				damaged = DamagePass.apply(level, chunk, source, AtlasHash.hash(level.getSeed(), cp.pack(), 0xDA4A6EL));
				recordCandidates(level, chunk, source);
			}
			if (version < RELAY_VERSION) {
				if (plan.isPresent() && plan.get().isStreet()) {
					RelayPlan.recordAmbient(level, plan.get());
				}
				relays = RelayPlacer.placeLoaded(level, chunk);
			}
			if (version < STREET_PROPS_VERSION) {
				StreetProps.place(level, chunk, plan);
			}
		} catch (RuntimeException e) {
			// Never retry a chunk that throws on every load; log it and move on.
			AshfallSalvage.LOGGER.error("Atlas processing failed for chunk {}", chunk.getPos(), e);
		}
		chunk.setAttached(VERSION, CURRENT_VERSION);
		chunk.markUnsaved();
		processedThisSession++;
		return new Result(lights, containers.converted(), containers.placed(), damaged, relays);
	}

	/**
	 * Safe-zone candidates, and on Arnis maps per-building relay candidates (Lost Cities relays go on streets, see
	 * {@link RelayPlan}). Each building is recorded from exactly one chunk, the one holding its footprint's
	 * minimum corner (a Lost Cities building's origin chunk), so no other chunk needs to know whether it was seen.
	 */
	static void recordCandidates(ServerLevel level, LevelChunk chunk, BuildingSource source) {
		ChunkPos cp = chunk.getPos();
		boolean streetRelays = source.chunk(cp.x(), cp.z()).isPresent();
		AtlasData data = null;
		for (Building building : source.inChunk(cp.x(), cp.z())) {
			if (building.footprint().minX() >> 4 != cp.x() || building.footprint().minZ() >> 4 != cp.z()) {
				continue;
			}
			Unit ground = null;
			for (Unit unit : building.units()) {
				if (ground == null || unit.floor() < ground.floor()) {
					ground = unit;
				}
			}
			Identifier profileId = BuildingProfiles.classifyByTags(building);
			if (profileId == null) {
				profileId = ground != null ? BuildingProfiles.classify(building, ground) : BuildingProfiles.GENERIC;
			}
			BuildingProfile profile = BuildingProfiles.get(profileId);
			if (profile == null) {
				continue;
			}
			if (data == null) {
				data = AtlasData.get(level.getServer());
			}
			if (!streetRelays && ground != null && profile.relayChance() > 0
					&& AtlasHash.unit(AtlasHash.hash(level.getSeed(), building.id(), 0x4E1AL)) < profile.relayChance()) {
				data.addRelay(new AtlasData.RelayCandidate(level.dimension(), relayCell(building, ground), building.id()));
			}
			if (profile.safeCandidate()) {
				int y = ground != null ? ground.floorY() + 1 : building.baseY() + 1;
				BlockPos centre = new BlockPos((building.footprint().minX() + building.footprint().maxX()) / 2, y,
						(building.footprint().minZ() + building.footprint().maxZ()) / 2);
				data.addSafe(new AtlasData.SafeCandidate(building.id(), centre));
			}
		}
	}

	/** A deterministic cell among the building's ground-floor units (the lowest-hash one). */
	static BlockPos relayCell(Building building, Unit ground) {
		long[] best = {Long.MAX_VALUE, 0};
		for (Unit unit : building.units()) {
			if (unit.floor() != ground.floor()) {
				continue;
			}
			unit.footprint().forEach((x, z) -> {
				BlockPos pos = new BlockPos(x, unit.floorY() + 1, z);
				long h = AtlasHash.hash(building.id(), pos.asLong()) >>> 1;
				if (h < best[0]) {
					best[0] = h;
					best[1] = pos.asLong();
				}
			});
		}
		return BlockPos.of(best[1]);
	}
}
