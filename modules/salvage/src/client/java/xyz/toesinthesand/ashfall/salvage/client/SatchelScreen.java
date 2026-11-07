package xyz.toesinthesand.ashfall.salvage.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import xyz.toesinthesand.ashfall.salvage.item.SatchelMenu;

/** Satchel GUI: the vanilla one-row chest background, drawn the way the vanilla container screen draws it. */
public class SatchelScreen extends AbstractContainerScreen<SatchelMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int ROWS = 1;
	private static final int TOP = ROWS * 18 + 17;

	public SatchelScreen(SatchelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 114 + ROWS * 18);
		inventoryLabelY = imageHeight - 94;
	}

	public static void register() {
		MenuScreens.register(SatchelMenu.MENU, SatchelScreen::new);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, TOP, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + TOP, 0, 126, imageWidth, 96, 256, 256);
	}
}
