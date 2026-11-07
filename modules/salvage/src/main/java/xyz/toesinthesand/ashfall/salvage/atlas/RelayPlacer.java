package xyz.toesinthesand.ashfall.salvage.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.Optional;

/**
 * Puts a Power Relay at each relay candidate the atlas recorded.
 * <p>
 * In a Lost Cities world the candidates are the district key relays and the ambient street relays of
 * {@link RelayPlan}, known before generation; on an Arnis map they were rolled per building from the building
 * profiles. Either way placement is the same.
 * <p>
 * The block belongs to the arms module, which depends on salvage and not the other way round, so it is
 * looked up by id: without arms installed nothing is placed and the candidates stay pending until it is.
 * <p>
 * A candidate may be recorded before or after its chunk is processed (key relays at server start, Arnis
 * candidates from the chunk holding the building's corner), so placement runs both when a chunk is processed
 * (for every pending candidate in any loaded, already-processed chunk, plus the chunk being processed) and on a
 * periodic sweep from {@link AtlasProcessor}, which catches candidates whose chunk was processed before they were
 * recorded. Each candidate is handled once, tracked in {@link AtlasData}.
 */
public final class RelayPlacer {
	public static final Identifier POWER_RELAY = Identifier.fromNamespaceAndPath(AshfallSalvage.NAMESPACE, "power_relay");
	/** How far from the recorded cell to look for room (furniture, a container or a street lamp may sit on it). */
	private static final int SEARCH_RADIUS = 2;
	/** Heights tried around the recorded cell: streets have kerbs and rubble, so the surface is not always flat. */
	private static final int[] DY = {0, 1, -1, 2, -2};
	private static volatile Block blockOverride;

	private RelayPlacer() {
	}

	/**
	 * Places every pending relay whose chunk is loaded and either already processed or is {@code processing}
	 * (null on a sweep). Never loads chunks. @return relays placed
	 */
	public static int placeLoaded(ServerLevel level, LevelChunk processing) {
		Block override = blockOverride;
		Optional<Block> relay = override != null ? Optional.of(override) : BuiltInRegistries.BLOCK.getOptional(POWER_RELAY);
		if (relay.isEmpty()) {
			return 0;
		}
		AtlasData data = AtlasData.get(level.getServer());
		int placed = 0;
		for (AtlasData.RelayCandidate candidate : data.pendingRelays()) {
			if (!candidate.dimension().equals(level.dimension())) {
				continue;
			}
			BlockPos pos = candidate.pos();
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
			if (chunk == null || (chunk != processing && chunk.getAttachedOrElse(AtlasProcessor.VERSION, 0) < AtlasProcessor.RELAY_VERSION)) {
				continue; // its chunk's own processing will place it
			}
			BlockPos spot = findSpot(level, chunk.getPos(), pos);
			if (spot != null) {
				level.setBlock(spot, relay.get().defaultBlockState(), Block.UPDATE_ALL);
				placed++;
			} else {
				AshfallSalvage.LOGGER.debug("No room for Power Relay {} near {}", candidate.building(), pos);
			}
			data.markRelayPlaced(candidate.building()); // a full room stays full: don't search it forever
		}
		return placed;
	}

	/**
	 * The recorded cell if it has room, else the nearest cell with room within two blocks on the same level, then
	 * one and two blocks up or down; always in the same chunk.
	 */
	static BlockPos findSpot(ServerLevel level, ChunkPos chunk, BlockPos origin) {
		for (int dy : DY) {
			for (int r = 0; r <= SEARCH_RADIUS; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue; // ring r only
						}
						BlockPos pos = origin.offset(dx, dy, dz);
						if (pos.getX() >> 4 == chunk.x() && pos.getZ() >> 4 == chunk.z() && hasRoom(level, pos)) {
							return pos;
						}
					}
				}
			}
		}
		return null;
	}

	/** Test hook: places this block instead of the arms Power Relay (null restores the registry lookup). */
	public static void setBlockForTesting(Block block) {
		blockOverride = block;
	}

	private static boolean hasRoom(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockPos below = pos.below();
		return state.canBeReplaced() && state.getFluidState().isEmpty()
				&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}
}
