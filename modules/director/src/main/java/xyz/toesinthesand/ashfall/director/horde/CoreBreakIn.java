package xyz.toesinthesand.ashfall.director.horde;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.compat.ZbbMixinPlugin;
import xyz.toesinthesand.ashfall.director.compat.zbb.ZbbCoreBreaker;

/**
 * Horde-night break-in: a wave sent at a Bunker Core it can't reach ({@link Hordes#besiegedCore}) used to pile up
 * outside the wall and wait, because Zombies Break &amp; Build only breaks blocks toward a <em>target</em>, and a
 * zombie gets a target only by seeing a player or survivor. A sealed, dark base was therefore untouchable. Now a
 * member with no target that has stood stuck near the core breaks the block in its way toward it
 * ({@link #blockToBreak}), through ZB&amp;B's own break action ({@link ZbbCoreBreaker}), so fortified wear, cracks and
 * block regrowth all apply as usual. Once a zombie sees someone, the normal attack takes over.
 * <p>
 * Without ZB&amp;B in the game nothing breaks blocks at all, so the break-in does nothing either.
 */
public final class CoreBreakIn {
	/** Hits one block for one zombie; true if the hit landed (ZB&amp;B spaces hits with its own cooldown). */
	@FunctionalInterface
	public interface Breaker {
		boolean hit(BlockPos pos);
	}

	/** Makes the {@link Breaker} for one zombie (each keeps its own hit cooldown). */
	@FunctionalInterface
	public interface Factory {
		Breaker create(PathfinderMob mob);
	}

	private static final Breaker NONE = pos -> false;
	private static final boolean ZBB_LOADED = FabricLoader.getInstance().isModLoaded(ZbbMixinPlugin.ZBB_MOD_ID);
	private static volatile @Nullable Factory testFactory;

	private CoreBreakIn() {
	}

	/** The breaker for {@code mob}: ZB&amp;B's break action when the mod is present, else one that never breaks. */
	public static Breaker breakerFor(Mob mob) {
		Factory test = testFactory;
		if (!(mob instanceof PathfinderMob pathfinder)) {
			return NONE;
		}
		if (test != null) {
			return test.create(pathfinder);
		}
		return ZBB_LOADED ? ZbbCoreBreaker.create(pathfinder) : NONE;
	}

	/** Test hook: breakers come from {@code factory} instead of ZB&amp;B; null goes back. */
	public static void setForTesting(@Nullable Factory factory) {
		testFactory = factory;
	}

	/** Drops the test hook (on server stop, so a test that failed half-way can't leak it into the next run). */
	public static void clear() {
		testFactory = null;
	}

	/**
	 * The block {@code mob} should break to get closer to {@code core}, or null if nothing is in its way. It looks
	 * one block ahead toward the core, along the longer horizontal axis first, at the zombie's feet and then its head
	 * (both must be clear to walk through), then along the other axis. With nothing ahead and the core below (a buried
	 * bunker), it digs down. It never picks the core itself, air, or a block with no collision.
	 */
	public static @Nullable BlockPos blockToBreak(ServerLevel level, Mob mob, BlockPos core) {
		BlockPos feet = mob.blockPosition();
		int dx = core.getX() - feet.getX();
		int dz = core.getZ() - feet.getZ();
		Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
		Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
		boolean xFirst = Math.abs(dx) >= Math.abs(dz);
		Direction first = xFirst ? (dx != 0 ? xDir : null) : zDir;
		Direction second = xFirst ? (dz != 0 ? zDir : null) : (dx != 0 ? xDir : null);
		for (Direction dir : new Direction[] {first, second}) {
			if (dir == null) {
				continue;
			}
			BlockPos ahead = feet.relative(dir);
			if (solid(level, ahead, core)) {
				return ahead;
			}
			if (solid(level, ahead.above(), core)) {
				return ahead.above();
			}
		}
		if (core.getY() < feet.getY() && solid(level, feet.below(), core)) {
			return feet.below();
		}
		return null;
	}

	private static boolean solid(ServerLevel level, BlockPos pos, BlockPos core) {
		if (pos.equals(core)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
	}
}
