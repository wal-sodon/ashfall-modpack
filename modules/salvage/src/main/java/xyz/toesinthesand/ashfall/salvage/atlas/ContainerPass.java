package xyz.toesinthesand.ashfall.salvage.atlas;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.loot.LootPlaceholder;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives every generated container in a building Ashfall loot and tops each storey up to its profile's
 * container count.
 * <p>
 * The building type (and, for Lost Cities, its zone) decides the loot tier through the profile's loot table,
 * and the storey's floor area decides how many containers there are, which keeps loot scarce and predictable:
 * a house gets a cupboard or two, a warehouse floor a row of crates. Lost Cities parts already place chests on
 * some floors; those count toward the quota and get the profile table in place of the generation-time
 * placeholder ({@link xyz.toesinthesand.ashfall.salvage.loot.LootPlaceholder}), so only storeys whose parts
 * have too few containers get new ones. Ruined storeys are not topped up. Containers only get a loot table,
 * not items, so loot is rolled when a player first opens them, and their {@link LootState} lets the refill
 * system reroll them later.
 * <p>
 * Lost Cities also leaves chests outside buildings (subway and rail-dungeon chests under streets); in a city
 * chunk any such container that still has an unrolled loot table is generated loot, never a player's, and
 * gets generic building loot.
 */
public final class ContainerPass {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	private static final Set<Identifier> WARNED_MISSING = ConcurrentHashMap.newKeySet();
	private static final Identifier LOOTR_CHEST = Identifier.fromNamespaceAndPath("lootr", "chest");

	private ContainerPass() {
	}

	/** Counts of what one chunk's pass did. */
	public record Result(int converted, int placed) {
	}

