package xyz.toesinthesand.ashfall.director.pathing;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;

/**
 * Finds the way <em>out</em> of a building from a point inside it (a Substation Core), so zombies can be led the
 * same way <em>in</em>.
 * <p>
 * Why: the vanilla path finder only plans a limited distance and, when it cannot reach a target, walks to the
 * closest point it found. For a core inside a building that closest point is often the roof above it or the cellar
 * below it, which is exactly where the Phase 3 playtest found siege zombies standing ("some stand in the cellar or on
 * other roofs"). Walking from the core outwards through the building's own floors finds the real entrance (the
 * nearest door or ground-level opening by walking distance), and the walk back is a route the path finder can
 * follow in short, always reachable legs.
 * <p>
 * The walk is a breadth-first flood over standing spots (two free blocks over something solid), stepping one block
 * up or down or dropping two, through any door (siege doors are held open; wooden ones zombies break) and open gates.
 * It stops at the first spots under open sky and picks the one closest to the core's floor height, so a roof hatch
 * never beats the front door. Bounded by {@link #MAX_RADIUS} and {@link #MAX_NODES}; a few thousand block reads,
 * once per core per {@code SiegeRoutes} cache period.
 */
public final class EntranceFinder {
	/** Furthest a route may wander from the core horizontally. */
	public static final int MAX_RADIUS = 32;
	/** Furthest a route may climb or descend from the core's floor. */
	public static final int MAX_HEIGHT = 8;
	/** Flood budget: standing spots visited at most. */
	public static final int MAX_NODES = 6000;
	/** Extra steps one block of height away from the core's floor costs when choosing between exits. */
	private static final int HEIGHT_PENALTY = 4;
	/** Once the first exit is found, keep flooding this many steps further for a better (lower) one. */
	private static final int EXIT_SLACK = 12;

	/**
	 * A route out: {@code path} runs from the standing spot next to the core to {@code exit}, the first spot under open
	 * sky, one block apart. An empty path means the core is outdoors (or unreachable) and needs no route.
	 */
	public record Result(List<BlockPos> path, BlockPos exit) {
		public boolean outdoors() {
			return path.isEmpty();
		}
	}

	private EntranceFinder() {
	}

	/** The route from {@code core} to its building's entrance, or an empty result if none was found. */
	public static Result find(ServerLevel level, BlockPos core) {
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
		Long2LongOpenHashMap depth = new Long2LongOpenHashMap();
		int floorY = core.getY() - 1;
		for (int dy = -2; dy <= 1; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos seed = core.offset(dx, dy, dz);
					if (standable(level, seed) && !parent.containsKey(seed.asLong())) {
						if (outside(level, seed)) {
							return new Result(List.of(), seed); // the core stands in the open
						}
						parent.put(seed.asLong(), Long.MIN_VALUE);
						depth.put(seed.asLong(), 0);
						queue.enqueue(seed.asLong());
					}
				}
			}
		}
		long bestExit = Long.MIN_VALUE;
		long bestScore = Long.MAX_VALUE;
		long stopAt = Long.MAX_VALUE;
		int visited = 0;
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		while (!queue.isEmpty() && visited++ < MAX_NODES) {
			long key = queue.dequeueLong();
			long d = depth.get(key);
			if (d > stopAt) {
				break;
			}
			BlockPos pos = BlockPos.of(key);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				for (int dy : new int[]{0, 1, -1, -2}) {
					next.set(pos.getX() + dir.getStepX(), pos.getY() + dy, pos.getZ() + dir.getStepZ());
					long nk = next.asLong();
					if (parent.containsKey(nk) || Math.abs(next.getX() - core.getX()) > MAX_RADIUS
							|| Math.abs(next.getZ() - core.getZ()) > MAX_RADIUS || Math.abs(next.getY() - floorY) > MAX_HEIGHT
							|| !LoadedChunks.ready(level, next) || !standable(level, next)) {
						continue;
					}
					// stepping up needs head room above the spot we leave; dropping needs the air we fall through
					if (dy == 1 && !passable(level, pos.above(2))) {
						continue;
					}
					if (dy < 0 && (!passable(level, next.above(-dy)) || !passable(level, next.above(-dy + 1)))) {
						continue;
					}
					parent.put(nk, key);
					depth.put(nk, d + 1);
					if (outside(level, next)) {
						long score = (d + 1) + (long) HEIGHT_PENALTY * Math.abs(next.getY() - floorY);
						if (score < bestScore) {
							bestScore = score;
							bestExit = nk;
						}
						if (stopAt == Long.MAX_VALUE) {
							stopAt = d + 1 + EXIT_SLACK;
						}
						continue; // outside: no need to flood the street
					}
					queue.enqueue(nk);
				}
			}
		}
		if (bestExit == Long.MIN_VALUE) {
			return new Result(List.of(), core);
		}
		List<BlockPos> path = new ArrayList<>();
		for (long k = bestExit; k != Long.MIN_VALUE; k = parent.get(k)) {
			path.add(BlockPos.of(k));
		}
		java.util.Collections.reverse(path); // core -> exit
		return new Result(path, BlockPos.of(bestExit));
	}

	/**
	 * Under open sky: nothing that blocks movement stands above the spot. The heightmap, not sky light, because the
	 * heightmap is updated the moment a block changes while sky light catches up later, and a glass roof should
	 * still count as indoors.
	 */
	public static boolean outside(ServerLevel level, BlockPos pos) {
		return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
	}

	/** Two passable blocks over something a zombie can stand on. */
	public static boolean standable(ServerLevel level, BlockPos feet) {
		if (!passable(level, feet) || !passable(level, feet.above())) {
			return false;
		}
		BlockState below = level.getBlockState(feet.below());
		return !below.getCollisionShape(level, feet.below()).isEmpty() && !(below.getBlock() instanceof DoorBlock);
	}

	/**
	 * Whether a zombie can be in this block: no collision, or a door (held open in a siege, wooden ones get broken),
	 * an open gate or an open trapdoor.
	 */
	public static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof DoorBlock) {
			return true;
		}
		if ((state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock)
				&& state.hasProperty(FenceGateBlock.OPEN) && state.getValue(FenceGateBlock.OPEN)) {
			return true;
		}
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}
}
