package xyz.toesinthesand.ashfall.arms.grid;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the landmark assets' Substation Core placeholder into the real {@link GridBlocks#SUBSTATION_CORE}.
 * <p>
 * The four substation landmarks are salvage's Lost Cities assets, and salvage sits below arms in the dependency
 * chain: its palettes can't name an arms block, or a salvage-only world (and salvage's own gametests) would fail
 * to load them. So the assets keep the {@code ashfall:landmark/substation_core} marker (a heavy core on the
 * control room's slab plinth) and arms swaps it the first time the chunk loads with arms present, on the server
 * thread, before anyone can reach the control room. Only district chunks are scanned, and a section is only
 * walked when its palette holds a marker at all, so the pass costs next to nothing elsewhere.
 */
public final class CoreConversion {
	/** The placeholder blocks (salvage's tag; arms' core is added to it so the census counts both). */
	public static final TagKey<Block> MARKER = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("ashfall", "landmark/substation_core"));
	private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();
	private static final int PER_TICK = 8;

	private CoreConversion() {
	}

	static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() == Level.OVERWORLD) {
				ChunkPos cp = chunk.getPos();
				if (ZoneMap.zoneAtChunk(cp.x(), cp.z()).isDistrict()) {
					QUEUE.add(cp.pack());
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CoreConversion::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUE.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
			long key = QUEUE.removeFirstLong();
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk != null) {
				convert(level, chunk);
			}
		}
	}

	/** True for a placeholder that is not already the real core. */
	static boolean isPlaceholder(BlockState state) {
		return state.is(MARKER) && !state.is(GridBlocks.SUBSTATION_CORE);
	}

	/** Replaces every placeholder core in the chunk. @return cores placed */
	public static int convert(ServerLevel level, LevelChunk chunk) {
		List<BlockPos> found = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.maybeHas(CoreConversion::isPlaceholder)) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (isPlaceholder(section.getBlockState(x, y, z))) {
							found.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
						}
					}
				}
			}
		}
		int placed = 0;
		for (BlockPos pos : found) {
			// Only the control-room marker: a heavy core on the plinth's slab, inside a district.
			if (!level.getBlockState(pos.below()).is(Blocks.POLISHED_BLACKSTONE_SLAB) || GridDistricts.districtAt(level, pos).isEmpty()) {
				continue;
			}
			level.setBlock(pos, GridBlocks.SUBSTATION_CORE.defaultBlockState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(pos) instanceof SubstationCoreBlockEntity core) {
				core.resolveDistrict(level);
				core.update(level, pos, level.getBlockState(pos));
			}
			placed++;
			AshfallArms.LOGGER.info("Grid: Substation Core placed at {} ({})", pos.toShortString(),
					GridDistricts.districtAt(level, pos).orElse("?"));
		}
		return placed;
	}
}
