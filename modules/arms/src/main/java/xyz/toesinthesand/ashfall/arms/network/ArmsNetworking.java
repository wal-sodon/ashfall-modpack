package xyz.toesinthesand.ashfall.arms.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.blade.BladeStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunStatsManager;

/** Registers arms' payloads, the reload (and flamethrower refuel) and fire mode handlers and the gun, attachment and blade stats sync. */
public final class ArmsNetworking {
	private ArmsNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ReloadPayload.TYPE, ReloadPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleModePayload.TYPE, ToggleModePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GunStatsPayload.TYPE, GunStatsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AttachmentStatsPayload.TYPE, AttachmentStatsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BladeStatsPayload.TYPE, BladeStatsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ReloadPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				if (player.isSpectator() || !player.isAlive()) {
					return;
				}
				ItemStack gun = GunLogic.heldGun(player);
				ItemStack flamer = FlamethrowerLogic.heldFlamethrower(player);
				// A flamethrower in the main hand wins over a gun in the off hand, and the other way round.
				boolean refuel = !flamer.isEmpty() && (gun.isEmpty() || flamer == player.getMainHandItem());
				ItemStack held = refuel ? flamer : gun;
				// Anything still cooling down (being drawn, mid-reload, between semi-auto shots) can't start a reload.
				if (held.isEmpty() || player.getCooldowns().isOnCooldown(held)) {
					return;
				}
				if (refuel) {
					FlamethrowerLogic.refuel(player, flamer);
				} else {
					GunLogic.reload(player, gun);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ToggleModePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				if (player.isSpectator() || !player.isAlive()) {
					return;
				}
				ItemStack gun = GunLogic.heldGun(player);
				if (!gun.isEmpty()) {
					GunLogic.toggleMode(player, gun);
				}
			});
		});

		// Fires for each player on join and for everyone after /reload.
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			if (ServerPlayNetworking.canSend(player, GunStatsPayload.TYPE)) {
				ServerPlayNetworking.send(player, new GunStatsPayload(GunStatsManager.serverStats()));
			}
			if (ServerPlayNetworking.canSend(player, AttachmentStatsPayload.TYPE)) {
				ServerPlayNetworking.send(player, new AttachmentStatsPayload(AttachmentStatsManager.serverStats()));
			}
			if (ServerPlayNetworking.canSend(player, BladeStatsPayload.TYPE)) {
				ServerPlayNetworking.send(player, new BladeStatsPayload(BladeStatsManager.all()));
			}
		});
	}
}
