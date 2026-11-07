package xyz.toesinthesand.ashfall.arms.client.charger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlockEntity;
import xyz.toesinthesand.ashfall.arms.charger.ChargerKind;
import xyz.toesinthesand.ashfall.arms.charger.ChargerMenu;

import java.util.List;
import java.util.Optional;

/**
 * Charger GUI. Reuses the vanilla hopper background, whose single row of five slots is exactly the
 * charger's layout, and draws the buffer gauge and status in code, so no GUI texture has to ship.
 */
public class ChargerScreen extends AbstractContainerScreen<ChargerMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/hopper.png");
	private static final int WIDTH = 176, HEIGHT = 133;
	/** Buffer gauge, right of the slot row (inside the hopper texture's empty margin). */
	private static final int GAUGE_X = 150, GAUGE_Y = 19, GAUGE_W = 10, GAUGE_H = 18;
	private static final int LABEL = 0xFF404040;

	public ChargerScreen(ChargerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = HEIGHT - 94;
	}

	/** Client init: binds every charger menu type to this screen. */
	public static void register() {
		MenuScreens.register(ArmsBlocks.FIELD_CHARGER_MENU, ChargerScreen::new);
		MenuScreens.register(ArmsBlocks.RAPID_CHARGER_MENU, ChargerScreen::new);
		MenuScreens.register(ArmsBlocks.POWER_RELAY_MENU, ChargerScreen::new);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

		int x = leftPos + GAUGE_X, y = topPos + GAUGE_Y;
		graphics.fill(x - 1, y - 1, x + GAUGE_W + 1, y + GAUGE_H + 1, 0xFF373737);
		graphics.fill(x, y, x + GAUGE_W, y + GAUGE_H, 0xFF1E1E1E);
		int filled = (int) Math.round((double) GAUGE_H * Math.clamp(menu.energy(), 0, ChargerKind.BUFFER) / ChargerKind.BUFFER);
		if (filled > 0) {
			int top = menu.status() == ChargerBlockEntity.STATUS_CHARGING ? 0xFFFFE15A : 0xFFE0A030;
			graphics.fillGradient(x, y + GAUGE_H - filled, x + GAUGE_W, y + GAUGE_H, top, 0xFFB05A10);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		Component status = statusText();
		graphics.text(font, status, imageWidth - 8 - font.width(status), titleLabelY, statusColour(), false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (isHovering(GAUGE_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, List.of(
					Component.translatable("container.ashfall.charger.energy", menu.energy(), ChargerKind.BUFFER),
					statusText()), Optional.empty(), mouseX, mouseY);
		} else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < ChargerKind.SLOTS) {
			String key = hoveredSlot.index == ChargerKind.CELL_SLOT ? "container.ashfall.charger.cell_slot" : "container.ashfall.charger.charge_slot";
			graphics.setTooltipForNextFrame(font, Component.translatable(key), mouseX, mouseY);
		}
	}

	private Component statusText() {
		return Component.translatable(switch (menu.status()) {
			case ChargerBlockEntity.STATUS_CHARGING -> "container.ashfall.charger.charging";
			case ChargerBlockEntity.STATUS_NO_CLAIM -> "container.ashfall.charger.no_claim";
			default -> "container.ashfall.charger.idle";
		});
	}

	private int statusColour() {
		return switch (menu.status()) {
			case ChargerBlockEntity.STATUS_CHARGING -> 0xFF2E7D32;
			case ChargerBlockEntity.STATUS_NO_CLAIM -> 0xFFB71C1C;
			default -> LABEL;
		};
	}
}
