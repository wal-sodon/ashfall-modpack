package xyz.toesinthesand.ashfall.director.client.infection;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import xyz.toesinthesand.ashfall.director.client.hydration.ThirstHud;
import xyz.toesinthesand.ashfall.director.infection.Infection;

/**
 * The infection stage indicator: one 9x9 germ icon just right of the thirst droplets, coloured by stage.
 * It appears from stage 2, the first stage with real symptoms; stage 1 is cosmetic and only announced in
 * the action bar, so a fresh bite does not clutter the HUD.
 * <p>
 * It reads the synced {@code ashfall:infection_stage} attachment (the stage alone, not the timeline) and
 * follows the thirst row's visibility rule, so it never floats on its own when the droplets are hidden. It
 * sits outside the hotbar's width, so it needs no status-bar height of its own.
 */
public final class InfectionHud {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "infection_stage");
	/** The first stage the indicator shows. */
	public static final int FIRST_SHOWN_STAGE = 2;
	private static final Identifier[] SPRITES = {
			Identifier.fromNamespaceAndPath("ashfall", "hud/infection_stage_2"),
			Identifier.fromNamespaceAndPath("ashfall", "hud/infection_stage_3"),
			Identifier.fromNamespaceAndPath("ashfall", "hud/infection_stage_4"),
	};
	private static final int ICON = 9;
	private static final int GAP = 2;

	private InfectionHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(ThirstHud.ID, ID, InfectionHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Player player = Minecraft.getInstance().player;
		if (!ThirstHud.shown(player)) {
			return;
		}
		int stage = Infection.stage(player);
		if (stage < FIRST_SHOWN_STAGE) {
			return;
		}
		Identifier sprite = SPRITES[Math.min(stage, SPRITES.length + 1) - FIRST_SHOWN_STAGE];
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, ThirstHud.right(graphics) + GAP, ThirstHud.top(graphics), ICON, ICON);
	}
}
