package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends {@link GridSyncPayload} to each player on join and to everyone after any district changes state. Players
 * whose client has no receiver for it (a client without Ashfall's UI) are skipped by {@code canSend}.
 */
public final class GridNetworking {
	private GridNetworking() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(GridSyncPayload.TYPE, GridSyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), payload(server)));
		GridEvents.DISTRICT_CHANGED.register((level, id, from, to) -> sendAll(level.getServer()));
	}

	/** The current grid as a payload. */
	public static GridSyncPayload payload(MinecraftServer server) {
		GridState grid = GridState.get(server);
		return new GridSyncPayload(grid.snapshot(), grid.gridPercent());
	}

	public static void sendAll(MinecraftServer server) {
		GridSyncPayload payload = payload(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, payload);
		}
	}

	private static void send(ServerPlayer player, GridSyncPayload payload) {
		if (ServerPlayNetworking.canSend(player, GridSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
