package xyz.toesinthesand.ashfall.salvage.city;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.Optional;

/**
 * Keeps landmark interiors dark. The assets contain no light sources, but vanilla's cave decoration
 * runs over Lost Cities' output and treats a sealed cellar (or the shaded air under a parking deck) as a
 * cave, growing glow lichen on its ceilings. In a landmark that is a stray light in a building whose
 * darkness, and later relighting, is the point, so when a landmark chunk loads we remove
 * every light-emitting non-fluid block between the bottom cellar and just above the roof.
 * <p>
 * Only landmark chunks are touched; ordinary Lost Cities buildings keep whatever vanilla decorated
 * them with (the LightStripper safety net covers those when it runs).
 */
public final class LandmarkCleanup {
	private LandmarkCleanup() {
	}

	public static void register() {
		// On every load rather than only on generation: worlds generated before this existed get cleaned
		// too, and the scan is cheap because only the few dozen landmark chunks get past the plan check.
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> clean(level, chunk));
	}

	private static void clean(ServerLevel level, LevelChunk chunk) {
		if (LostCityContext.profile(level).isEmpty()) {
			return;
		}
		Optional<CityChunk> plan = LostCityContext.chunk(level, chunk.getPos().x(), chunk.getPos().z());
		if (plan.isEmpty() || !Landmarks.isLandmarkBuilding(plan.get().buildingId())) {
			return;
		}
		int bottom = plan.get().groundY() - plan.get().cellars() * 6;
		int top = plan.get().groundY() + plan.get().floors() * 6 + 12;
		int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int removed = 0;
		for (int y = bottom; y <= top; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					BlockState st = chunk.getBlockState(p.set(bx + x, y, bz + z));
					if (st.getLightEmission() > 0 && st.getFluidState().isEmpty()) {
						// Through the chunk, not the level: this runs while the chunk is being promoted to a
						// full chunk, and Level.setBlock would wait for that same chunk and deadlock.
						chunk.setBlockState(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
						level.getChunkSource().getLightEngine().checkBlock(p.immutable());
						removed++;
					}
				}
			}
		}
		if (removed > 0) {
			AshfallSalvage.LOGGER.debug("Removed {} cave-decoration lights from landmark chunk {}", removed, chunk.getPos());
		}
	}
}
