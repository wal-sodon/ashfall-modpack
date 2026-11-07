package xyz.toesinthesand.ashfall.salvage.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the chunk map's visible holders. Vanilla has no public "every loaded chunk" view (its own
 * iterators skip chunks that are full but not yet ticking or sent), and a list rebuilt from load/unload events
 * misses chunks that were loaded before our listener ran or kept by force-load tickets.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("visibleChunkMap")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> ashfall$visibleChunkMap();
}
