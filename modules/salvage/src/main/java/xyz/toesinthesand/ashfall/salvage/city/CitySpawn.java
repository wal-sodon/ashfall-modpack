package xyz.toesinthesand.ashfall.salvage.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.Optional;
import java.util.Set;

/**
 * Puts the new-world spawn inside the Montford safehouse (plan section 1.5), or on a Montford street when
 * the safehouse is not there (worlds whose predefined cities were removed, test dimensions).
 * <p>
 * We place it ourselves instead of using the profile's {@code spawnCity}/{@code forceSpawnBuildings}:
 * Lost Cities' search always spirals out from (0, 0), Montford is about 50 chunks away, and once the
 * attempt budget runs out Lost Cities throws and world creation fails. Our zone layout already knows
 * where Montford is, so we search outward from its centre with the Lost Cities chunk API, which answers
 * for chunks that are not generated yet.
 * <p>
 * The safehouse is a Lost Cities predefined building at a fixed chunk near Montford's centre; we find it
 * by asking Lost Cities for the building id of the chunks around the centre, nearest first. Runs from
 * {@code MinecraftServer.setInitialSpawn}, which only happens when a world is created, so existing worlds
 * keep their spawn.
 */
public final class CitySpawn {
	public static final String START_ZONE = "montford";
	/** How far from the Montford centre to look for a street, in chunks. */
	private static final int SEARCH_RADIUS_CHUNKS = 16;
	/** How far from the Montford centre the safehouse may sit, in chunks (it is predefined at the centre). */
	private static final int SAFEHOUSE_SEARCH_CHUNKS = 3;

	private CitySpawn() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
	}

	/** Called at the end of vanilla's initial spawn selection for each new level. */
	public static void onInitialSpawn(ServerLevel level, ServerLevelData levelData) {
		if (level.dimension() != Level.OVERWORLD || !LostCityContext.isAshfall(level)) {
			return;
		}
		findSpawn(level).ifPresent(pos -> {
			LevelData.RespawnData data = LevelData.RespawnData.of(Level.OVERWORLD, pos, 0.0f, 0.0f);
			levelData.setSpawn(data);
			level.setRespawnData(data);
			Data saved = Data.get(level);
			saved.pos = Optional.of(pos);
			saved.pending = true;
			saved.setDirty();
			if (insideSafehouse(level, pos)) {
				// Vanilla scatters (re)spawns over a radius on the surface heightmap, which would put players
				// on the safehouse roof or in the next street. The spawn point is indoors, so use it as is.
				level.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, level.getServer());
			}
			AshfallSalvage.LOGGER.info("Ashfall spawn placed in {} at {}", START_ZONE, pos.toShortString());
		});
	}

	/**
	 * The Montford spawn: the safehouse's front hall if Lost Cities placed the safehouse near the zone
	 * centre, else the street chunk nearest the centre (no building, no highway), standing on its surface.
	 * Empty if Lost Cities is inactive or zone data is missing.
	 */
	public static Optional<BlockPos> findSpawn(ServerLevel level) {
		Optional<Zone> zone = ZoneMap.current().byId(START_ZONE);
		if (zone.isEmpty() || !(zone.get().shape() instanceof ZoneShape.Disc disc)) {
			return Optional.empty();
		}
		int ccx = disc.x() >> 4, ccz = disc.z() >> 4;
		Optional<BlockPos> safehouse = findSafehouse(level, ccx, ccz);
		if (safehouse.isPresent()) {
			return safehouse;
		}
		for (int r = 0; r <= SEARCH_RADIUS_CHUNKS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int cx = ccx + dx, cz = ccz + dz;
					if (!START_ZONE.equals(ZoneMap.zoneAtChunk(cx, cz).id())) {
						continue;
					}
					Optional<CityChunk> chunk = LostCityContext.chunk(level, cx, cz);
					if (chunk.isEmpty()) {
						return Optional.empty();
					}
					if (chunk.get().isStreet()) {
						return Optional.of(surface(level, (cx << 4) + 8, (cz << 4) + 8));
					}
				}
			}
		}
		return Optional.of(surface(level, disc.x(), disc.z()));
	}

	/** The safehouse's spawn point if a chunk within {@link #SAFEHOUSE_SEARCH_CHUNKS} of the centre holds it. */
	public static Optional<BlockPos> findSafehouse(ServerLevel level, int ccx, int ccz) {
		for (int r = 0; r <= SAFEHOUSE_SEARCH_CHUNKS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Optional<CityChunk> chunk = LostCityContext.chunk(level, ccx + dx, ccz + dz);
					if (chunk.isEmpty()) {
						return Optional.empty();
					}
					if (Landmarks.SAFEHOUSE.equals(chunk.get().buildingId())) {
						int x = ((ccx + dx) << 4) + Landmarks.SAFEHOUSE_SPAWN_X;
						int z = ((ccz + dz) << 4) + Landmarks.SAFEHOUSE_SPAWN_Z;
						level.getChunk(x >> 4, z >> 4);
						return Optional.of(new BlockPos(x, chunk.get().groundY() + 1, z));
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean insideSafehouse(ServerLevel level, BlockPos pos) {
		return LostCityContext.chunk(level, pos.getX() >> 4, pos.getZ() >> 4)
				.map(c -> Landmarks.SAFEHOUSE.equals(c.buildingId())).orElse(false);
	}

	/** First free block above the generated surface; generates the chunk if needed. */
	private static BlockPos surface(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/**
	 * Re-applies the spawn for the first player to join and moves them there. Lost Cities does the same
	 * after its own spawn search because on a new singleplayer world the first player can be placed before
	 * the saved spawn is read back, and vanilla may scatter them onto the roof; doing it once (then clearing {@code pending}) means a later
	 * {@code /setworldspawn} is never undone.
	 */
	private static void onJoin(ServerPlayer player) {
		ServerLevel level = player.level().getServer().overworld();
		Data data = Data.get(level);
		if (!data.pending || data.pos.isEmpty()) {
			return;
		}
		BlockPos pos = data.pos.get();
		data.pending = false;
		data.setDirty();
		if (!level.getRespawnData().pos().equals(pos)) {
			LevelData.RespawnData respawn = LevelData.RespawnData.of(Level.OVERWORLD, pos, 0.0f, 0.0f);
			level.setRespawnData(respawn);
			if (level.getLevelData() instanceof ServerLevelData sld) {
				sld.setSpawn(respawn);
			}
		}
		if (player.level() == level && player.blockPosition().distSqr(pos) > 2 * 2) {
			player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		}
	}

	/** {@code data/ashfall/city_spawn.dat}: where we put the spawn, and whether the first join still has to confirm it. */
	static final class Data extends SavedData {
		static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.optionalFieldOf("pos").forGetter(d -> d.pos),
				Codec.BOOL.optionalFieldOf("pending", false).forGetter(d -> d.pending)
		).apply(i, Data::new));
		static final SavedDataType<Data> TYPE = new SavedDataType<>(AshfallSalvage.id("city_spawn"), Data::new, CODEC, null);

		Optional<BlockPos> pos;
		boolean pending;

		Data() {
			this(Optional.empty(), false);
		}

		Data(Optional<BlockPos> pos, boolean pending) {
			this.pos = pos;
			this.pending = pending;
		}

		static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(TYPE);
		}
	}
}
