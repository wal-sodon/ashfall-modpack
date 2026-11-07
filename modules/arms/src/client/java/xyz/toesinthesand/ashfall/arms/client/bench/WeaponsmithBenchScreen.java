package xyz.toesinthesand.ashfall.arms.client.bench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.bench.ArmsBench;
import xyz.toesinthesand.ashfall.arms.bench.BenchRules;
import xyz.toesinthesand.ashfall.arms.bench.WeaponsmithBenchMenu;
import xyz.toesinthesand.ashfall.arms.grid.GridDistricts;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentItem;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStats;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStatsManager;

/**
 * Weaponsmith Bench GUI. Drawn entirely in code in the vanilla container style (like the charger's
 * gauge), so no GUI texture ships: blaster + attachment = fitted blaster, with a status line explaining
 * a refusal (most importantly which district must be Reclaimed first).
 */
public class WeaponsmithBenchScreen extends AbstractContainerScreen<WeaponsmithBenchMenu> {
	private static final int WIDTH = 176, HEIGHT = 166;
	private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, EDGE = 0xFF000000;
	private static final int SLOT_BG = 0xFF8B8B8B, LABEL = 0xFF404040;

	public WeaponsmithBenchScreen(WeaponsmithBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		inventoryLabelY = HEIGHT - 94;
	}

	public static void register() {
		MenuScreens.register(ArmsBench.MENU, WeaponsmithBenchScreen::new);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = leftPos, y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, EDGE);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
		graphics.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
		graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, PANEL);
		slot(graphics, WeaponsmithBenchMenu.GUN_X, WeaponsmithBenchMenu.SLOT_Y);
		slot(graphics, WeaponsmithBenchMenu.ATTACHMENT_X, WeaponsmithBenchMenu.SLOT_Y);
		slot(graphics, WeaponsmithBenchMenu.RESULT_X, WeaponsmithBenchMenu.SLOT_Y);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				slot(graphics, 8 + col * 18, 84 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			slot(graphics, 8 + col * 18, 142);
		}
	}

	/** A sunken 18x18 slot frame around the 16x16 item position (x, y). */
	private void slot(GuiGraphicsExtractor graphics, int sx, int sy) {
		int x = leftPos + sx - 1, y = topPos + sy - 1;
		graphics.fill(x, y, x + 18, y + 18, SHADOW);
		graphics.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		int midY = WeaponsmithBenchMenu.SLOT_Y + 4;
		graphics.text(font, Component.literal("+"), (WeaponsmithBenchMenu.GUN_X + WeaponsmithBenchMenu.ATTACHMENT_X) / 2 + 5, midY, LABEL, false);
		graphics.text(font, Component.literal("->"), (WeaponsmithBenchMenu.ATTACHMENT_X + WeaponsmithBenchMenu.RESULT_X) / 2 + 3, midY, LABEL, false);
		Component status = statusText();
		if (status != null) {
			int colour = menu.status() == BenchRules.Status.READY ? 0xFF2E7D32 : 0xFFB71C1C;
			graphics.text(font, status, (imageWidth - font.width(status)) / 2, WeaponsmithBenchMenu.SLOT_Y + 24, colour, false);
		}
	}

	private Component statusText() {
		return switch (menu.status()) {
			case EMPTY -> Component.translatable("container.ashfall.weaponsmith_bench.hint");
			case READY -> Component.translatable("container.ashfall.weaponsmith_bench.ready");
			case NOT_A_GUN, NOT_AN_ATTACHMENT -> Component.translatable("container.ashfall.weaponsmith_bench.incompatible");
			case ALREADY_FITTED -> Component.translatable("container.ashfall.weaponsmith_bench.fitted");
			case LOCKED -> Component.translatable("container.ashfall.weaponsmith_bench.locked", lockedDistrict());
		};
	}

	/** The district the attachment in the slot waits for, from the client's synced attachment stats. */
	private Component lockedDistrict() {
		ItemStack attachment = menu.getSlot(WeaponsmithBenchMenu.ATTACHMENT_SLOT).getItem();
		String id = AttachmentItem.attachmentId(attachment);
		AttachmentStats stats = id == null ? null : AttachmentStatsManager.get(true, id);
		return stats == null || stats.requiresDistrict().isEmpty()
				? Component.literal("?") : GridDistricts.name(stats.requiresDistrict().get());
	}
}
