package xyz.toesinthesand.ashfall.arms.client.admin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.salvage.admin.AdminAccess;

/**
 * Arms' handle on the "Ashfall: Admin" key category. Salvage's client registers it; registering an id twice
 * throws, so arms only builds an equal value (categories are records compared by id).
 */
public final class AdminKeys {
	public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("ashfall", "admin"));

	private AdminKeys() {
	}

	/** Same courtesy check as salvage's: non-ops get a hint, the server still checks every payload. */
	public static boolean mayOpen(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			return false;
		}
		if (!AdminAccess.isAdmin(client.player.permissions())) {
			client.player.sendOverlayMessage(Component.translatable("screen.ashfall.admin.not_op"));
			return false;
		}
		return true;
	}
}
