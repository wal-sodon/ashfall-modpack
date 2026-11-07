package xyz.toesinthesand.ashfall.arms.client.gun;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.DrawTime;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerItem;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunState;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunStats;
import xyz.toesinthesand.ashfall.arms.gun.HeatColors;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;

/**
 * The gun HUD, drawn to the right of the hotbar while a gun is held: the gun's name, a heat bar (blue to
 * orange to red, flashing while locked out) and the loaded pack's charge. Right after a gun is drawn its name
 * gives way to "READYING" and a bar that fills as the draw cooldown ({@link DrawTime}) runs out. A gun in stun mode
 * shows a pale blue "STUN" tag after the charge. The flamethrower shows its tank instead: an orange fuel bar and the
 * seconds of fire left.
 * <p>
 * Heat is computed client-side from the synced {@code gun_state} and the synced stats, with the same
 * lazy formula the server uses, so the bar cools smoothly without any packets.
 */
public final class GunHud {
	private static final int BAR_WIDTH = 64;
	private static final int BAR_HEIGHT = 4;
	private static final int GAP_FROM_HOTBAR = 10;
	/** Slack after a draw for the server's cooldown to reach the client before READYING gives up. */
	private static final int READYING_GRACE_TICKS = 10;

	// The client's own view of the draw: the selected slot and item it last saw, and when that changed to a gun.
	private static int lastSlot = -1;
	private static Item lastItem = null;
	private static long drawnAt = Long.MIN_VALUE;

	private GunHud() {
	}

	private static boolean isHudItem(ItemStack stack) {
		return stack.getItem() instanceof EnergyGunItem || stack.getItem() instanceof FlamethrowerItem;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.player.isSpectator()) {
			return;
		}
		long gameTime = mc.level.getGameTime();
		int slot = mc.player.getInventory().getSelectedSlot();
		Item held = mc.player.getMainHandItem().getItem();
		if (slot != lastSlot || held != lastItem) {
			lastSlot = slot;
			lastItem = held;
			drawnAt = held instanceof EnergyGunItem || held instanceof FlamethrowerItem ? gameTime : Long.MIN_VALUE;
		}
		// Main hand only: guns and the flamethrower don't work from the off hand.
		ItemStack gun = mc.player.getMainHandItem();
		if (!isHudItem(gun)) {
			return;
		}
		Font font = mc.font;
		int x = graphics.guiWidth() / 2 + 91 + GAP_FROM_HOTBAR;
		int bottom = graphics.guiHeight();

		boolean flamer = gun.getItem() instanceof FlamethrowerItem;
		GunStats stats = flamer ? null : GunLogic.effectiveStats(true, gun, false);
		float cooldown = mc.player.getCooldowns().getCooldownPercent(gun, delta.getGameTimeDeltaPartialTick(false));
		int drawTicks = flamer ? FlamethrowerLogic.DRAW_TICKS : stats == null ? 0 : stats.drawTicks();
		int drawWindow = drawTicks + DrawTime.HOLSTER_TICKS + READYING_GRACE_TICKS;
		boolean readying = gun == mc.player.getMainHandItem() && cooldown > 0f && drawnAt != Long.MIN_VALUE
				&& gameTime - drawnAt <= drawWindow;
		if (readying) {
			graphics.text(font, Component.translatable("hud.ashfall.gun.readying"), x, bottom - 33, 0xFFFFE066, true);
			graphics.fill(x, bottom - 24, x + Math.round(BAR_WIDTH * (1f - cooldown)), bottom - 23, 0xFFFFE066);
		} else {
			graphics.text(font, gun.getHoverName(), x, bottom - 33, 0xFFFFFFFF, true);
		}

		int barY = bottom - 21;
		graphics.fill(x - 1, barY - 1, x + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xC0000000);
		if (flamer) {
			// The tank in place of the heat bar, and the seconds of fire left in place of the pack's charge.
			int fuel = FlamethrowerLogic.fuel(gun);
			int filled = Math.round(BAR_WIDTH * (float) fuel / FlamethrowerLogic.TANK_TICKS);
			if (filled > 0) {
				graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT, 0xFF000000 | FlamethrowerItem.FUEL_BAR_COLOR);
			}
			graphics.text(font, Component.translatable("hud.ashfall.flamethrower.fuel", FlamethrowerItem.seconds(fuel)),
					x, bottom - 14, fuel > 0 ? 0xFF000000 | FlamethrowerItem.FUEL_BAR_COLOR : 0xFFFF5555, true);
			return;
		}
		if (stats != null) {
			long now = mc.level.getGameTime();
			GunState state = gun.getOrDefault(ArmsComponents.GUN_STATE, GunState.EMPTY);
			float fraction = Math.min(1f, state.heatAt(now, stats.coolPerSecond()) / stats.heatCapacity());
			boolean locked = state.lockedAt(now, stats.coolPerSecond());
			int color = HeatColors.color(fraction);
			if (locked && (now / 4) % 2 == 0) {
				color = 0xFF5A1010; // flash dark red while locked out
			}
			int filled = Math.round(BAR_WIDTH * fraction);
			if (filled > 0) {
				graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT, color);
			}
		}

		ItemStackTemplate pack = gun.get(ArmsComponents.LOADED_PACK);
		Component charge = pack == null
				? Component.translatable("hud.ashfall.gun.no_pack")
				: Component.literal(EnergyItems.stored(pack) + "/" + EnergyItems.capacity(pack));
		graphics.text(font, charge, x, bottom - 14, pack == null ? 0xFFFF5555 : 0xFF55FFFF, true);
		if (GunLogic.isStunMode(gun, stats)) {
			Component tag = Component.translatable("hud.ashfall.gun.stun");
			graphics.text(font, tag, x + font.width(charge) + 6, bottom - 14, 0xFF000000 | GunLogic.STUN_BOLT_COLOR, true);
		}
	}
}
