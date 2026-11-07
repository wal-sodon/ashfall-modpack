package xyz.toesinthesand.ashfall.arms.mission;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xyz.toesinthesand.ashfall.arms.story.StoryRules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each client's objective current. Every {@link MissionSettings#syncIntervalTicks} the server recomputes each
 * online player's objective and sends {@link MissionSyncPayload} only when it differs from the last one that player
 * got, so a district hold ticking up costs one small packet a second and a player standing still costs nothing.
 * <p>
 * Polling rather than hooking every source (grid changes, inventory, boss deaths, the finale, the ending) keeps the
 * tracker correct whatever changed the world, including admin commands and other mods, for the price of a few saved
 * data lookups per player per second. The same pass makes "armed" sticky and delivers the first-join briefing a few
 * seconds after login, once the loading screen is gone, or a few seconds after {@code ashfall:objective} is turned on
 * for a player who joined while it was off.
 */
public final class MissionTracker {
	/** The last payload each player was sent. */
	private static final Map<UUID, MissionSyncPayload> LAST = new HashMap<>();
	/** Players waiting for their briefing, with the server tick it is due at. */
	private static final Map<UUID, Long> PENDING_BRIEFING = new HashMap<>();

	private MissionTracker() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(MissionSyncPayload.TYPE, MissionSyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			LAST.remove(player.getUUID());
			sync(player);
			if (!MissionProgress.get(player).briefed()) {
				PENDING_BRIEFING.put(player.getUUID(), (long) server.getTickCount() + MissionSettings.get().briefingDelayTicks());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST.remove(handler.getPlayer().getUUID());
			PENDING_BRIEFING.remove(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(MissionTracker::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			PENDING_BRIEFING.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (!PENDING_BRIEFING.isEmpty()) {
			long now = server.getTickCount();
			PENDING_BRIEFING.entrySet().removeIf(e -> {
				if (e.getValue() > now) {
					return false;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player != null) {
					MissionBriefing.maybeBrief(player);
					sync(player);
				}
				return true;
			});
		}
		if (server.getTickCount() % MissionSettings.get().syncIntervalTicks() != 0) {
			return;
		}
		boolean prompts = StoryRules.objective(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
			// Someone who joined with the rule off was dropped from the queue unbriefed; an op turning the rule on
			// mid-session (the usual singleplayer case) should brief them now, not on their next login.
			if (prompts && !PENDING_BRIEFING.containsKey(player.getUUID()) && !MissionProgress.get(player).briefed()) {
				PENDING_BRIEFING.put(player.getUUID(), (long) server.getTickCount() + MissionSettings.get().briefingDelayTicks());
			}
		}
	}

	/** Recomputes {@code player}'s objective and sends it if it changed. */
	public static void sync(ServerPlayer player) {
		if (!player.isSpectator() && MissionWorld.armedNow(player)) {
			MissionProgress.set(player, MissionProgress.get(player).withArmed());
		}
		if (!ServerPlayNetworking.canSend(player, MissionSyncPayload.TYPE)) {
			return;
		}
		MissionSyncPayload payload = payloadFor(player);
		if (!payload.equals(LAST.get(player.getUUID()))) {
			LAST.put(player.getUUID(), payload);
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** What {@code player}'s client should show now (public for gametests, whose mock players have no client). */
	public static MissionSyncPayload payloadFor(ServerPlayer player) {
		Objective objective = MissionResolver.current(MissionWorld.collect(player));
		return MissionSyncPayload.of(StoryRules.objective(player.level().getServer()), objective, MissionWorld.bunker(player));
	}
}
