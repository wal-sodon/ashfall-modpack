package xyz.toesinthesand.ashfall.director.senses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;

/**
 * Can a zombie see a light? A voxel walk from its eyes to the light, blocked by:
 * <ul>
 *   <li>any opaque full block, and the occlusion shape of partial opaque blocks (slabs, stairs) where
 *   the ray actually crosses them;</li>
 *   <li>anything in {@code #ashfall:blocks_light_sight} (wool, carpets, banners, doors, trapdoors), the
 *   whole cell, so a carpet or banner hung over a window works as a blackout curtain. Doors and
 *   trapdoors only while closed.</li>
 * </ul>
 * Glass, panes, leaves and other non-occluding blocks let light through, which is the point: a lit
 * window is visible from outside.
 */
public final class LightSight {
	public static final TagKey<Block> BLOCKS_LIGHT_SIGHT =
			TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("ashfall", "blocks_light_sight"));

	private LightSight() {
	}

	/**
	 * Whether the straight line from {@code from} to {@code to} is clear. The cells holding the two end
	 * points are not tested: the viewer's own cell and the light's (the player standing in a doorway)
	 * never hide the light from themselves.
	 * <p>
	 * A cell in a chunk that isn't loaded counts as blocking: {@code ServerLevel#getBlockState} would
	 * otherwise load (or even generate) that chunk synchronously from inside a mob's AI tick.
	 */
	public static boolean canSee(BlockGetter level, Vec3 from, Vec3 to) {
		BlockPos start = BlockPos.containing(from);
		BlockPos end = BlockPos.containing(to);
		Boolean blocked = BlockGetter.traverseBlocks(from, to, level, (getter, pos) -> {
			if (pos.equals(start) || pos.equals(end)) {
				return null;
			}
			if (getter instanceof LevelReader reader && !LoadedChunks.ready(reader, pos)) {
				return Boolean.TRUE;
			}
			return blocksSight(getter, pos, getter.getBlockState(pos), from, to) ? Boolean.TRUE : null;
		}, getter -> Boolean.FALSE);
		return !blocked;
	}

	/** Whether one cell stops the ray. */
	public static boolean blocksSight(BlockGetter level, BlockPos pos, BlockState state, Vec3 from, Vec3 to) {
		if (state.isAir()) {
			return false;
		}
		if (state.is(BLOCKS_LIGHT_SIGHT)) {
			return !(state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN));
		}
		if (state.isSolidRender()) {
			return true;
		}
		return state.canOcclude() && state.getOcclusionShape().clip(from, to, pos) != null;
	}
}
