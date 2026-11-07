package xyz.toesinthesand.ashfall.arms.mission;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import xyz.toesinthesand.ashfall.arms.story.Ending;

/**
 * The mission layer: the first-join briefing, the live objective (HUD line, {@code /ashfall mission}, the Field
 * Radio) and the way home. It sits on top of the grid, story and finale and only reads them, so it can never change
 * how the game plays, only how clearly it explains itself. Registration only; each class says what it does and why.
 */
public final class Mission {
	private Mission() {
	}

	public static void init() {
		// Touch the attachment so it registers during mod init, before any player loads.
		MissionProgress.TYPE.identifier();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(MissionSettings.Loader.ID, new MissionSettings.Loader());
		MissionTracker.init();
		// A replayed finale needs a new Uplink, so the world's endgame milestones start over with the story.
		Ending.RESET.register(server -> MissionState.get(server).reset());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> MissionCommand.register(dispatcher));
	}
}
