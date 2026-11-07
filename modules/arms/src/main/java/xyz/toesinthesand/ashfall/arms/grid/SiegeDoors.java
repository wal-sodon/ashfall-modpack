package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

/**
 * The siege surge fails the substation's security doors open.
 * <p>
 * Every Substation Core stands in a control room behind an iron "secure door" (the landmark kit's
 * {@code secure_door}: shut to zombies, opened by players with its button). Left alone, that door made the
 * District Siege harmless: zombies path to the core, stop at the iron door, and the core never takes a hit, so
 * the core's health and the "core at 0 means back to REPAIRED" rule could never happen in play (found in the
 * Phase 3 headless playtest). Instead, while a district is under siege, every non-wooden door within
 * {@code core.door_radius} of the core is held open once a second, and closed again when the siege ends, won or
 * lost. Players then defend the doorway, which is the fight the design asks for, and the button still works the
 * moment the siege is over.
 * <p>
 * Wooden doors are left alone (zombies can already break those on Hard). The scan is a box of
 * {@code (2r+1)^2 x 5} block reads per besieged district per second, about 4,200 at the default radius 14,
 * which is far below one chunk's worth of work.
 */
public final class SiegeDoors {
	/** Door lower halves sit one block below the core (core on a slab on the floor); scan a little around that. */
	private static final int BELOW = 3, ABOVE = 1;

	private SiegeDoors() {
	}

	static void init() {
		GridEvents.DISTRICT_CHANGED.register((level, id, from, to) -> {
			if (to == DistrictState.SIEGE && from != DistrictState.SIEGE) {
				int opened = holdOpen(level, id);
				GridActions.broadcast(level, Component.translatable("message.ashfall.grid.doors_open", GridDistricts.name(id))
						.withStyle(ChatFormatting.RED));
				AshfallArms.LOGGER.info("Grid: {} siege surge, {} security doors forced open", id, opened);
			} else if (from == DistrictState.SIEGE && to != DistrictState.SIEGE) {
				int closed = setDoors(level, id, false);
				AshfallArms.LOGGER.info("Grid: {} siege over, {} security doors closed", id, closed);
			}
		});
	}

	/** Opens the district's security doors (called every siege tick). @return doors opened by this call */
	static int holdOpen(ServerLevel level, String id) {
		return setDoors(level, id, true);
	}

	/** Sets every non-wooden door near the district's core open or shut. @return doors changed */
	static int setDoors(ServerLevel level, String id, boolean open) {
		DistrictRecord r = GridState.get(level.getServer()).record(id);
		int radius = GridDistricts.settings(id).core().doorRadius();
		if (r == null || !r.hasCore() || radius <= 0) {
			return 0;
		}
		BlockPos core = BlockPos.of(r.corePos);
		if (!level.hasChunkAt(core)) {
			return 0;
		}
		int changed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -BELOW; dy <= ABOVE; dy++) {
					pos.set(core.getX() + dx, core.getY() + dy, core.getZ() + dz);
					if (!level.hasChunkAt(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof DoorBlock door && !DoorBlock.isWoodenDoor(state)
							&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && door.isOpen(state) != open) {
						door.setOpen(null, level, state, pos.immutable(), open);
						changed++;
					}
				}
			}
		}
		return changed;
	}
}
