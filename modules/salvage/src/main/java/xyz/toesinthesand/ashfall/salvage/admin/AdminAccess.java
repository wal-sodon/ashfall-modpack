package xyz.toesinthesand.ashfall.salvage.admin;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

/**
 * The one permission gate for every admin GUI payload (loot zones, mob drops): operator level 2, the same level
 * as {@code /lootzone}.
 * <p>
 * The GUI is only a convenience on top of the commands, so the server must never trust that a client only shows
 * it to ops: a modified client can send any payload at any time. Each handler therefore asks here first, and a
 * rejection is logged with the player and the action so a server owner can see who tried.
 */
public final class AdminAccess {
	private AdminAccess() {
	}

	/** Whether {@code permissions} reach the game-master level (op level 2). */
	public static boolean isAdmin(PermissionSet permissions) {
		return Commands.LEVEL_GAMEMASTERS.check(permissions);
	}

	/**
	 * Checks {@code permissions} (normally {@code player.permissions()}; tests pass an explicit set) and logs a
	 * rejection.
	 *
	 * @param action short name of what was attempted, for the log
	 * @return true if the action may proceed
	 */
	public static boolean check(ServerPlayer player, PermissionSet permissions, String action) {
		if (isAdmin(permissions)) {
			return true;
		}
		AshfallSalvage.LOGGER.warn("Rejected admin payload '{}' from {} ({}): needs operator level 2",
				action, player.getName().getString(), player.getUUID());
		return false;
	}
}
