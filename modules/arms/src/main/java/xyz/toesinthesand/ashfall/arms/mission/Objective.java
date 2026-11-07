package xyz.toesinthesand.ashfall.arms.mission;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * What the player should do next: a step, the text for it and, when there is one, the spot to walk to.
 * <p>
 * The text is a key and arguments rather than a finished string so one objective can be drawn as a HUD line, a chat
 * checklist row and a radio line, each in the reader's language. {@code objective.ashfall.<key>} is the short line;
 * {@code objective.ashfall.<key>.how} explains how to do it (shown by {@code /ashfall mission}).
 *
 * @param step   which stage of the mission this belongs to
 * @param key    the text key, without the {@code objective.ashfall.} prefix
 * @param args   the text's arguments
 * @param target where to go, if the step has a place
 */
public record Objective(MissionStep step, String key, List<ObjectiveArg> args, Optional<MissionPos> target) {
	public static final String PREFIX = "objective.ashfall.";

	public Objective {
		args = List.copyOf(args);
	}

	public static Objective of(MissionStep step, String key, Optional<MissionPos> target, ObjectiveArg... args) {
		return new Objective(step, key, List.of(args), target);
	}

	public Component text() {
		return Component.translatable(PREFIX + key, componentArgs());
	}

	/** The how-to line for this objective. */
	public Component how() {
		return Component.translatable(PREFIX + key + ".how", componentArgs());
	}

	private Object[] componentArgs() {
		Object[] out = new Object[args.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = args.get(i).component();
		}
		return out;
	}
}
