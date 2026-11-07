package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.bike.Bicycle;
import xyz.toesinthesand.ashfall.salvage.bike.Bikes;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;

import java.util.Optional;

/**
 * Street props. Exterior loot: some city street chunks get a dumpster (a barrel, lid up) or a street crate (a chest, facing away
 * from the wall it stands by, if any). Why: all salvage used to be indoors, so streets were only something to cross.
 * The choice is a pure function of the seed and chunk, like ambient relays ({@link RelayPlan#isAmbient}); the loot is
 * a one-time find (a {@link LootState} with no building or zone, like a crash site's chest), never refilled.
 * Bicycles: a few street chunks get one leaning on a building wall ({@link #BIKE_CHANCE}); there is no recipe.
 */
public final class StreetProps {
	/** Share of city street chunks with an exterior container. */
	public static final double CHANCE = 0.12;
	/** Share of those that are dumpsters; the rest are street crates. */
	public static final double DUMPSTER_SHARE = 0.6;
	public static final Identifier DUMPSTER_TABLE = AshfallSalvage.id("exterior/dumpster");
	public static final Identifier CRATE_TABLE = AshfallSalvage.id("exterior/street_crate");
	/** Share of city street chunks with a bicycle left against a building wall. */
	public static final double BIKE_CHANCE = 0.02;
	private static final long SALT = 0x57EE7L, CELL_SALT = 0x57CE11L, KIND_SALT = 0x57D1EL, BIKE_SALT = 0xB1CEL;
	/** Spots tried per chunk when looking for a wall to lean a bike on. */
	private static final int BIKE_TRIES = 8;

	private StreetProps() {
	}

	/** Whether this street chunk gets an exterior container. */
	public static boolean chosen(long seed, CityChunk plan) {
		return plan.isStreet() && ZoneMap.zoneAtChunk(plan.chunkX(), plan.chunkZ()).city()
				&& AtlasHash.unit(AtlasHash.hash(seed, ChunkPos.pack(plan.chunkX(), plan.chunkZ()), SALT)) < CHANCE;
	}

	/** Whether this street chunk has a bicycle (if a wall can be found for it). */
	public static boolean bikeChosen(long seed, CityChunk plan) {
		return plan.isStreet() && ZoneMap.zoneAtChunk(plan.chunkX(), plan.chunkZ()).city()
				&& AtlasHash.unit(AtlasHash.hash(seed, ChunkPos.pack(plan.chunkX(), plan.chunkZ()), BIKE_SALT)) < BIKE_CHANCE;
	}

	/** Places this chunk's container and bicycle, if it has them. @return how many things were placed */
	static int place(ServerLevel level, LevelChunk chunk, Optional<CityChunk> plan) {
		if (plan.isEmpty()) {
			return 0;
		}
		return placeContainer(level, chunk, plan.get()) + placeBike(level, chunk, plan.get());
	}

	/**
	 * A bicycle leaning on a building: the street cell must have a sturdy wall beside it, and the bike stands along
	 * that wall. Why only there: bikes are a find, left where people locked them, not something lying mid-road.
	 */
	private static int placeBike(ServerLevel level, LevelChunk chunk, CityChunk p) {
		if (!bikeChosen(level.getSeed(), p)) {
			return 0;
		}
		ChunkPos cp = chunk.getPos();
		for (int i = 0; i < BIKE_TRIES; i++) {
			long h = AtlasHash.hash(level.getSeed(), cp.pack(), BIKE_SALT, i);
			BlockPos origin = new BlockPos(cp.getMinBlockX() + 1 + (int) Long.remainderUnsigned(h, 14), p.groundY() + 1,
					cp.getMinBlockZ() + 1 + (int) Long.remainderUnsigned(h >>> 20, 14));
			BlockPos spot = RelayPlacer.findSpot(level, cp, origin);
			Direction wall = spot == null ? null : ContainerPass.wallSide(level, spot);
			if (wall != null && level.getBlockState(spot.above()).canBeReplaced()) {
				return spawnBike(level, spot, wall.getClockWise().toYRot()) ? 1 : 0;
			}
		}
		return 0;
	}

	/** Stands a bicycle at {@code pos} facing {@code yaw}. Public for tests. */
	public static boolean spawnBike(ServerLevel level, BlockPos pos, float yaw) {
		Bicycle bike = Bikes.BICYCLE.create(level, EntitySpawnReason.STRUCTURE);
		if (bike == null) {
			return false;
		}
		bike.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		bike.setYHeadRot(yaw);
		bike.setYBodyRot(yaw);
		return level.addFreshEntity(bike);
	}

	private static int placeContainer(ServerLevel level, LevelChunk chunk, CityChunk p) {
		if (!chosen(level.getSeed(), p)) {
			return 0;
		}
		ChunkPos cp = chunk.getPos();
		long h = AtlasHash.hash(level.getSeed(), cp.pack(), CELL_SALT);
		BlockPos origin = new BlockPos(cp.getMinBlockX() + 2 + (int) Long.remainderUnsigned(h, 12), p.groundY() + 1,
				cp.getMinBlockZ() + 2 + (int) Long.remainderUnsigned(h >>> 20, 12));
		BlockPos spot = RelayPlacer.findSpot(level, cp, origin);
		if (spot == null || ContainerPass.nextToContainer(level, spot)) {
			return 0;
		}
		boolean dumpster = AtlasHash.unit(AtlasHash.hash(level.getSeed(), cp.pack(), KIND_SALT)) < DUMPSTER_SHARE;
		return placeContainer(level, spot, dumpster, AtlasHash.hash(h, spot.asLong())) ? 1 : 0;
	}

	/** Places a dumpster or a street crate at {@code spot} with its one-time loot. Public for tests. */
	public static boolean placeContainer(ServerLevel level, BlockPos spot, boolean dumpster, long seed) {
		Direction wall = ContainerPass.wallSide(level, spot);
		BlockState state = dumpster
				? Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)
				: Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, wall != null ? wall.getOpposite() : Direction.NORTH);
		level.setBlock(spot, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return arm(level.getBlockEntity(spot), dumpster ? DUMPSTER_TABLE : CRATE_TABLE, seed);
	}

	private static boolean arm(BlockEntity be, Identifier table, long seed) {
		if (!(be instanceof RandomizableContainer container)) {
			return false;
		}
		container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, table), seed);
		be.setAttached(AshfallSalvage.LOOT_STATE, new LootState(table, Optional.empty(), Optional.empty(), -1, 0));
		be.setChanged();
		return true;
	}
}
