package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;

import java.util.Optional;

/**
 * What happens when a district is reclaimed (endgame-design section 3, "Reclaimed means"), and what is undone
 * when an admin sets it back:
 * <ul>
 * <li>The substation compound becomes a safe zone, through the Director's haven regions (and an OPAC server claim
 * when OPAC is present). Only the compound: the rest of the district keeps spawning, at the reduced darkness
 * weight {@link GridPower} applies, because a lit district must never be a free safe zone.</li>
 * <li>A per-player reward cache at the core: a chest with an {@code ashfall_cache:} table, which Lootr turns into
 * a per-player chest.</li>
 * <li>District extras from the settings: Mission unseals Patient Zero's deep ward; River Arts starts the city
 * pump (read live by {@link GridPower#cleanWaterAt}).</li>
 * <li>The district's loaded chunks relight now; the rest relight lazily as they load ({@link GridRelight}).</li>
 * </ul>
 */
public final class GridReclaim {
	private GridReclaim() {
	}

	static void init() {
		GridEvents.DISTRICT_CHANGED.register((level, id, from, to) -> {
			if (to == DistrictState.RECLAIMED) {
				onReclaimed(level, id);
			} else if (from == DistrictState.RECLAIMED) {
				onLost(level, id);
			}
			if (to.dark()) {
				int darkened = GridRelight.darkenLoaded(level, id);
				AshfallArms.LOGGER.info("Grid: {} {}, {} grid lamps went dark in loaded chunks", id, to.id(), darkened);
			}
		});
	}

	/** The safe-zone id of a district's substation compound. */
	public static String compoundZoneId(String district) {
		return "ashfall:grid_" + district;
	}

	static void onReclaimed(ServerLevel level, String id) {
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id);
		GridSettings settings = GridDistricts.settings(id);
		// Back on the grid after a held Breach or a re-repair: the effects return, the first-reclaim fanfare does not.
		boolean restoring = r != null && r.restoring;
		if (r != null && r.hasCore()) {
			BlockPos core = BlockPos.of(r.corePos);
			SafeZone zone = new SafeZone(Component.translatable("safezone.ashfall.grid_compound", GridDistricts.name(id)).getString(),
					Level.OVERWORLD, ChunkArea.around(core.getX(), core.getZ(), settings.reclaimed().compoundRadiusChunks()),
					Optional.empty(), false, Optional.of("ashfall:substation_" + id));
			HavenState.get(level.getServer()).putCustomZone(compoundZoneId(id), zone);
			Havens.refresh(level.getServer());
			if (!r.rewarded && placeRewardCache(level, core, settings.rewardTable(id)) != null) {
				grid.update(level, id, rec -> rec.rewarded = true);
			}
			level.playSound(null, core, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0f, 0.8f);
		}
		int relit = GridRelight.relightLoaded(level, id);
		if (restoring) {
			AshfallArms.LOGGER.info("Grid: {} back on the grid (grid {}%, {} lights relit in loaded chunks)", id, grid.gridPercent(), relit);
			return;
		}
		settings.rewards(id).unsealLair().ifPresent(lair -> {
			int opened = BossLairs.unseal(level, lair);
			AshfallArms.LOGGER.info("Grid: {} reclaimed, unsealed {} ({} doors)", id, lair, opened);
		});
		GridActions.broadcast(level, Component.translatable("message.ashfall.grid.reclaimed", GridDistricts.name(id),
				grid.gridPercent()).withStyle(ChatFormatting.GREEN));
		if (settings.rewards(id).cityPump()) {
			GridActions.broadcast(level, Component.translatable("message.ashfall.grid.city_pump").withStyle(ChatFormatting.AQUA));
		}
		AshfallArms.LOGGER.info("Grid: {} reclaimed (grid {}%, {} lights relit in loaded chunks)", id, grid.gridPercent(), relit);
	}

	/**
	 * Set back from RECLAIMED by an admin, or under attack in a Breach: the compound stops being a safe zone (the horde
	 * has to be able to reach the core, and a district under attack is no refuge).
	 */
	static void onLost(ServerLevel level, String id) {
		if (HavenState.get(level.getServer()).removeCustomZone(compoundZoneId(id))) {
			Havens.refresh(level.getServer());
		}
	}

	/**
	 * Places the reward chest on the control-room floor beside the core: the first free spot (air with a solid
	 * floor) in the ring around it, one block below the core (the core stands on a slab on its plinth).
	 *
	 * @return where the chest went, or null if there was no room
	 */
	public static BlockPos placeRewardCache(ServerLevel level, BlockPos core, Identifier table) {
		for (int dy : new int[]{-1, 0, -2}) {
			for (int ring = 1; ring <= 3; ring++) {
				for (int dx = -ring; dx <= ring; dx++) {
					for (int dz = -ring; dz <= ring; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
							continue;
						}
						BlockPos pos = core.offset(dx, dy, dz);
						if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolidRender()) {
							continue;
						}
						Direction facing = Math.abs(dx) >= Math.abs(dz)
								? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
						level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
						if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
							chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, table), level.getRandom().nextLong());
							chest.setChanged();
						}
						return pos;
					}
				}
			}
		}
		AshfallArms.LOGGER.warn("Grid: no room for the reward cache near the core at {}", core);
		return null;
	}
}
