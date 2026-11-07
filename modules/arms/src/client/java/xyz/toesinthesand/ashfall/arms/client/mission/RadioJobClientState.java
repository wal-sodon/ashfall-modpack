package xyz.toesinthesand.ashfall.arms.client.mission;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import xyz.toesinthesand.ashfall.arms.mission.MissionPos;
import xyz.toesinthesand.ashfall.arms.radio.RadioJobPayload;

import java.util.Optional;

/** The client's copy of the last {@code ashfall:radio_job}: the radio job under way, if any. Cleared on disconnect. */
public final class RadioJobClientState {
	private static volatile Optional<RadioJobPayload.View> job = Optional.empty();

	private RadioJobClientState() {
	}

	public static void accept(RadioJobPayload payload) {
		job = payload.job();
	}

	public static void clear() {
		job = Optional.empty();
	}

	public static Optional<RadioJobPayload.View> job() {
		return job;
	}

	public static MissionPos target(RadioJobPayload.View view) {
		return new MissionPos(view.x(), view.z());
	}

	/** "Supplies: pharmacy, River Arts" (the HUD line adds distance and time; the map tooltip uses it as is). */
	public static MutableComponent text(RadioJobPayload.View view) {
		return Component.translatable("hud.ashfall.radio_job.text", Component.translatable("radio_job.ashfall.type." + view.type()),
				Component.translatable("radio_job.ashfall.place." + view.profile()), Component.translatable("zone.ashfall." + view.district()));
	}

	/** "Radio: Supplies: pharmacy, River Arts · 420 m NE · 14:05". */
	public static Component line(RadioJobPayload.View view, double playerX, double playerZ, boolean inOverworld, long gameTime) {
		long seconds = Math.max(0, view.deadline() - gameTime) / 20;
		return Component.translatable("hud.ashfall.radio_job", text(view).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.AQUA)
				.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append((inOverworld ? ObjectiveHud.target(target(view), playerX, playerZ) : Component.translatable("hud.ashfall.objective.elsewhere"))
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" · " + String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.GRAY));
	}
}
