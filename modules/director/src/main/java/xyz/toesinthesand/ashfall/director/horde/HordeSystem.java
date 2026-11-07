package xyz.toesinthesand.ashfall.director.horde;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import xyz.toesinthesand.ashfall.director.pathing.SiegeRoutes;

/**
 * Plan M8: our own horde nights (replacing Undead Nights), dynamic hordes, Breach scheduling and siege routes.
 * Registration only; each part's class says what it does and why.
 */
public final class HordeSystem {
	private HordeSystem() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(HordeSchedule.Loader.ID, new HordeSchedule.Loader());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(HordeSettings.Loader.ID, new HordeSettings.Loader());
		Hordes.init();
		HordeNights.init();
		HordeLoot.init();
		SiegeRoutes.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> HordeCommand.register(dispatcher));
	}
}
