package xyz.toesinthesand.ashfall.arms.client.gun;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import xyz.toesinthesand.ashfall.arms.gun.ScopeMath;

/**
 * The blaster scope overlay: a black vignette with a round sight and a cyan reticle
 * ({@code textures/misc/blaster_scope.png}, drawn by {@code tools/textures/hud_scope.py}), opened like the
 * spyglass's as the zoom eases in, with the screen outside it blacked out and the magnification under the
 * sight. Drawn just above the vanilla vignette and below the hotbar, so the blaster's heat and charge HUD
 * stays readable on top.
 */
public final class ScopeOverlay {
	public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("ashfall", "blaster_scope");
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("ashfall", "textures/misc/blaster_scope.png");
	private static final int BLACK = 0xFF000000;
	private static final int LABEL_COLOR = 0xFF5AE6FF;

	private ScopeOverlay() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!ScopeClient.scoped() || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float scale = ScopeMath.overlayScale(ScopeClient.magnification(), ScopeClient.target());
		int size = Mth.floor(Math.min(width, height) * scale);
		int left = (width - size) / 2;
		int top = (height - size) / 2;
		int right = left + size;
		int bottom = top + size;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0f, 0f, size, size, size, size);
		graphics.fill(0, bottom, width, height, BLACK);
		graphics.fill(0, 0, width, top, BLACK);
		graphics.fill(0, top, left, bottom, BLACK);
		graphics.fill(right, top, width, bottom, BLACK);

		ScopeMath.Levels levels = ScopeClient.active();
		if (levels != null) {
			String label = ScopeMath.label(ScopeClient.target());
			graphics.centeredText(mc.font, label, width / 2, height / 2 + size / 4, LABEL_COLOR);
		}
	}
}
