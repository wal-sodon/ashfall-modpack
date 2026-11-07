package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/**
 * Remembers which district a core serves and tells {@link GridState} where it stands. The district is read from
 * the ZoneMap at the core's position the first time (the landmark assets carry no per-district data, see
 * {@code tools/landmarks/industrial.py}) and then saved, so a later zone edit never moves a core to another
 * district mid-run. Once a second it re-registers its position and shows the loop on its blockstate; the loop
 * itself runs in {@link GridController}, which works even while this block's chunk is unloaded.
 */
public class SubstationCoreBlockEntity extends BlockEntity {
	private static final int UPDATE_TICKS = 20;
	private String district = "";

	public SubstationCoreBlockEntity(BlockPos pos, BlockState state) {
		super(GridBlocks.SUBSTATION_CORE_ENTITY, pos, state);
	}

	/** The district this core serves, if it is inside one. */
	public Optional<String> district() {
		return district.isEmpty() ? Optional.empty() : Optional.of(district);
	}

	/** Resolves and saves the district on first use. */
	public Optional<String> resolveDistrict(ServerLevel level) {
		if (district.isEmpty()) {
			GridDistricts.districtAt(level, worldPosition).ifPresent(id -> {
				district = id;
				setChanged();
			});
		}
		return district();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SubstationCoreBlockEntity core) {
		if (level instanceof ServerLevel server && (server.getGameTime() + pos.hashCode()) % UPDATE_TICKS == 0) {
			core.update(server, pos, state);
		}
	}

	/** Registers this core for its district and syncs the blockstate with the loop. Public for gametests. */
	public void update(ServerLevel level, BlockPos pos, BlockState state) {
		Optional<String> id = resolveDistrict(level);
		if (id.isEmpty()) {
			return;
		}
		GridState grid = GridState.get(level.getServer());
		DistrictRecord r = grid.record(id.get());
		if (r == null || r.corePos != pos.asLong()) {
			boolean claim = r == null || !r.hasCore() || !isCoreAt(level, BlockPos.of(r.corePos));
			if (claim) {
				grid.update(level, id.get(), rec -> rec.corePos = pos.asLong());
				r = grid.record(id.get());
			}
		}
		DistrictState st = r == null ? DistrictState.DARK : r.state;
		boolean energizing = r != null && r.energizing(GridDistricts.settings(id.get()).rules());
		int power = SubstationCoreBlock.power(st, energizing);
		if (state.getValue(SubstationCoreBlock.POWER) != power) {
			level.setBlock(pos, state.setValue(SubstationCoreBlock.POWER, power), Block.UPDATE_ALL);
		}
	}

	/** True if a Substation Core stands at {@code pos}, or its chunk is not loaded (an unloaded core is not lost). */
	private static boolean isCoreAt(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return true;
		}
		return level.getBlockState(pos).is(GridBlocks.SUBSTATION_CORE);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		district = input.getStringOr("district", "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("district", district);
	}
}
