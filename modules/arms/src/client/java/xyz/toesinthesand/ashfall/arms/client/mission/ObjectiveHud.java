package xyz.toesinthesand.ashfall.arms.client.mission;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.mission.MissionPos;
import xyz.toesinthesand.ashfall.arms.radio.RadioJobPayload;
import xyz.toesinthesand.ashfall.arms.mission.Objective;
import xyz.toesinthesand.ashfall.arms.story.Compass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The objective line: "Objective: River Arts: socket Grid Couplers 2/4 · 640 m W", right-aligned to the left of the
 * hotbar, just above the Grid HUD and clear of the offhand slot.
 * <p>
 * It shares the Grid HUD's corner so everything about the mission reads in one place, and keeps off the centre of
 * the screen, where titles, the action bar and the item name already compete. The space left of the hotbar is narrow
 * at large GUI scales, so the line wraps upward (at most {@link #MAX_LINES} lines) instead of running under the
 * hotbar. Distance and heading are worked out every frame from the synced target, so they follow the player without
 * any packets. Hidden with F1, in spectator mode, when {@code ashfall:objective} is off, and by the toggle key.
 */
public final class ObjectiveHud {
	static final int MAX_LINES = 4;
	/** Gap between the line's right edge and the hotbar. */
	static final int HOTBAR_GAP = 4;
	/** Bottom of the block: above the offhand slot (23 px) and the Grid HUD beside it. */
	static final int BOTTOM_OFFSET = 26;

	private ObjectiveHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.player.isSpectator() || !MissionClient.visible()) {
			return;
		}
		Optional<Objective> objective = MissionClientState.objective();
		Optional<RadioJobPayload.View> job = RadioJobClientState.job();
		if (objective.isEmpty() && job.isEmpty()) {
			return;
		}
		Font font = mc.font;
		boolean overworld = mc.level.dimension() == Level.OVERWORLD;
		int right = graphics.guiWidth() / 2 - 91 - HOTBAR_GAP;
		int maxWidth = Math.max(60, right - 4);
		List<FormattedCharSequence> lines = new ArrayList<>();
		objective.ifPresent(o -> lines.addAll(font.split(line(o, mc.player.getX(), mc.player.getZ(), overworld), maxWidth)));
		// The radio job sits under the objective: a side errand, so the main thread stays on top.
		job.ifPresent(j -> lines.addAll(font.split(RadioJobClientState.line(j, mc.player.getX(), mc.player.getZ(), overworld,
				mc.level.getGameTime()), maxWidth)));
		if (lines.size() > MAX_LINES + 2) {
			lines.subList(MAX_LINES + 2, lines.size()).clear();
		}
		int lineHeight = font.lineHeight + 1;
		int top = graphics.guiHeight() - BOTTOM_OFFSET - lines.size() * lineHeight;
		int widest = 0;
		for (FormattedCharSequence l : lines) {
			widest = Math.max(widest, font.width(l));
		}
		graphics.fill(right - widest - 3, top - 2, right + 2, top + lines.size() * lineHeight, 0x70000000);
		int y = top;
		for (FormattedCharSequence l : lines) {
			graphics.text(font, l, right - font.width(l), y, 0xFFE8E2D0, true);
			y += lineHeight;
		}
	}

	/**
	 * "Objective: text · 640 m W" (the target part only when the objective has a place). Every target is an overworld
	 * spot and the payload carries no dimension, so in the Nether or the End a distance from the player's own x/z would
	 * be wrong (the Nether is 8:1 with its own origin) and could send someone the wrong way home: it reads "in the
	 * overworld" instead.
	 */
	public static Component line(Objective objective, double playerX, double playerZ, boolean inOverworld) {
		MutableComponent line = Component.translatable("hud.ashfall.objective", objective.text().copy().withStyle(ChatFormatting.WHITE))
				.withStyle(ChatFormatting.GOLD);
		objective.target().ifPresent(t -> line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append((inOverworld ? target(t, playerX, playerZ) : Component.translatable("hud.ashfall.objective.elsewhere"))
						.withStyle(ChatFormatting.GRAY)));
		return line;
	}

	/** "640 m W", or "here" when standing on it. Blocks are metres, and the radio rounds to tens the same way. */
	public static MutableComponent target(MissionPos t, double playerX, double playerZ) {
		int distance = Compass.roughDistance(playerX, playerZ, t.x(), t.z());
		if (distance < 10) {
			return Component.translatable("hud.ashfall.objective.here");
		}
		return Component.translatable("hud.ashfall.objective.target", distance,
				Component.translatable("compass.ashfall.short." + Compass.point(playerX, playerZ, t.x(), t.z())));
	}
}
