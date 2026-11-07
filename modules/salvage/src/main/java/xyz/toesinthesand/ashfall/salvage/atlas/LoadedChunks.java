package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.mixin.ChunkMapAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The chunks a level actually has loaded at full status, read from the chunk map itself.
 * The atlas commands used to walk a set kept up to date from chunk load/unload events, which reported
 * "0 loaded chunks" for force-loaded chunks and anything loaded before the listener existed.
 */
public final class LoadedChunks {
	private LoadedChunks() {
	}

	/** A snapshot (safe to modify the level while walking it) of every full chunk loaded in {@code level}. */
	public static List<LevelChunk> of(ServerLevel level) {
		ChunkMapAccessor map = (ChunkMapAccessor) level.getChunkSource().chunkMap;
		List<LevelChunk> chunks = new ArrayList<>();
		for (ChunkHolder holder : map.ashfall$visibleChunkMap().values()) {
			LevelChunk chunk = holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null);
			if (chunk != null) {
				chunks.add(chunk);
			}
		}
		return chunks;
	}

	/**
	 * True when the chunk holding this block is loaded at full status, so reading it never waits. Vanilla's
	 * {@code hasChunk}/{@code hasChunkAt}/{@code isLoaded} only look at the chunk's ticket level, which is already
	 * high enough while the chunk is still loading or generating, and a {@code getBlockState} then parks the server
	 * thread until it arrives. Profiling a flight over new city chunks found the Director's spawn search doing
	 * exactly that. Other levels (client, tests' fake getters) fall back to {@code hasChunk}.
	 */
	public static boolean ready(LevelReader level, int blockX, int blockZ) {
		int cx = blockX >> 4, cz = blockZ >> 4;
		if (level instanceof ServerLevel server) {
			return server.getChunkSource().getChunkNow(cx, cz) != null;
		}
		return level.hasChunk(cx, cz);
	}

	/** {@link #ready(LevelReader, int, int)} for a block position. */
	public static boolean ready(LevelReader level, BlockPos pos) {
		return ready(level, pos.getX(), pos.getZ());
	}

	/** {@link #ready(LevelReader, int, int)} for a chunk position. */
	public static boolean chunkReady(LevelReader level, int chunkX, int chunkZ) {
		return ready(level, chunkX << 4, chunkZ << 4);
	}
}