	public static Result apply(ServerLevel level, LevelChunk chunk, BuildingSource buildings) {
		ChunkPos cp = chunk.getPos();
		int converted = 0, placed = 0;
		Set<BlockPos> handled = new HashSet<>();
		for (Building building : buildings.inChunk(cp.x(), cp.z())) {
			for (Unit unit : building.units()) {
				Footprint f = unit.footprint();
				if (f.maxX() < cp.getMinBlockX() || f.minX() > cp.getMaxBlockX()
						|| f.maxZ() < cp.getMinBlockZ() || f.minZ() > cp.getMaxBlockZ()) {
					continue;
				}
				Identifier profileId = BuildingProfiles.classify(building, unit);
				BuildingProfile profile = BuildingProfiles.get(profileId);
				if (profile == null) {
					warnMissing(profileId);
				}
				long salt = unitSalt(building, unit);

				// Generated chests in this unit (within this chunk) count toward the unit's quota.
				int existing = 0;
				for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
					BlockPos pos = be.getBlockPos();
					if (isLootContainer(be) && unit.containsY(pos.getY()) && f.contains(pos.getX(), pos.getZ()) && handled.add(pos)) {
						if (!be.hasAttached(AshfallSalvage.LOOT_STATE) && !isAuthoredCache(be)) {
							convert(be, building.id(), profileId, profile);
							converted++;
						}
						existing++;
					}
				}
				if (profile == null) {
					continue;
				}
				if (unit.floorY() >= LostCitiesBuildingSource.ruinY(building)) {
					continue; // Lost Cities ruined this storey: whatever chests survived count, nothing is added
				}
				BuildingProfile.Containers spec = profile.containers();
				int target = QuotaAllocator.target(spec.per100m2(), spec.min(), spec.max(), f.area());
				int quota = QuotaAllocator.allocate(target, cellsPerChunk(f), salt).getOrDefault(cp.pack(), 0);
				int toPlace = quota - existing;
				if (toPlace <= 0) {
					continue;
				}
				List<BlockPos> candidates = CandidateRanking.rank(candidates(level, chunk, unit), salt);
				for (BlockPos pos : candidates) {
					if (toPlace <= 0) {
						break;
					}
					Direction wall = wallSide(level, pos);
					if (wall == null || nextToContainer(level, pos)) {
						continue; // a container placed earlier in this pass took the spot's neighbourhood
					}
					BlockState state = pickBlock(spec, AtlasHash.hash(salt, pos.asLong(), 0xB10CL));
					Direction facing = wall.getOpposite();
					if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
						state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
					} else if (state.hasProperty(BlockStateProperties.FACING)) {
						state = state.setValue(BlockStateProperties.FACING, facing);
					}
					level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					BlockEntity be = chunk.getBlockEntity(pos);
					if (be != null) {
						handled.add(pos);
						convert(be, building.id(), profileId, profile);
					}
					placed++;
					toPlace--;
				}
			}
		}

		placed += placeCaches(level, chunk, buildings, handled);

		// Generated chests inside a building but outside any unit (stairwells, roofs, walls between storeys):
		// still not allowed to keep their generated loot. Containers outside every building are left alone unless
		// they are Lost Cities street loot: anything else is natural structure loot beyond the city, or a player's.
		for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
			BlockPos pos = be.getBlockPos();
			if (!isLootContainer(be) || handled.contains(pos) || be.hasAttached(AshfallSalvage.LOOT_STATE) || isAuthoredCache(be)) {
				continue;
			}
			Building building = buildings.at(pos);
			if (building == null) {
				if (isGeneratedCityLoot(buildings, cp, be)) {
					convert(be, cp.pack(), BuildingProfiles.GENERIC, BuildingProfiles.get(BuildingProfiles.GENERIC));
					converted++;
				}
				continue;
			}
			Identifier profileId = BuildingProfiles.classifyByTags(building);
			if (profileId == null) {
				profileId = BuildingProfiles.GENERIC;
			}
			convert(be, building.id(), profileId, BuildingProfiles.get(profileId));
			converted++;
		}
		return new Result(converted, placed);
	}

	/**
	 * One per-player cache chest for each building whose tag profile has a cache table (police
	 * armory, hospital supply room). Its spot is the building's lowest-hash ground-floor unit cell, so
	 * exactly one chunk owns it; if that cell can't hold a container, the best candidate of the same
	 * unit in that chunk is used. It gets no {@link LootState}: Lootr turns it into a per-player chest
	 * with its own refresh rules.
	 */
	private static int placeCaches(ServerLevel level, LevelChunk chunk, BuildingSource buildings, Set<BlockPos> handled) {
		ChunkPos cp = chunk.getPos();
		int placed = 0;
		for (Building building : buildings.inChunk(cp.x(), cp.z())) {
			Identifier profileId = BuildingProfiles.classifyByTags(building);
			BuildingProfile profile = profileId == null ? null : BuildingProfiles.get(profileId);
			if (profile == null || profile.cacheTable().isEmpty()) {
				continue;
			}
			Unit groundUnit = null;
			BlockPos cell = null;
			long best = Long.MAX_VALUE;
			for (Unit unit : building.units()) {
				if (unit.floor() != 0) {
					continue;
				}
				final Unit u = unit;
				final long[] min = {best};
				final BlockPos[] at = {cell};
				unit.footprint().forEach((x, z) -> {
					long h = AtlasHash.hash(building.id(), x, z, 0xCAC4EL);
					if (h < min[0]) {
						min[0] = h;
						at[0] = new BlockPos(x, u.floorY() + 1, z);
					}
				});
				if (min[0] < best) {
					best = min[0];
					cell = at[0];
					groundUnit = unit;
				}
			}
			if (cell == null || (cell.getX() >> 4) != cp.x() || (cell.getZ() >> 4) != cp.z()) {
				continue;
			}
			BlockPos spot = isCandidate(level, chunk, cell) && !nextToContainer(level, cell) ? cell : null;
			if (spot == null) {
				for (BlockPos pos : CandidateRanking.rank(candidates(level, chunk, groundUnit), unitSalt(building, groundUnit))) {
					if (wallSide(level, pos) != null && !nextToContainer(level, pos)) {
						spot = pos;
						break;
					}
				}
			}
			if (spot == null) {
				continue;
			}
			Direction wall = wallSide(level, spot);
			BlockState state = cacheChest();
			if (wall != null) {
				state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, wall.getOpposite());
			}
			level.setBlock(spot, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (chunk.getBlockEntity(spot) instanceof RandomizableContainer rc) {
				rc.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, profile.cacheTable().get()),
						AtlasHash.hash(building.id(), spot.asLong(), 0xCAC4EL));
				handled.add(spot);
				placed++;
			}
		}
		return placed;
	}

	/**
	 * Lootr's own chest when Lootr is installed, so the cache is per-player from the start rather than
	 * waiting for Lootr's lazy conversion; a plain chest otherwise.
	 */
	private static BlockState cacheChest() {
		return BuiltInRegistries.BLOCK.getOptional(LOOTR_CHEST).map(Block::defaultBlockState)
				.filter(s -> s.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
				.orElse(Blocks.CHEST.defaultBlockState());
	}

	/**
	 * A unit classified to a profile that is not loaded (only possible when the datapack lacks
	 * {@code ashfall:generic}) still has its generated items cleared but gets no loot; say so once, since
	 * the result is silently empty chests.
	 */
	private static void warnMissing(Identifier profileId) {
		if (WARNED_MISSING.add(profileId)) {
			AshfallSalvage.LOGGER.warn("Building profile {} is not loaded; containers it classifies are cleared and left empty", profileId);
		}
	}

	/**
	 * A container outside every building of a Lost Cities city chunk that still holds an unrolled loot table:
	 * Lost Cities put it there (subway, rail dungeon, street furniture). Player chests never have one.
	 */
	static boolean isGeneratedCityLoot(BuildingSource buildings, ChunkPos cp, BlockEntity be) {
		return be instanceof RandomizableContainer rc && rc.getLootTable() != null
				&& buildings.chunk(cp.x(), cp.z()).map(CityChunk::isCity).orElse(false);
	}

	/** Chests and barrels: the container kinds Lost Cities and Arnis furnish with and Ashfall places. */
	static boolean isLootContainer(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
	}

	/**
	 * A chest a landmark design placed with an {@code ashfall_cache} table (the safehouse's Field Radio cache, the
	 * police armory, the Kiln &amp; Co. office): it is a story or reward cache with its own table, so the pass must
	 * not swap it for the building profile's loot. It still counts toward the storey's container quota.
	 */
	public static boolean isAuthoredCache(BlockEntity be) {
		if (!(be instanceof RandomizableContainer rc) || rc.getLootTable() == null) {
			return false;
		}
		return LootPlaceholder.CACHE_NAMESPACE.equals(rc.getLootTable().identifier().getNamespace());
	}

	/** Clears any generated items and points the container at the profile's loot table. */
	static void convert(BlockEntity be, long buildingId, Identifier profileId, BuildingProfile profile) {
		if (be instanceof Container container) {
			container.clearContent();
		}
		Optional<Identifier> table = profile == null ? Optional.empty() : profile.lootTable();
		if (table.isPresent() && be instanceof RandomizableContainer rc) {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, table.get());
			rc.setLootTable(key, AtlasHash.hash(buildingId, be.getBlockPos().asLong()));
			be.setAttached(AshfallSalvage.LOOT_STATE, new LootState(table.get(), Optional.of(profileId), Optional.empty(), -1, 0));
		}
		be.setChanged();
	}

	static long unitSalt(Building building, Unit unit) {
		return AtlasHash.hash(building.id(), unit.floor(), unit.arnisUse().hashCode());
	}

	/** Packed chunk pos -> cells of the footprint in that chunk. */
	static Long2IntMap cellsPerChunk(Footprint f) {
		Long2IntMap cells = new Long2IntOpenHashMap();
		f.forEach((x, z) -> cells.mergeInt(ChunkPos.pack(x >> 4, z >> 4), 1, Integer::sum));
		return cells;
	}

	/**
	 * Cells of the unit inside this chunk where a container fits: on the unit's floor, empty or
	 * carpeted, standing on a solid floor, against a wall, not in a 1-wide corridor, not next to a
	 * door or ladder, and leaving a walkable neighbour so the room stays passable.
	 */
	static List<BlockPos> candidates(ServerLevel level, LevelChunk chunk, Unit unit) {
		ChunkPos cp = chunk.getPos();
		int y = unit.floorY() + 1;
		List<BlockPos> out = new ArrayList<>();
		for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
			for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
				if (!unit.footprint().contains(x, z)) {
					continue;
				}
				BlockPos pos = new BlockPos(x, y, z);
				if (isCandidate(level, chunk, pos)) {
					out.add(pos);
				}
			}
		}
		return out;
	}

	static boolean isCandidate(ServerLevel level, LevelChunk chunk, BlockPos pos) {
		BlockState here = chunk.getBlockState(pos);
		if (!(here.isAir() || here.is(BlockTags.WOOL_CARPETS) || (here.canBeReplaced() && here.getFluidState().isEmpty()))) {
			return false;
		}
		BlockPos below = pos.below();
		if (!chunk.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		boolean[] wall = new boolean[4];
		boolean free = false;
		for (int i = 0; i < 4; i++) {
			BlockPos np = pos.relative(HORIZONTAL[i]);
			BlockState n = AtlasProcessor.peek(level, np);
			if (n == null) {
				continue; // unloaded neighbour: neither a wall nor a way through
			}
			if (n.is(BlockTags.DOORS) || n.is(BlockTags.CLIMBABLE) || n.is(BlockTags.TRAPDOORS)) {
				return false;
			}
			wall[i] = n.isFaceSturdy(level, np, HORIZONTAL[i].getOpposite());
			if (!wall[i] && walkable(level, np)) {
				free = true;
			}
		}
		boolean anyWall = wall[0] || wall[1] || wall[2] || wall[3];
		boolean corridor = (wall[0] && wall[2]) || (wall[1] && wall[3]);
		return anyWall && free && !corridor;
	}

	private static boolean walkable(ServerLevel level, BlockPos pos) {
		BlockState feet = AtlasProcessor.peek(level, pos);
		BlockState head = AtlasProcessor.peek(level, pos.above());
		return feet != null && head != null && feet.getCollisionShape(level, pos).isEmpty()
				&& head.getCollisionShape(level, pos.above()).isEmpty();
	}

	/** The first horizontal side with a sturdy wall, or null. */
	static Direction wallSide(ServerLevel level, BlockPos pos) {
		for (Direction d : HORIZONTAL) {
			BlockPos np = pos.relative(d);
			BlockState n = AtlasProcessor.peek(level, np);
			if (n != null && n.isFaceSturdy(level, np, d.getOpposite())) {
				return d;
			}
		}
		return null;
	}

	/** True if a chest or barrel already stands on a horizontal neighbour (keeps containers spread out). */
	static boolean nextToContainer(ServerLevel level, BlockPos pos) {
		for (Direction d : HORIZONTAL) {
			BlockState n = AtlasProcessor.peek(level, pos.relative(d));
			if (n != null && (n.is(Blocks.CHEST) || n.is(Blocks.TRAPPED_CHEST) || n.is(Blocks.BARREL))) {
				return true;
			}
		}
		return false;
	}

	static BlockState pickBlock(BuildingProfile.Containers spec, long hash) {
		List<BuildingProfile.ContainerBlock> blocks = spec.blocks();
		int[] weights = new int[blocks.size()];
		for (int i = 0; i < weights.length; i++) {
			weights[i] = blocks.get(i).weight();
		}
		int pick = CandidateRanking.weightedPick(weights, hash);
		if (pick >= 0) {
			Block block = BuiltInRegistries.BLOCK.getValue(blocks.get(pick).block());
			if (block != Blocks.AIR) {
				return block.defaultBlockState();
			}
			AshfallSalvage.LOGGER.warn("Unknown container block {} in a building profile; using a chest", blocks.get(pick).block());
		}
		return Blocks.CHEST.defaultBlockState();
	}
}
