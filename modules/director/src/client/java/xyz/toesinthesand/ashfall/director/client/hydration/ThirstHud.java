package xyz.toesinthesand.ashfall.director.client.hydration;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.hydration.ThirstData;

/**
 * The droplet row, one status bar directly above hunger on the right of the hotbar. It is a Fabric
 * HUD element placed right after the vanilla food bar plus a right-side status bar height, so Fabric
 * stacks it like a vanilla bar: the air bubbles (and any other mod's right bars) move up a row instead
 * of overlapping it, with no mixin into the HUD.
 * <p>
 * Drawn like the food bar: an empty droplet behind every slot, then full or half droplets on top,
 * jittering when saturation is gone. Shown only for players who can take damage (survival and
 * adventure), and only for the local player, the one whose thirst the server syncs.
 */
public final class ThirstHud {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "thirst");
	private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("ashfall", "hud/thirst_empty");
	private static final Identifier HALF = Identifier.fromNamespaceAndPath("ashfall", "hud/thirst_half");
	private static final Identifier FULL = Identifier.fromNamespaceAndPath("ashfall", "hud/thirst_full");
	private static final int ROW_HEIGHT = 10;
	private static final int ICON = 9;

	private static final RandomSource JITTER = RandomSource.create();

	private ThirstHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, ID, ThirstHud::extract);
		HudStatusBarHeightRegistry.addRight(ID, player -> shown(player) ? ROW_HEIGHT : 0);
	}

	/**
	 * Same rule for drawing and for the reserved height, so the air bar only moves up when the droplets
	 * are really there. Requiring the camera to be the local player also keeps
	 * {@code HudStatusBarHeightRegistry.getHeight}, which needs a camera player, safe to call. Public so
	 * HUD pieces that sit beside the droplets (the infection stage) follow exactly the same rule.
	 */
	public static boolean shown(Player player) {
		Minecraft mc = Minecraft.getInstance();
		return player != null && player == mc.player && mc.getCameraEntity() == player
				&& mc.gameMode != null && mc.gameMode.canHurtPlayer();
	}

	/** Right edge of the droplet row (the right end of the hotbar). */
	public static int right(GuiGraphicsExtractor graphics) {
		return graphics.guiWidth() / 2 + 91;
	}

	/** Top of the droplet row; only meaningful while {@link #shown} is true. */
	public static int top(GuiGraphicsExtractor graphics) {
		return graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(ID);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Player player = Minecraft.getInstance().player;
		if (!shown(player)) {
			return;
		}
		ThirstData data = Hydration.get(player);
		int thirst = data.thirst();
		int right = right(graphics);
		int top = top(graphics);
		boolean jitter = data.saturation() <= 0.0f && player.tickCount % (thirst * 3 + 1) == 0;
		for (int i = 0; i < 10; i++) {
			int x = right - i * 8 - ICON;
			int y = jitter ? top + JITTER.nextInt(3) - 1 : top;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, x, y, ICON, ICON);
			int half = i * 2 + 1;
			if (half < thirst) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, y, ICON, ICON);
			} else if (half == thirst) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, x, y, ICON, ICON);
			}
		}
	}
}
