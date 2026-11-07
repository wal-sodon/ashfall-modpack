package xyz.toesinthesand.ashfall.arms.client.grid;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xyz.toesinthesand.ashfall.arms.story.RadioProgress;

import java.util.List;

/**
 * The Grid HUD: "Grid NN%" over one pip per district, tucked left of the hotbar (and clear of the offhand slot).
 * The right side of the hotbar already holds thirst, the infection germ and the gun readout, so the left side
 * keeps the bottom of the screen balanced without stacking anything on the status bars.
 * <p>
 * It only appears once the player has found the Field Radio (the synced {@link RadioProgress#radio} flag, set
 * from the "Relight" root advancement): before that the grid is not part of the player's world yet.
 */
public final class GridHud {
	/** Gap between the hotbar's left edge and the readout, wide enough for the offhand slot. */
	static final int LEFT_OF_HOTBAR = 34;
	static final int PIP = 5, PIP_GAP = 2;

	private GridHud() {
	}

	public static boolean shown(Minecraft mc) {
		return mc.player != null && mc.level != null && !mc.player.isSpectator() && RadioProgress.get(mc.player).radio();
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!shown(mc)) {
			return;
		}
		Font font = mc.font;
		Component label = Component.translatable("hud.ashfall.grid", GridClientState.percent());
		List<String> districts = GridClientState.districts();
		int pipsWidth = districts.size() * (PIP + PIP_GAP) - PIP_GAP;
		int right = graphics.guiWidth() / 2 - 91 - LEFT_OF_HOTBAR;
		int bottom = graphics.guiHeight() - 3;
		int pipY = bottom - PIP;
		int textY = pipY - 2 - font.lineHeight;

		graphics.text(font, label, right - font.width(label), textY, 0xFFE8E2D0, true);
		long now = mc.level.getGameTime();
		int x = right - pipsWidth;
		for (String district : districts) {
			graphics.fill(x - 1, pipY - 1, x + PIP + 1, pipY + PIP + 1, 0xC0000000);
			graphics.fill(x, pipY, x + PIP, pipY + PIP, GridColours.pip(GridClientState.state(district), now));
			x += PIP + PIP_GAP;
		}
	}
}
