package xyz.toesinthesand.ashfall.director.pets;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

/**
 * Stray dogs in the residential zones (Montford and the suburbs, the zones built in the
 * {@value #RESIDENTIAL_STYLE} city style). The world is cut into cells of {@value #CELL_CHUNKS}x{@value #CELL_CHUNKS}
 * chunks; the first time a player stands in a residential cell, a few spots are tried there and one wolf named
 * "Stray Dog" is placed on the ground by a building, out of sight and off any claim. Each cell is seeded once
 * ({@link StrayDogState}). Taming is vanilla: feed it meat.
 * <p>
 * Nothing changes world generation, and strays follow {@code doMobSpawning} (off in gametests).
 */
public final class StrayDogs {
	public static final int CELL_CHUNKS = 6;
	public static final int CELL_BLOCKS = CELL_CHUNKS * 16;
	public static final int CYCLE_TICKS = 200;
	public static final int ATTEMPTS_PER_CYCLE = 4;
	/** Never closer to the player than this, so the dog doesn't appear beside them. */
	public static final int MIN_DISTANCE = 24;
	public static final String RESIDENTIAL_STYLE = "ashfall:residential";
	public static final String NAME_KEY = "entity.ashfall.stray_dog";

	private StrayDogs() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StrayDogs::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!level.getGameRules().get(GameRules.SPAWN_MOBS)) {
			return;
		}
		long tick = server.getTickCount();
		for (ServerPlayer player : level.players()) {
			if ((tick + player.getId()) % CYCLE_TICKS == 0 && !player.isSpectator() && player.isAlive()) {
				trySeed(level, player, level.getRandom());
			}
		}
	}

	/** The cell holding block column (x, z), packed like a chunk position. */
	public static long cellOf(int x, int z) {
		return ChunkPos.pack(Math.floorDiv(x >> 4, CELL_CHUNKS), Math.floorDiv(z >> 4, CELL_CHUNKS));
	}

	/** Whether the column is in a residential zone. */
	public static boolean residential(int x, int z) {
		return ZoneMap.zoneAt(x, z).cityStyle().filter(RESIDENTIAL_STYLE::equals).isPresent();
	}

	/** One try at the stray for the player's cell. @return the new dog, or null */
	public static @Nullable Wolf trySeed(ServerLevel level, ServerPlayer player, RandomSource random) {
		long cell = cellOf(player.getBlockX(), player.getBlockZ());
		StrayDogState state = StrayDogState.get(level.getServer());
		if (state.seeded(cell)) {
			return null;
		}
		int minX = ChunkPos.getX(cell) * CELL_BLOCKS;
		int minZ = ChunkPos.getZ(cell) * CELL_BLOCKS;
		for (int i = 0; i < ATTEMPTS_PER_CYCLE; i++) {
			int x = minX + random.nextInt(CELL_BLOCKS);
			int z = minZ + random.nextInt(CELL_BLOCKS);
			double dx = x + 0.5 - player.getX();
			double dz = z + 0.5 - player.getZ();
			if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) {
				continue;
			}
			BlockPos pos = spot(level, x, z);
			Wolf dog = pos == null ? null : spawn(level, pos);
			if (dog != null) {
				state.markSeeded(cell);
				return dog;
			}
		}
		return null;
	}

	/** The ground at column (x, z) if a stray may stand there, else null. Cheap checks first. */
	public static @Nullable BlockPos spot(ServerLevel level, int x, int z) {
		if (!residential(x, z) || !nearBuilding(x, z) || !LoadedChunks.ready(level, x, z)) {
			return null;
		}
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		if (!SpawnDirector.validLand(level, pos) || Claims.isClaimed(level, pos) || SpawnDirector.visibleToAnyPlayer(level, pos)) {
			return null;
		}
		return pos;
	}

	/** A building in the column's chunk or one next to it. Without city data (a flat test world) the zone decides alone. */
	static boolean nearBuilding(int x, int z) {
		BuildingSource source = AshfallSalvage.buildings();
		if (source.isEmpty()) {
			return true;
		}
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (source.chunk(chunkX + dx, chunkZ + dz).map(CityChunk::hasBuilding).orElse(false)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Places a stray at {@code pos}: a wild wolf named "Stray Dog". */
	public static @Nullable Wolf spawn(ServerLevel level, BlockPos pos) {
		Wolf dog = EntityTypes.WOLF.spawn(level, pos, EntitySpawnReason.EVENT);
		if (dog != null) {
			dog.setCustomName(Component.translatable(NAME_KEY));
		}
		return dog;
	}
}
