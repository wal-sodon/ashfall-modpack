package xyz.toesinthesand.ashfall.arms.grid;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.atlas.LightStripper;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;
import xyz.toesinthesand.ashfall.salvage.atlas.RelightLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lazy relight (plan section 3.2, M4.5 row): a reclaimed district's lights come back chunk by chunk as players
 * load them, by replaying the positions the atlas logged when it turned them off ({@link RelightLog}).
 * <p>
 * Dead redstone lamps become {@link GridBlocks#GRID_LAMP}; stripped lights (glowstone, lanterns, ...) are put back
 * as the block they were, but only where the stripper's leftover is still there (air for removed lights, the
 * patch for full-cube ones), so a player's build at that spot is never overwritten. Each chunk carries
 * {@link #RELIT}, so a chunk is replayed once per load cycle at most, never on every load.
 * <p>
 * At night the restored lights are LIGHT stimuli for the Director (endgame-design section 3, Downtown row): lit
 * streets draw the dead, so a lit district is more dangerous after dark, not less. Only lights near players
 * shine, under a per-pass cap, which keeps a large lit district from flooding the stimulus field.
 */
public final class GridRelight {
	/** The chunk has been relit for its district. Persistent, so restarts don't replay every chunk again. */
	public static final AttachmentType<Boolean> RELIT = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath("ashfall", "grid_relit"), Codec.BOOL);

	private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();
	/** Relit chunks of BROKEN or BLACKOUT districts, waiting for their grid lamps to go dark. */
	private static final LongLinkedOpenHashSet DARKEN = new LongLinkedOpenHashSet();
	/** District -> packed chunk -> logged lights, rebuilt when the log grows. */
	private static Map<String, Map<Long, List<RelightLog.Entry>>> index = Map.of();
	private static int indexedTotal = -1;

	private GridRelight() {
	}

	static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level.OVERWORLD) {
				return;
			}
			// Only chunks of a district that is lit (or broken) right now; any other chunk is looked at again when it next loads.
			ChunkPos cp = chunk.getPos();
			boolean relit = chunk.getAttachedOrElse(RELIT, false);
			Optional<String> district = GridDistricts.districtAt(level, new BlockPos(cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ()));
			if (district.isEmpty()) {
				return;
			}
			DistrictState state = GridState.get(level.getServer()).state(district.get());
			if (!relit && state == DistrictState.RECLAIMED) {
				QUEUE.add(cp.pack());
			} else if (relit && state.dark()) {
				DARKEN.add(cp.pack());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(GridRelight::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			DARKEN.clear();
			index = Map.of();
			indexedTotal = -1;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GridSettings settings = GridSettings.get();
		GridState grid = GridState.get(server);
		int looked = 0, budget = QUEUE.size();
		for (int done = 0; done < settings.reclaimed().relightChunksPerTick() && !QUEUE.isEmpty() && looked < budget; looked++) {
			long key = QUEUE.removeFirstLong();
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk == null || chunk.getAttachedOrElse(RELIT, false)) {
				continue;
			}
			if (AtlasProcessor.active() && AtlasProcessor.needsProcessing(chunk)) {
				// The atlas has not logged this chunk's lights yet; relighting now would mark it done with nothing restored.
				QUEUE.add(key);
				continue;
			}
			ChunkPos cp = chunk.getPos();
			Optional<String> district = GridDistricts.districtAt(level, new BlockPos(cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ()));
			if (district.isEmpty() || !grid.isReclaimed(district.get())) {
				continue; // not lit (yet): checked again the next time the chunk loads
			}
			relightChunk(level, chunk, district.get());
			chunk.setAttached(RELIT, true);
			chunk.markUnsaved();
			done++;
		}
		for (int done = 0; done < settings.reclaimed().relightChunksPerTick() && !DARKEN.isEmpty(); done++) {
			long key = DARKEN.removeFirstLong();
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk == null || !chunk.getAttachedOrElse(RELIT, false)) {
				continue;
			}
			ChunkPos cp = chunk.getPos();
			Optional<String> district = GridDistricts.districtAt(level, new BlockPos(cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ()));
			if (district.isPresent() && grid.state(district.get()).dark()) {
				darkenChunk(level, chunk, district.get());
			}
		}
		if (server.getTickCount() % settings.reclaimed().lightIntervalTicks() == 5 && level.isDarkOutside()) {
			emitLights(level, settings.reclaimed());
		}
	}

	/** The district's logged lights by chunk. */
	static synchronized Map<Long, List<RelightLog.Entry>> lights(MinecraftServer server, String district) {
		RelightLog log = RelightLog.get(server);
		if (log.total() != indexedTotal) {
			Map<String, Map<Long, List<RelightLog.Entry>>> next = new HashMap<>();
			for (String zone : log.countsByZone().keySet()) {
				Map<Long, List<RelightLog.Entry>> byChunk = new HashMap<>();
				for (RelightLog.Entry e : log.entries(zone)) {
					byChunk.computeIfAbsent(ChunkPos.pack(e.pos()), k -> new ArrayList<>()).add(e);
				}
				next.put(zone, byChunk);
			}
			index = next;
			indexedTotal = log.total();
		}
		return index.getOrDefault(district, Map.of());
	}

	/** Replays the district's logged lights in one loaded chunk. @return lights restored */
	public static int relightChunk(ServerLevel level, LevelChunk chunk, String district) {
		List<RelightLog.Entry> entries = lights(level.getServer(), district).getOrDefault(chunk.getPos().pack(), List.of());
		int restored = 0;
		for (RelightLog.Entry entry : entries) {
			if (restore(level, entry)) {
				restored++;
			}
		}
		return restored;
	}

	/** Relights every loaded chunk of a district that has logged lights (right after it is reclaimed). */
	public static int relightLoaded(ServerLevel level, String district) {
		Map<Long, List<RelightLog.Entry>> byChunk = lights(level.getServer(), district);
		int restored = 0;
		for (LevelChunk chunk : LoadedChunks.of(level)) {
			if (byChunk.containsKey(chunk.getPos().pack())) {
				restored += relightChunk(level, chunk, district);
				chunk.setAttached(RELIT, true);
				chunk.markUnsaved();
			}
		}
		return restored;
	}

	/**
	 * A Breach broke the district: its Grid Lamps in loaded chunks go dark again (back to plain redstone lamps), and
	 * those chunks lose {@link #RELIT} so a re-repair relights them. Relit chunks that are not loaded go dark when
	 * they next load. Only Grid Lamps at logged lamp positions are touched, so nothing a player built changes.
	 * @return lamps darkened
	 */
	public static int darkenLoaded(ServerLevel level, String district) {
		Map<Long, List<RelightLog.Entry>> byChunk = lights(level.getServer(), district);
		int darkened = 0;
		for (LevelChunk chunk : LoadedChunks.of(level)) {
			if (byChunk.containsKey(chunk.getPos().pack()) && chunk.getAttachedOrElse(RELIT, false)) {
				darkened += darkenChunk(level, chunk, district);
			}
		}
		return darkened;
	}

	/** Turns the district's Grid Lamps in one chunk back into dead redstone lamps and clears {@link #RELIT}. */
	static int darkenChunk(ServerLevel level, LevelChunk chunk, String district) {
		int darkened = 0;
		for (RelightLog.Entry entry : lights(level.getServer(), district).getOrDefault(chunk.getPos().pack(), List.of())) {
			Block original = BuiltInRegistries.BLOCK.getOptional(entry.block()).orElse(null);
			if (original == Blocks.REDSTONE_LAMP && level.getBlockState(entry.pos()).is(GridBlocks.GRID_LAMP)) {
				level.setBlock(entry.pos(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				darkened++;
			}
		}
		chunk.removeAttached(RELIT);
		chunk.markUnsaved();
		return darkened;
	}

	/** Puts one logged light back if its spot still holds what the stripper left. */
	static boolean restore(ServerLevel level, RelightLog.Entry entry) {
		BlockPos pos = entry.pos();
		BlockState current = level.getBlockState(pos);
		Block original = BuiltInRegistries.BLOCK.getOptional(entry.block()).orElse(null);
		if (original == null) {
			return false;
		}
		BlockState next = null;
		if (original == Blocks.REDSTONE_LAMP) {
			if (current.is(Blocks.REDSTONE_LAMP)) {
				next = GridBlocks.GRID_LAMP.defaultBlockState();
			}
		} else if (LightStripper.REMOVE.contains(original)) {
			boolean water = current.getFluidState().is(Fluids.WATER) && current.getBlock() == Blocks.WATER;
			if (current.isAir() || water) {
				next = original.defaultBlockState();
				if (next.hasProperty(LanternBlock.HANGING)) {
					next = next.setValue(LanternBlock.HANGING, !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
							net.minecraft.core.Direction.UP));
				}
				if (water && next.hasProperty(BlockStateProperties.WATERLOGGED)) {
					next = next.setValue(BlockStateProperties.WATERLOGGED, true);
				}
			}
		} else if (LightStripper.PATCH.contains(original)) {
			if (!current.isAir() && !current.hasBlockEntity() && current.getLightEmission() == 0 && current.isSolidRender()) {
				next = original.defaultBlockState();
			}
		}
		if (next == null) {
			return false;
		}
		// Like the stripper: no neighbour updates, so nothing nearby pops off when the light changes.
		level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return true;
	}

	/** One night pass: restored lights near players become LIGHT stimuli. Public for gametests. */
	public static int emitLights(ServerLevel level, GridSettings.Reclaimed rules) {
		GridState grid = GridState.get(level.getServer());
		int emitted = 0;
		double r2 = (double) rules.lightPlayerRadius() * rules.lightPlayerRadius();
		for (String district : grid.districts()) {
			if (!grid.isReclaimed(district)) {
				continue;
			}
			for (List<RelightLog.Entry> entries : lights(level.getServer(), district).values()) {
				for (RelightLog.Entry entry : entries) {
					if (emitted >= rules.lightMaxPerPass()) {
						return emitted;
					}
					BlockPos pos = entry.pos();
					if (!nearPlayer(level, pos, r2) || !level.hasChunkAt(pos) || level.getBlockState(pos).getLightEmission() <= 0) {
						continue;
					}
					Stimuli.emit(level, Vec3.atCenterOf(pos), StimulusType.LIGHT, rules.lightRadius(), null);
					emitted++;
				}
			}
		}
		return emitted;
	}

	private static boolean nearPlayer(ServerLevel level, BlockPos pos, double r2) {
		Vec3 c = Vec3.atCenterOf(pos);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(c) <= r2) {
				return true;
			}
		}
		return false;
	}
}
