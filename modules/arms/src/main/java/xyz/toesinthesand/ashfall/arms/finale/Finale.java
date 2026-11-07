package xyz.toesinthesand.ashfall.arms.finale;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.story.Ending;

/**
 * The Black Start (plan M9.5, endgame-design section 4 phases 1-3): the Grid Uplink and its sockets, the sealed Wreck
 * hull, the finale controller, the Heart pylons and the Choir. Registration only; each class says what it does.
 */
public final class Finale {
	private Finale() {
	}

	public static void init() {
		FinaleBlocks.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(FinaleSettings.Loader.ID, new FinaleSettings.Loader());
		FinaleController.init();
		HullSite.init();
		// A participant who was offline when the Heart went dark still gets "Asheville Relit" on their next join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			FinaleRun run = FinaleState.get(server).run();
			if (run.status == FinaleRun.Status.COMPLETE && run.participants.contains(handler.player.getUUID())) {
				FinaleEffects.awardRelit(handler.player);
			}
			// A vote cut short by a quit or restart reopens when a fighter comes back (it is not saved).
			if (run.status == FinaleRun.Status.COMPLETE) {
				Ending.resumeChoice(server, java.util.List.copyOf(run.participants), handler.player);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> FinaleCommand.register(dispatcher));
		// The replay reset (/ashfall story reset) forgets the ending; a COMPLETE run would refuse a new start forever,
		// so the world's finale goes back to IDLE (pylons, breakers and the Choir cleared). The spent Uplink stays in
		// its socket, powered down, and an empty hand on the socket starts the replay (black-start spec section 9):
		// the hand-back. The hull stays open: the districts it opened for are kept by the reset.
		Ending.RESET.register(server -> {
			FinaleInstance main = FinaleController.main(server);
			if (main.run().status != FinaleRun.Status.IDLE) {
				boolean kept = FinaleController.rearm(server.overworld(), main);
				AshfallArms.LOGGER.info("Finale: rearmed by the story reset{}", kept ? "; the Grid Uplink waits in its socket" : "");
				if (kept) {
					FinaleController.broadcast(server, Component.translatable("message.ashfall.finale.uplink_standby")
							.withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			}
		});
	}
}
