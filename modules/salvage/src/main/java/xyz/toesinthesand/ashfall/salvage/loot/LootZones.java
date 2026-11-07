package xyz.toesinthesand.ashfall.salvage.loot;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

/**
 * Wires the loot zone and refill system into the game: lazy refill on open, the presence watcher
 * {@code /lootzone} and the admin GUI payloads. Kept in one call so the module entrypoint needs a single line.
 */
public final class LootZones {
	private LootZones() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(LootRefill::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(PresenceTracker::tick);
		LootZoneAdmin.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> LootZoneCommand.register(dispatcher));
	}
}
