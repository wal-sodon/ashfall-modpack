package xyz.toesinthesand.ashfall.salvage.client.admin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.salvage.admin.AdminAccess;

/**
 * The "Ashfall: Admin" key category shared by the admin screens of every module (salvage registers it, arms adds
 * its Mob Drops key to it). The admin keys are unbound by default: players never need them, and ops pick a key.
 */
public final class AdminKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ashfall", "admin"));

	private AdminKeys() {
	}

	/**
	 * Client-side courtesy check before asking the server to open an admin screen: a non-op gets a hint instead of
	 * a payload the server would refuse (and log). The server still checks every payload itself.
	 */
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
