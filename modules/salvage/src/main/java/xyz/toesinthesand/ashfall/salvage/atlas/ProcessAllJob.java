package xyz.toesinthesand.ashfall.salvage.atlas;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

/**
 * {@code /ashfall city processall <radius>} (also {@code /ashfall atlas processall}): walks every chunk the
 * building source lists (Lost Cities: the city chunks within the radius of Downtown; Arnis: every chunk a
 * building touches), so a server can be converted ahead of launch, right after a Chunky pregeneration, instead
 * of lazily as players explore. Chunks are loaded in small batches with an Atlas ticket, processed once loaded,
 * then released, so memory stays flat across the whole city (tens of thousands of chunks).
 */
public final class ProcessAllJob {
	/** Chunks loaded at once. */
	public static final int BATCH = 16;
	/** Ticks to wait for a batch to load before giving up on the chunks still missing. */
	private static final int BATCH_TIMEOUT = 600;
	private static final int REPORT_EVERY = 200;

	private final LongList chunks;
	private final CommandSourceStack source;
	private final LongList batch = new LongArrayList();
	private int next;
	private int waited;
	private int done;
	private int skipped;
	private int lastReport;

	ProcessAllJob(LongList chunks, CommandSourceStack source) {
		this.chunks = chunks;
		this.source = source;
	}

	public int total() {
		return chunks.size();
	}

	public int done() {
		return done;
	}

	/** Advances the job by one tick. @return true when finished */
	boolean tick(ServerLevel level) {
		if (batch.isEmpty()) {
			if (next >= chunks.size()) {
				finish();
				return true;
			}
			for (int i = 0; i < BATCH && next < chunks.size(); i++, next++) {
				long key = chunks.getLong(next);
				batch.add(key);
				level.getChunkSource().addTicketWithRadius(AtlasProcessor.TICKET, ChunkPos.unpack(key), 1);
			}
			waited = 0;
			return false;
		}
		boolean timedOut = ++waited > BATCH_TIMEOUT;
		boolean allLoaded = true;
		for (int i = 0; i < batch.size(); i++) {
			long key = batch.getLong(i);
			// The radius-1 ticket loads the ring too; wait for it so the passes never load a neighbour themselves.
			if (level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) == null
					|| !AtlasProcessor.neighboursReady(level, ChunkPos.getX(key), ChunkPos.getZ(key))) {
				allLoaded = false;
				break;
			}
		}
		if (!allLoaded && !timedOut) {
			return false;
		}
		for (int i = 0; i < batch.size(); i++) {
			long key = batch.getLong(i);
			ChunkPos pos = ChunkPos.unpack(key);
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk == null) {
				skipped++;
			} else if (AtlasProcessor.needsProcessing(chunk)) {
				AtlasProcessor.processChunk(level, chunk);
			}
			level.getChunkSource().removeTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
			done++;
		}
		batch.clear();
		if (done - lastReport >= REPORT_EVERY) {
			lastReport = done;
			report(Component.literal("City processall: " + done + "/" + chunks.size() + " chunks"));
		}
		return false;
	}

	/** Releases any tickets still held (server stopping or job replaced). */
	void cancel(ServerLevel level) {
		for (int i = 0; i < batch.size(); i++) {
			level.getChunkSource().removeTicketWithRadius(AtlasProcessor.TICKET, ChunkPos.unpack(batch.getLong(i)), 1);
		}
		batch.clear();
	}

	private void finish() {
		report(Component.literal("City processall finished: " + done + " chunks"
				+ (skipped > 0 ? " (" + skipped + " failed to load and were skipped)" : "")));
	}

	private void report(Component message) {
		AshfallSalvage.LOGGER.info(message.getString());
		source.sendSuccess(() -> message, true);
	}
}
