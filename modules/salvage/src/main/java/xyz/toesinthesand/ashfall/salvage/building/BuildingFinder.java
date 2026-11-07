package xyz.toesinthesand.ashfall.salvage.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds buildings for things sent into the city (radio jobs): a random building in a ring around a point, and an open
 * spot to stand on inside one. Sampling, not a full scan: a 600-block ring is ~20,000 chunk plans, where a few dozen
 * random chunks find a match just as well.
 */
public final class BuildingFinder {
	private BuildingFinder() {
	}

	/**
	 * A random building whose footprint lies {@code min}-{@code max} blocks from {@code centre} and that passes
	 * {@code filter}, trying up to {@code tries} random chunks in the ring.
	 */
	public static Optional<Building> random(BuildingSource source, BlockPos centre, int min, int max,
			Predicate<Building> filter, RandomSource random, int tries) {
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < tries; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = min + random.nextDouble() * (max - min);
			int cx = (int) Math.floor(centre.getX() + Math.cos(angle) * r) >> 4;
			int cz = (int) Math.floor(centre.getZ() + Math.sin(angle) * r) >> 4;
			for (Building b : source.inChunk(cx, cz)) {
				if (!seen.add(b.id())) {
					continue;
				}
				double d = distanceToBox(b.footprint(), centre);
				if (d >= min && d <= max && filter.test(b)) {
					return Optional.of(b);
				}
			}
		}
		return Optional.empty();
	}

	/** Horizontal distance from a block to a footprint's bounding box (0 inside it). */
	public static double distanceToBox(Footprint f, BlockPos pos) {
		int dx = Math.max(0, Math.max(f.minX() - pos.getX(), pos.getX() - f.maxX()));
		int dz = Math.max(0, Math.max(f.minZ() - pos.getZ(), pos.getZ() - f.maxZ()));
		return Math.sqrt((double) dx * dx + (double) dz * dz);
	}

	/**
	 * Two clear blocks over a solid one on the given storey ({@link Unit#floor()}; 0 is the ground floor), searched
	 * outward from the middle of the footprint, so things land in the room rather than against an outer wall. Only
	 * loaded chunks are looked at. @param floor the storey; above the top one means the top one
	 */
	public static Optional<BlockPos> floorSpot(ServerLevel level, Building building, int floor) {
		Unit unit = null;
		for (Unit u : building.units()) {
			if (u.floor() <= floor && u.floor() >= 0 && (unit == null || u.floor() > unit.floor())) {
				unit = u;
			}
		}
		int y = unit != null ? unit.floorY() + 1 : (building.floors().length > 0 ? building.floors()[0] + 1 : building.baseY());
		Footprint f = unit != null ? unit.footprint() : building.footprint();
		int midX = (f.minX() + f.maxX()) / 2, midZ = (f.minZ() + f.maxZ()) / 2;
		int reach = Math.max(f.maxX() - f.minX(), f.maxZ() - f.minZ()) / 2 + 1;
		for (int ring = 0; ring <= reach; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !f.contains(midX + dx, midZ + dz)) {
						continue;
					}
					// The slab may be a block off the plan's floor height (stairs, raised entrances): look one up and down.
					for (int dy : new int[] {0, 1, -1}) {
						BlockPos pos = new BlockPos(midX + dx, y + dy, midZ + dz);
						if (level.isLoaded(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
								&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
							return Optional.of(pos);
						}
					}
				}
			}
		}
		return Optional.empty();
	}
}
