package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where base survivors put things: only Supply Lockers inside their own home claim (survivors plan, "everything comes
 * from the bunker"). A guard's weapon goes back on a role change ({@link #store}); the base roles of step 2 count and
 * take their supplies and food ({@link #count}, {@link #takeOne}) and a scavenger puts its haul in ({@link #fit}), all through
 * {@link #lockers}.
 */
public final class SupplyLockers {
	/** Where a returned item went. */
	public enum Stored {
		LOCKER,
		DROPPED
	}

	private SupplyLockers() {
	}

	/**
	 * Every Supply Locker in loaded chunks of {@code survivor}'s home claim, nearest first; empty without a home or when
	 * the home's core is gone. Unloaded chunks are skipped rather than loaded.
	 */
	public static List<SupplyLockerBlockEntity> lockers(ServerLevel level, Survivor survivor) {
		BlockPos home = survivor.home();
		if (home == null) {
			return List.of();
		}
		Optional<BunkerCoreRecord> core = Survivors.coreAt(level, home);
		if (core.isEmpty()) {
			return List.of();
		}
		ChunkArea area = core.get().area();
		List<SupplyLockerBlockEntity> found = new ArrayList<>();
		for (int cx = area.chunkX() - area.radius(); cx <= area.chunkX() + area.radius(); cx++) {
			for (int cz = area.chunkZ() - area.radius(); cz <= area.chunkZ() + area.radius(); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof SupplyLockerBlockEntity locker && !locker.isRemoved()) {
						found.add(locker);
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(l -> l.getBlockPos().distToCenterSqr(survivor.position())));
		return found;
	}

	/** Puts {@code stack} into the nearest locker with room in the survivor's home claim, else drops it at its feet. */
	public static Stored store(ServerLevel level, Survivor survivor, ItemStack stack) {
		ItemStack left = stack;
		for (SupplyLockerBlockEntity locker : lockers(level, survivor)) {
			left = insert(locker, left);
			if (left.isEmpty()) {
				return Stored.LOCKER;
			}
		}
		survivor.spawnAtLocation(level, left);
		return Stored.DROPPED;
	}

	/** How many items matching {@code what} the survivor's lockers hold (0 without a home). */
	public static int count(ServerLevel level, Survivor survivor, Predicate<ItemStack> what) {
		int n = 0;
		for (SupplyLockerBlockEntity locker : lockers(level, survivor)) {
			for (int i = 0; i < locker.getContainerSize(); i++) {
				ItemStack in = locker.getItem(i);
				if (!in.isEmpty() && what.test(in)) {
					n += in.getCount();
				}
			}
		}
		return n;
	}

	/** Takes one item matching {@code what} from the nearest locker that has one. @return it, or empty when none has */
	public static ItemStack takeOne(ServerLevel level, Survivor survivor, Predicate<ItemStack> what) {
		for (SupplyLockerBlockEntity locker : lockers(level, survivor)) {
			for (int i = 0; i < locker.getContainerSize(); i++) {
				ItemStack in = locker.getItem(i);
				if (!in.isEmpty() && what.test(in)) {
					ItemStack taken = locker.removeItem(i, 1);
					locker.setChanged();
					return taken;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Takes one item matching {@code what}, the first by {@code prefer} across all the lockers (ties go to the nearer
	 * locker). @return it, or empty when none has one
	 */
	public static ItemStack takeOne(ServerLevel level, Survivor survivor, Predicate<ItemStack> what, Comparator<ItemStack> prefer) {
		SupplyLockerBlockEntity bestLocker = null;
		int bestSlot = -1;
		for (SupplyLockerBlockEntity locker : lockers(level, survivor)) {
			for (int i = 0; i < locker.getContainerSize(); i++) {
				ItemStack in = locker.getItem(i);
				if (!in.isEmpty() && what.test(in) && (bestLocker == null || prefer.compare(in, bestLocker.getItem(bestSlot)) < 0)) {
					bestLocker = locker;
					bestSlot = i;
				}
			}
		}
		if (bestLocker == null) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = bestLocker.removeItem(bestSlot, 1);
		bestLocker.setChanged();
		return taken;
	}

	/** Puts as much of {@code stack} as fits into the lockers, nearest first. @return what did not fit (never dropped) */
	public static ItemStack fit(ServerLevel level, Survivor survivor, ItemStack stack) {
		ItemStack left = stack;
		for (SupplyLockerBlockEntity locker : lockers(level, survivor)) {
			if (left.isEmpty()) {
				break;
			}
			left = insert(locker, left);
		}
		return left;
	}

	/** Merges into matching stacks, then fills empty slots. @return what did not fit (empty when all of it did) */
	static ItemStack insert(Container container, ItemStack stack) {
		ItemStack left = stack.copy();
		for (int i = 0; i < container.getContainerSize() && !left.isEmpty(); i++) {
			ItemStack in = container.getItem(i);
			if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, left)) {
				int move = Math.min(left.getCount(), Math.min(in.getMaxStackSize(), container.getMaxStackSize()) - in.getCount());
				if (move > 0) {
					in.grow(move);
					left.shrink(move);
				}
			}
		}
		for (int i = 0; i < container.getContainerSize() && !left.isEmpty(); i++) {
			if (container.getItem(i).isEmpty() && container.canPlaceItem(i, left)) {
				container.setItem(i, left.split(Math.min(left.getCount(), container.getMaxStackSize())));
			}
		}
		container.setChanged();
		return left;
	}
}
