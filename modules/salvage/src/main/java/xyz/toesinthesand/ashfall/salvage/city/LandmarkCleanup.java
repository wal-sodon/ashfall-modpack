package xyz.toesinthesand.ashfall.salvage.city;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
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
 * darkness, and later relighting, is the point, so when a landmark chunk loads we remove the lit
 * cave decoration ({@link #CAVE_LIGHTS}) between the bottom cellar and just above the roof.
 * <p>
 * Only that decoration, never "anything that gives light": this runs on every load, so a broader rule would
 * also delete what the game and the players light later (a repaired Substation Core glows, Grid Lamps,
 * placed torches), and an unbreakable core deleted that way could not be got back in survival.
 * <p>
 * Only landmark chunks are touched; ordinary Lost Cities buildings keep whatever vanilla decorated
 * them with (the LightStripper safety net covers those when it runs).
 */
public final class LandmarkCleanup {
	/** The cave decoration this removes, when lit (glow berries make cave vines glow). */
	public static final TagKey<Block> CAVE_LIGHTS = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("ashfall", "landmark/cave_lights"));

	private LandmarkCleanup() {
	}

	public static void register() {
		// On every load rather than only on generation: worlds generated before this existed get cleaned
		// too, and the scan is cheap because only the few dozen landmark chunks get past the plan check.
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> clean(level, chunk));
	}

	/** Cleans one chunk if it holds a landmark. Public for gametests. @return blocks removed */
	public static int clean(ServerLevel level, LevelChunk chunk) {
		if (LostCityContext.profile(level).isEmpty()) {
			return 0;
		}
		Optional<CityChunk> plan = LostCityContext.chunk(level, chunk.getPos().x(), chunk.getPos().z());
		if (plan.isEmpty() || !Landmarks.isLandmarkBuilding(plan.get().buildingId())) {
			return 0;
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
					if (st.is(CAVE_LIGHTS) && st.getLightEmission() > 0) {
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
		return removed;
	}
}
