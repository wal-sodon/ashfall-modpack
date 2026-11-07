package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluids;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the city's lights off. In Ashfall the grid is down, so darkness (and the zombies that spawn in it)
 * is part of the game. Full light blocks become whatever the surrounding ceiling or wall is made of;
 * hanging and standing lights simply disappear; redstone lamps stay as dead lamps (unlit).
 * <p>
 * Arnis lit every interior with glowstone grids, so this pass did most of the darkening there. In a Lost
 * Cities world the Lights Off assets and {@code generateLighting: false} leave little to do, so it remains as a
 * cheap safety net for built-in Lost Cities parts that still carry glowstone or lanterns. When given a
 * {@link RelightLog} it records every light it removes and every dead lamp it sees, per zone, which is the
 * data the district relight needs to put them back.
 */
public final class LightStripper {
	/** Full-cube lights, patched over with the surrounding material. */
	public static final Set<Block> PATCH = Set.of(Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT);
	/** Non-cube lights, removed outright. */
	public static final Set<Block> REMOVE = Set.of(Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.END_ROD);
	private static final BlockState FALLBACK = Blocks.SMOOTH_STONE.defaultBlockState();

	private LightStripper() {
	}

	/** A block that lights its surroundings: one we strip, or a lamp someone powered. */
	public static boolean isLight(BlockState state) {
		Block block = state.getBlock();
		return PATCH.contains(block) || REMOVE.contains(block) || (block == Blocks.REDSTONE_LAMP && state.getValue(RedstoneLampBlock.LIT));
	}

	/** Anything this pass acts on or logs: lights, plus dead (unlit) redstone lamps. */
	private static boolean isTarget(BlockState state) {
		return isLight(state) || state.is(Blocks.REDSTONE_LAMP);
	}

	/** Number of light blocks left in a chunk (for the audit). */
	public static int count(LevelChunk chunk) {
		int n = 0;
		LevelChunkSection[] sections = chunk.getSections();
		for (LevelChunkSection section : sections) {
			if (section.hasOnlyAir() || !section.maybeHas(LightStripper::isLight)) {
				continue;
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (isLight(section.getBlockState(x, y, z))) {
							n++;
						}
					}
				}
			}
		}
		return n;
	}

	/** Strips every light block in the chunk without logging (Arnis maps: their lights were never real). */
	public static int apply(ServerLevel level, LevelChunk chunk) {
		return apply(level, chunk, null);
	}

	/**
	 * Strips every light block in the chunk and, if {@code log} is given, records each removed light and each
	 * dead lamp under its zone. @return blocks changed
	 */
	public static int apply(ServerLevel level, LevelChunk chunk, RelightLog log) {
		List<BlockPos> found = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.maybeHas(LightStripper::isTarget)) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (isTarget(section.getBlockState(x, y, z))) {
							found.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
						}
					}
				}
			}
		}
		int changed = 0;
		for (BlockPos pos : found) {
			BlockState state = chunk.getBlockState(pos);
			if (log != null) {
				log.record(ZoneMap.zoneAt(pos.getX(), pos.getZ()).id(), pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
			}
			BlockState replacement;
			if (state.is(Blocks.REDSTONE_LAMP)) {
				if (!state.getValue(RedstoneLampBlock.LIT)) {
					continue; // already a dead lamp: logged, left in place for the relight
				}
				replacement = state.setValue(RedstoneLampBlock.LIT, false);
			} else if (PATCH.contains(state.getBlock())) {
				replacement = patchFor(level, pos);
			} else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
				replacement = Fluids.WATER.defaultFluidState().createLegacyBlock();
			} else {
				replacement = Blocks.AIR.defaultBlockState();
			}
			// No neighbour updates: a support-less torch or a door next to a ceiling light must not pop.
			level.setBlock(pos, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			changed++;
		}
		return changed;
	}

	/** The most common solid, non-light block among the four horizontal neighbours; first-seen wins ties. */
	static BlockState patchFor(ServerLevel level, BlockPos pos) {
		Map<BlockState, Integer> counts = new LinkedHashMap<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState n = AtlasProcessor.peek(level, pos.relative(d));
			if (n != null && n.isSolidRender() && !isLight(n) && n.getLightEmission() == 0 && !n.hasBlockEntity()) {
				counts.merge(n, 1, Integer::sum);
			}
		}
		BlockState best = FALLBACK;
		int bestCount = 0;
		for (Map.Entry<BlockState, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestCount) {
				best = e.getKey();
				bestCount = e.getValue();
			}
		}
		return best;
	}
}
