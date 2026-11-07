package xyz.toesinthesand.ashfall.arms.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.story.StoryEffects;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.spawn.SpawnDirector;

import java.util.Optional;

/**
 * The grid's effects on other systems while a district is reclaimed, each a cheap per-position check:
 * <ul>
 * <li>Field Chargers and Power Relays run without cells ({@code ChargerBlockEntity} asks {@link #powered}); after an
 * ending whose profile frees the relays (Sever), every Power Relay does, wherever it stands.</li>
 * <li>Darkness spawning in the district is multiplied by {@code reclaimed.darkness_scale} (0.2: "drops by 80%")
 * through the Director's darkness modifier, not a claim, so the district still spawns, only less.</li>
 * <li>The city pump: while a {@code city_pump} district (River Arts) is reclaimed, bottles filled inside any
 * reclaimed district come up clean (the Director's dirty-water rule asks {@link #cleanWaterAt}).</li>
 * </ul>
 */
public final class GridPower {
	private GridPower() {
	}

	static void init() {
		SpawnDirector.addDarknessModifier(GridPower::darknessScale);
		Hydration.setCleanWaterRule(GridPower::cleanWaterAt);
	}

	/** The reclaimed district at {@code pos}, if any. */
	public static Optional<String> reclaimedDistrictAt(ServerLevel level, BlockPos pos) {
		Optional<String> id = GridDistricts.districtAt(level, pos);
		return id.filter(d -> GridState.get(level.getServer()).isReclaimed(d));
	}

	/**
	 * Whether the grid powers a charger at {@code pos}: it stands in a reclaimed district, or it is a Power Relay and
	 * the world's ending makes every relay a free Grid Point (Sever, {@link StoryEffects#freeRelays}).
	 */
	public static boolean powered(ServerLevel level, BlockPos pos) {
		if (reclaimedDistrictAt(level, pos).isPresent()) {
			return true;
		}
		return StoryEffects.freeRelays(level.getServer()) && level.getBlockState(pos).is(ArmsBlocks.POWER_RELAY);
	}

	/** The darkness multiplier at {@code pos}: the reclaimed scale inside a reclaimed district, else 1. */
	public static float darknessScale(ServerLevel level, BlockPos pos) {
		return reclaimedDistrictAt(level, pos).map(d -> GridDistricts.settings(d).reclaimed().darknessScale()).orElse(1f);
	}

	/** Whether the city pump runs: some district with {@code city_pump} is reclaimed. */
	public static boolean pumpRunning(ServerLevel level) {
		GridState grid = GridState.get(level.getServer());
		for (String id : grid.districts()) {
			if (grid.isReclaimed(id) && GridDistricts.settings(id).rewards(id).cityPump()) {
				return true;
			}
		}
		return false;
	}

	/** Clean water from open water at {@code pos}: the pump runs and {@code pos} is in a reclaimed district. */
	public static boolean cleanWaterAt(ServerLevel level, BlockPos pos) {
		return powered(level, pos) && pumpRunning(level);
	}
}
