package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.director.horde.BreachHook;
import xyz.toesinthesand.ashfall.director.horde.HordeHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid's side of the Director's horde scheduler (plan M8): the Breach target list and start, and the Grid tier
 * for horde nights. The Director defines the hooks ({@link BreachHook}, {@link HordeHooks#setGridTier}) because it
 * must not depend on arms; arms registers them here.
 * <p>
 * A Breach is a SIEGE flagged {@link DistrictRecord#breach} on a reclaimed district, so it reuses the whole siege
 * (waves, routes into the building, core health, the bossbar) and only differs in its stakes, which
 * {@link DistrictLoop} owns: a destroyed core breaks the district (BROKEN), a core still standing at dawn keeps it.
 */
public final class GridBreach implements BreachHook {
	/** The running server, for the Grid tier supplier (the Director's hook takes no arguments). */
	private static volatile MinecraftServer server;

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		HordeHooks.setBreach(new GridBreach());
		HordeHooks.setGridTier(GridBreach::gridTier);
	}

	/**
	 * The Grid tier horde nights use: {@code grid_tier_base} plus the districts reclaimed so far (siege.json), so
	 * every relit district makes horde nights harder, like it does sieges. 0 with no server running.
	 */
	public static int gridTier() {
		MinecraftServer s = server;
		if (s == null) {
			return 0;
		}
		SiegeSettings siege = SiegeSettings.get();
		return siege.gridTier(1, GridState.get(s).reclaimedCount());
	}

	/** Reclaimed districts whose core position is known; gametest districts are never picked by the schedule. */
	@Override
	public boolean busy(MinecraftServer server) {
		return GridState.get(server).snapshot().values().stream()
				.anyMatch(state -> state == DistrictState.ENERGIZED || state == DistrictState.SIEGE);
	}

	@Override
	public List<String> targets(MinecraftServer server) {
		GridState grid = GridState.get(server);
		List<String> out = new ArrayList<>();
		for (String id : grid.districts()) {
			DistrictRecord r = grid.record(id);
			if (r != null && r.state == DistrictState.RECLAIMED && r.hasCore() && !GridDistricts.isManual(id)) {
				out.add(id);
			}
		}
		return out;
	}

	@Override
	public Component name(String id) {
		return GridDistricts.name(id);
	}

	/** Starts the Breach now: the district leaves RECLAIMED for a SIEGE that lasts until dawn. Public for gametests. */
	@Override
	public boolean begin(ServerLevel level, String id) {
		LoopRules rules = GridDistricts.settings(id).rules();
		long clock = level.getOverworldClockTime();
		boolean[] started = new boolean[1];
		GridState grid = GridState.get(level.getServer());
		if (grid.state(id) != DistrictState.RECLAIMED) {
			return false;
		}
		int coreHp = GridSiege.coreHp(level, id);
		grid.update(level, id, r -> started[0] = DistrictLoop.startBreach(r, rules, clock, coreHp));
		if (started[0]) {
			AshfallArms.LOGGER.info("Grid: Breach at {} (tier {})", id, GridSiege.tier(level, id));
		}
		return started[0];
	}
}
