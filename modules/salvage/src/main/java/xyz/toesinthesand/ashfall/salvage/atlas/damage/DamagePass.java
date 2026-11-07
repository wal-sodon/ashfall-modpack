package xyz.toesinthesand.ashfall.salvage.atlas.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasHash;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Light apocalypse damage applied once per chunk by the atlas post-processor: the city looks recently
 * fallen, not destroyed.
 * <p>
 * On an Arnis map (no ruined mode) this was the only damage, so it weathered walls, hung vines and strewed
 * debris too. Lost Cities already makes ruins, explosions, rubble and vines, so in a Lost Cities world those
 * effects are off ({@link BuildingSource#generatorDamage()}) and only what Lost Cities lacks is kept: broken and
 * boarded windows, abandoned cars and barricades on street chunks. Their intensity follows the zone's
 * {@code damage_scale} (Downtown 0.6, Montford 0.7, districts 1.0, wasteland 1.5), and zone landmarks (which Lost
 * Cities never ruins) are only boarded up, so a substation or police station still reads as intact.
 * <p>
 * Every choice is a pure hash of (seed, position, purpose) so chunks can be processed in any order,
 * and nothing is written outside the given chunk.
 */
public final class DamagePass {
	// Building shells.
	static final double WINDOW_BREAK = 0.15;
	static final double WINDOW_BOARD = 0.05;
	static final double WALL_WEATHER = 0.10;
	static final double WALL_VINES = 0.03;
	// Streets.
	static final double ROAD_DEBRIS = 1.0 / 150;
	static final double GROUND_DEAD_BUSH = 1.0 / 200;
	static final int CAR_MIN_ROAD_COLUMNS = 120;
	static final double CAR_CHANCE = 0.25;
	static final double BARRICADE_CHANCE = 1.0 / 6;

	/**
	 * Chances for one chunk. Arnis maps use the base values everywhere; Lost Cities worlds scale the kept
	 * effects by the zone and zero the ones Lost Cities already does.
	 */
	public record Settings(double windowBreak, double windowBoard, double wallWeather, double wallVines, double roadDebris,
						   double deadBush, double carChance, double barricadeChance) {
		public static final Settings ARNIS = new Settings(WINDOW_BREAK, WINDOW_BOARD, WALL_WEATHER, WALL_VINES, ROAD_DEBRIS,
				GROUND_DEAD_BUSH, CAR_CHANCE, BARRICADE_CHANCE);

		/** The settings for a chunk in {@code zone}; {@code generatorDamage} = Lost Cities already damages it. */
		public static Settings of(Zone zone, boolean generatorDamage) {
			if (!generatorDamage) {
				return ARNIS;
			}
			double k = Math.max(0, zone.damageScale());
			return new Settings(clamp(WINDOW_BREAK * k), clamp(WINDOW_BOARD * k), 0, 0, 0, 0, clamp(CAR_CHANCE * k),
					clamp(BARRICADE_CHANCE * k));
		}

		/** Landmarks: windows get boarded (someone held out there), never smashed or weathered. */
		public Settings boardingOnly() {
			return new Settings(0, windowBoard, 0, 0, roadDebris, deadBush, carChance, barricadeChance);
		}

		private static double clamp(double chance) {
			return Math.min(1.0, chance);
		}
	}

	/** Arnis road surfaces: the default asphalt mix, black concrete and lane paint (highways.rs). */
	private static final Set<Block> ROAD = Set.of(Blocks.CONCRETE_POWDER.gray(), Blocks.DYED_TERRACOTTA.cyan(),
			Blocks.CONCRETE.black(), Blocks.CONCRETE.white(), Blocks.CONCRETE.yellow());

	private static final Map<Block, Block[]> WEATHERED = Map.of(
			Blocks.STONE_BRICKS, new Block[]{Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS},
			Blocks.COBBLESTONE, new Block[]{Blocks.MOSSY_COBBLESTONE},
			Blocks.MUD_BRICKS, new Block[]{Blocks.PACKED_MUD},
			Blocks.DEEPSLATE_BRICKS, new Block[]{Blocks.CRACKED_DEEPSLATE_BRICKS},
			Blocks.POLISHED_BLACKSTONE_BRICKS, new Block[]{Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS},
			Blocks.NETHER_BRICKS, new Block[]{Blocks.CRACKED_NETHER_BRICKS},
			Blocks.END_STONE_BRICKS, new Block[]{Blocks.STONE_BRICKS});

	private static final List<Block> CAR_BODIES = List.of(Blocks.CONCRETE.red(), Blocks.CONCRETE.blue(), Blocks.CONCRETE.white(),
			Blocks.CONCRETE.black(), Blocks.CONCRETE.lightGray(), Blocks.DYED_TERRACOTTA.green(), Blocks.DYED_TERRACOTTA.brown());

	// Purpose salts, so different effects at one position roll independently.
	private static final long WINDOW = 1, BOARD = 2, WEATHER = 3, WEATHER_KIND = 4, VINES = 5, VINE_LEN = 6,
			DEBRIS = 7, DEBRIS_KIND = 8, BUSH = 9, CAR = 10, CAR_SPOT = 11, BARRICADE = 12, BARRICADE_SPOT = 13;

	private DamagePass() {
	}

	/**
	 * Damages one chunk. Deterministic for a given (chunk, seed); only touches blocks inside this chunk.
	 *
	 * @return number of blocks changed
	 */
	public static int apply(ServerLevel level, LevelChunk chunk, BuildingSource buildings, long seed) {
		Writer writer = new Writer(level, chunk);
		ChunkPos cp = chunk.getPos();
		int baseX = cp.getMinBlockX(), baseZ = cp.getMinBlockZ();
		List<Building> here = buildings.inChunk(cp.x(), cp.z());
		Zone zone = ZoneMap.zoneAtChunk(cp.x(), cp.z());
		boolean generatorDamage = buildings.generatorDamage();
		Settings settings = Settings.of(zone, generatorDamage);

		for (Building building : here) {
			damageShell(writer, building, baseX, baseZ, seed, isLandmark(building, zone) ? settings.boardingOnly() : settings);
		}

		// Street surface. Arnis: road blocks outside every footprint. Lost Cities: the flat paved columns of a
		// street chunk at city ground level (the plan says which chunks are streets; building chunks get none).
		Optional<CityChunk> plan = buildings.chunk(cp.x(), cp.z());
		boolean lcStreet = generatorDamage && plan.isPresent() && plan.get().isStreet();
		int groundY = plan.map(CityChunk::groundY).orElse(Integer.MIN_VALUE);
		int[] surfaceY = new int[256];
		boolean[] road = new boolean[256];
		int roadCount = 0;
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int i = dz * 16 + dx, x = baseX + dx, z = baseZ + dz;
				surfaceY[i] = Integer.MIN_VALUE;
				if (insideAny(here, x, z) || (generatorDamage && !lcStreet)) {
					continue;
				}
				int y = surface(chunk, x, z);
				surfaceY[i] = y;
				if (y == Integer.MIN_VALUE) {
					continue;
				}
				BlockState top = chunk.getBlockState(new BlockPos(x, y, z));
				boolean isRoad = lcStreet ? Math.abs(y - groundY) <= 1 && !top.is(BlockTags.DIRT) && !top.is(BlockTags.LEAVES)
						: ROAD.contains(top.getBlock());
				if (isRoad) {
					road[i] = true;
					roadCount++;
				}
			}
		}
		streetDebris(writer, surfaceY, road, baseX, baseZ, seed, settings);
		if (roadCount >= CAR_MIN_ROAD_COLUMNS && roll(seed, CAR, baseX, 0, baseZ) < settings.carChance()) {
			abandonedCar(writer, surfaceY, road, baseX, baseZ, seed);
		}
		if (roadCount > 0 && roll(seed, BARRICADE, baseX, 0, baseZ) < settings.barricadeChance()) {
			barricade(writer, surfaceY, road, baseX, baseZ, seed);
		}
		return writer.changed;
	}

	/** A landmark of its zone (substation, police station...): listed by building or multibuilding id. */
	static boolean isLandmark(Building building, Zone zone) {
		if (zone.landmarks().isEmpty()) {
			return false;
		}
		String id = building.tags().get(LostCitiesBuildingSource.TAG_BUILDING);
		String multi = building.tags().get(LostCitiesBuildingSource.TAG_MULTI);
		return (id != null && zone.landmarks().contains(id)) || (multi != null && zone.landmarks().contains(multi));
	}

	/** Windows and outer walls: only columns on the footprint's edge face the street. */
	private static void damageShell(Writer writer, Building building, int baseX, int baseZ, long seed, Settings settings) {
		Footprint f = building.footprint();
		// Boards only go on the ground storey; a Lost Cities building's base is the bottom of its cellars.
		int cellars = Integer.parseInt(building.tags().getOrDefault(LostCitiesBuildingSource.TAG_CELLARS, "0"));
		int groundTop = building.baseY() + LostCitiesBuildingSource.FLOOR_HEIGHT * cellars + 4;
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = baseX + dx, z = baseZ + dz;
				if (!f.contains(x, z)) {
					continue;
				}
				Direction outside = outwardFace(f, x, z);
				if (outside == null) {
					continue;
				}
				for (int y = building.baseY() + 1; y <= building.topY(); y++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = writer.get(pos);
					if (isWindow(state)) {
						if (y <= groundTop && roll(seed, BOARD, x, y, z) < settings.windowBoard()) {
							writer.set(pos, boardFor(outside));
						} else if (roll(seed, WINDOW, x, y, z) < settings.windowBreak()) {
							writer.set(pos, Blocks.AIR.defaultBlockState());
						}
					} else if (WEATHERED.containsKey(state.getBlock()) && roll(seed, WEATHER, x, y, z) < settings.wallWeather()) {
						Block[] options = WEATHERED.get(state.getBlock());
						writer.set(pos, options[(int) (roll(seed, WEATHER_KIND, x, y, z) * options.length)].defaultBlockState());
					}
				}
				// Vines hang from the top of the wall down the outside face.
				if (roll(seed, VINES, x, 0, z) < settings.wallVines()) {
					int length = 1 + (int) (roll(seed, VINE_LEN, x, 0, z) * 4);
					BlockPos wallTop = new BlockPos(x, building.topY(), z);
					for (int k = 0; k < length; k++) {
						BlockPos vine = wallTop.below(k).relative(outside);
						if (!writer.inChunk(vine) || !writer.get(vine).isAir() || writer.get(vine.relative(outside.getOpposite())).isAir()) {
							break;
						}
						writer.set(vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(outside.getOpposite()), true));
					}
				}
			}
		}
	}

	private static void streetDebris(Writer writer, int[] surfaceY, boolean[] road, int baseX, int baseZ, long seed, Settings settings) {
		if (settings.roadDebris() <= 0 && settings.deadBush() <= 0) {
			return;
		}
		for (int i = 0; i < 256; i++) {
			int y = surfaceY[i];
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			int x = baseX + (i & 15), z = baseZ + (i >> 4);
			BlockPos above = new BlockPos(x, y + 1, z);
			if (!writer.get(above).isAir()) {
				continue;
			}
			if (road[i] && roll(seed, DEBRIS, x, y, z) < settings.roadDebris()) {
				double kind = roll(seed, DEBRIS_KIND, x, y, z);
				BlockState debris = kind < 0.35 ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
						: kind < 0.6 ? Blocks.ANDESITE_SLAB.defaultBlockState()
						: kind < 0.85 ? Blocks.GRAVEL.defaultBlockState()
						: kind < 0.95 ? Blocks.COBBLESTONE.defaultBlockState()
						: Blocks.COBWEB.defaultBlockState();
				writer.set(above, debris);
			} else if (!road[i] && writer.get(above.below()).is(BlockTags.DIRT) && roll(seed, BUSH, x, y, z) < settings.deadBush()) {
				writer.set(above, Blocks.DEAD_BUSH.defaultBlockState());
			}
		}
	}

	/** A 4x2 block-built car parked along the road's longer axis. */
	private static void abandonedCar(Writer writer, int[] surfaceY, boolean[] road, int baseX, int baseZ, long seed) {
		boolean alongX = runLength(road, true) >= runLength(road, false);
		int length = 4, width = 2;
		int spanX = alongX ? length : width, spanZ = alongX ? width : length;
		int start = (int) (roll(seed, CAR_SPOT, baseX, 1, baseZ) * 256);
		for (int n = 0; n < 256; n++) {
			int i = (start + n) & 255, ox = i & 15, oz = i >> 4;
			if (ox + spanX > 16 || oz + spanZ > 16 || !flatClearRoad(writer, surfaceY, road, baseX, baseZ, ox, oz, spanX, spanZ, 2)) {
				continue;
			}
			int y = surfaceY[oz * 16 + ox] + 1;
			BlockState body = CAR_BODIES.get((int) (roll(seed, CAR, baseX, 2, baseZ) * CAR_BODIES.size())).defaultBlockState();
			BlockState hood = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			for (int a = 0; a < length; a++) {
				for (int b = 0; b < width; b++) {
					int x = baseX + ox + (alongX ? a : b), z = baseZ + oz + (alongX ? b : a);
					writer.set(new BlockPos(x, y, z), body);
					boolean cabin = a == 1 || a == 2;
					writer.set(new BlockPos(x, y + 1, z), cabin ? Blocks.STAINED_GLASS.black().defaultBlockState() : hood);
				}
			}
			return;
		}
	}

	/** A short line of fences or walls across the road, 3-6 blocks long. */
	private static void barricade(Writer writer, int[] surfaceY, boolean[] road, int baseX, int baseZ, long seed) {
		boolean roadAlongX = runLength(road, true) >= runLength(road, false);
		boolean lineAlongX = !roadAlongX;
		int length = 3 + (int) (roll(seed, BARRICADE, baseX, 1, baseZ) * 4);
		BlockState block = roll(seed, BARRICADE, baseX, 2, baseZ) < 0.5 ? Blocks.OAK_FENCE.defaultBlockState()
				: Blocks.COBBLESTONE_WALL.defaultBlockState();
		int start = (int) (roll(seed, BARRICADE_SPOT, baseX, 0, baseZ) * 256);
		for (int n = 0; n < 256; n++) {
			int i = (start + n) & 255, ox = i & 15, oz = i >> 4;
			int spanX = lineAlongX ? length : 1, spanZ = lineAlongX ? 1 : length;
			if (ox + spanX > 16 || oz + spanZ > 16 || !flatClearRoad(writer, surfaceY, road, baseX, baseZ, ox, oz, spanX, spanZ, 1)) {
				continue;
			}
			int y = surfaceY[oz * 16 + ox] + 1;
			for (int k = 0; k < length; k++) {
				writer.set(new BlockPos(baseX + ox + (lineAlongX ? k : 0), y, baseZ + oz + (lineAlongX ? 0 : k)), block);
			}
			return;
		}
	}

	/** All columns in the rectangle are road at one height with {@code clearance} air blocks above. */
	private static boolean flatClearRoad(Writer writer, int[] surfaceY, boolean[] road, int baseX, int baseZ,
										 int ox, int oz, int spanX, int spanZ, int clearance) {
		int y = surfaceY[oz * 16 + ox];
		for (int dz = 0; dz < spanZ; dz++) {
			for (int dx = 0; dx < spanX; dx++) {
				int i = (oz + dz) * 16 + ox + dx;
				if (!road[i] || surfaceY[i] != y) {
					return false;
				}
				for (int h = 1; h <= clearance; h++) {
					if (!writer.get(new BlockPos(baseX + ox + dx, y + h, baseZ + oz + dz)).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Longest straight run of road columns along x (or z), to orient cars and barricades. */
	static int runLength(boolean[] road, boolean alongX) {
		int best = 0;
		for (int a = 0; a < 16; a++) {
			int run = 0;
			for (int b = 0; b < 16; b++) {
				int i = alongX ? a * 16 + b : b * 16 + a;
				run = road[i] ? run + 1 : 0;
				best = Math.max(best, run);
			}
		}
		return best;
	}

	/** Direction from an edge column to the outside of the footprint, or null for interior columns. */
	private static Direction outwardFace(Footprint f, int x, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!f.contains(x + d.getStepX(), z + d.getStepZ())) {
				return d;
			}
		}
		return null;
	}

	private static boolean insideAny(List<Building> buildings, int x, int z) {
		for (Building b : buildings) {
			if (b.footprint().contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Highest solid, non-foliage block of a column, or MIN_VALUE if the column is empty. */
	private static int surface(LevelChunk chunk, int x, int z) {
		int top = chunk.getHighestFilledSectionIndex();
		if (top < 0) {
			return Integer.MIN_VALUE;
		}
		int maxY = chunk.getSectionYFromSectionIndex(top) * 16 + 15;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);
		for (int y = maxY; y >= chunk.getMinY(); y--) {
			pos.setY(y);
			BlockState state = chunk.getBlockState(pos);
			if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.canBeReplaced() && !state.is(BlockTags.LOGS)) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean isWindow(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.GLASS || block == Blocks.GLASS_PANE || block == Blocks.TINTED_GLASS
				|| Blocks.STAINED_GLASS.asList().contains(block) || Blocks.STAINED_GLASS_PANE.asList().contains(block);
	}

	private static BlockState boardFor(Direction outside) {
		return Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
				.setValue(TrapDoorBlock.FACING, outside)
				.setValue(TrapDoorBlock.OPEN, true)
				.setValue(TrapDoorBlock.HALF, Half.BOTTOM);
	}

	static double roll(long seed, long purpose, int x, int y, int z) {
		return AtlasHash.unit(AtlasHash.hash(seed, purpose, x, y, z));
	}

	/** Reads and writes blocks of one chunk; refuses writes outside it and counts changes. */
	private static final class Writer {
		private final ServerLevel level;
		private final LevelChunk chunk;
		int changed;

		Writer(ServerLevel level, LevelChunk chunk) {
			this.level = level;
			this.chunk = chunk;
		}

		boolean inChunk(BlockPos pos) {
			return (pos.getX() >> 4) == chunk.getPos().x() && (pos.getZ() >> 4) == chunk.getPos().z();
		}

		/**
		 * Never loads a neighbour: {@code level.getBlockState} would wait for (or generate) an unloaded chunk on
		 * the server thread. The atlas only processes chunks whose ring is loaded, so this falls back to air only
		 * when a caller (tests, admin commands) runs the pass on an edge chunk.
		 */
		BlockState get(BlockPos pos) {
			if (inChunk(pos)) {
				return chunk.getBlockState(pos);
			}
			BlockState peeked = AtlasProcessor.peek(level, pos);
			return peeked != null ? peeked : Blocks.AIR.defaultBlockState();
		}

		void set(BlockPos pos, BlockState state) {
			if (!inChunk(pos) || pos.getY() < chunk.getMinY() || pos.getY() > chunk.getMaxY()) {
				return;
			}
			BlockState current = chunk.getBlockState(pos);
			// Never damage doors, ladders, beds or containers. Nor any point-of-interest block (workstations,
			// bells, ...): Lost Cities writes its buildings straight into chunk sections, so their POIs are not
			// registered until the chunk is next read from disk, and replacing one now would make vanilla log
			// "POI data mismatch: never registered".
			if (current.equals(state) || current.hasBlockEntity() || current.is(BlockTags.DOORS)
					|| current.is(Blocks.LADDER) || current.is(BlockTags.BEDS) || PoiTypes.forState(current).isPresent()) {
				return;
			}
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			changed++;
		}
	}
}
