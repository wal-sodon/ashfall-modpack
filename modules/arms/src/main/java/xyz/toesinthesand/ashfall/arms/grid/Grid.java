package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

/**
 * Grid I (plan M4.5): the Substation Cores, the district loop, District Sieges and what a Reclaimed district
 * changes. Registration only; each part's class says what it does and why.
 */
public final class Grid {
	private Grid() {
	}

	public static void init() {
		GridBlocks.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(GridSettings.Loader.ID, new GridSettings.Loader());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SiegeSettings.Loader.ID, new SiegeSettings.Loader());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(DecaySettings.Loader.ID, new DecaySettings.Loader());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> GridDistricts.invalidate());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> GridDistricts.invalidate());
		// Also on stop: the cached building source captures the stopped world's ServerLevel, which would otherwise stay
		// reachable after "Save and Quit to Title" until another world starts.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GridDistricts.invalidate());
		GridNetworking.init();
		GridController.init();
		GridReclaim.init();
		GridDecay.init();
		SiegeDoors.init();
		GridRelight.init();
		GridPower.init();
		SiegeSleep.init();
		CoreConversion.init();
		GridBreach.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> GridCommand.register(dispatcher));
	}
}
