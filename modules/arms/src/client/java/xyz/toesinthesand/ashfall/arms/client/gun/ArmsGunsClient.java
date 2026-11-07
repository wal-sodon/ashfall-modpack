package xyz.toesinthesand.ashfall.arms.client.gun;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.blade.BladeStatsManager;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunStatsManager;
import xyz.toesinthesand.ashfall.arms.network.AttachmentStatsPayload;
import xyz.toesinthesand.ashfall.arms.network.BladeStatsPayload;
import xyz.toesinthesand.ashfall.arms.network.GunStatsPayload;
import xyz.toesinthesand.ashfall.arms.network.ReloadPayload;
import xyz.toesinthesand.ashfall.arms.network.ToggleModePayload;

import java.util.Map;

/** Client setup for energy weapons: reload and fire mode keys, bolt renderer, particles and effects, gun HUD, scope overlay and the stats sync receiver. */
public final class ArmsGunsClient {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ashfall", "arms"));
	public static final KeyMapping RELOAD = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.ashfall.reload", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
	/** Damage/stun switch. C: G is our Grid Map (V was a voice chat mod's key when the pack had one; C stays put). */
	public static final KeyMapping FIRE_MODE = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.ashfall.fire_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));
	public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("ashfall", "gun_hud");

	private ArmsGunsClient() {
	}

	public static void init() {
		EntityRenderers.register(ArmsEntities.ENERGY_BOLT, EnergyBoltRenderer::new);
		EntityRenderers.register(ArmsEntities.NAIL, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
		EntityRenderers.register(ArmsEntities.BOTTLE_LURE, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
		EntityRenderers.register(ArmsEntities.ARC_CHARGE, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
		BoltParticles.register();
		EnergyBolt.clientEffects = BoltEffects.INSTANCE;
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HUD_ID, GunHud::extract);
		// Blaster scope: overlay above the vanilla vignette (under the hotbar and gun HUD); no crosshair while scoped.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ScopeOverlay.HUD_ID, ScopeOverlay::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, crosshair -> (graphics, delta) -> {
			if (!ScopeClient.scoped()) {
				crosshair.extractRenderState(graphics, delta);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(GunStatsPayload.TYPE,
				(payload, context) -> GunStatsManager.setClientStats(payload.stats()));
		ClientPlayNetworking.registerGlobalReceiver(AttachmentStatsPayload.TYPE,
				(payload, context) -> AttachmentStatsManager.setClientStats(payload.stats()));
		// Blade stats share one map with the integrated server, so only a remote client takes the server's copy.
		ClientPlayNetworking.registerGlobalReceiver(BladeStatsPayload.TYPE, (payload, context) -> {
			if (context.client().getSingleplayerServer() == null) {
				BladeStatsManager.setSynced(payload.stats());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GunStatsManager.setClientStats(Map.of());
			AttachmentStatsManager.setClientStats(Map.of());
		});

		ClientTickEvents.END_CLIENT_TICK.register(ArmsGunsClient::onEndTick);
	}

	private static void onEndTick(Minecraft client) {
		while (RELOAD.consumeClick()) {
			if (client.player != null && (!GunLogic.heldGun(client.player).isEmpty()
					|| !FlamethrowerLogic.heldFlamethrower(client.player).isEmpty())
					&& ClientPlayNetworking.canSend(ReloadPayload.TYPE)) {
				ClientPlayNetworking.send(ReloadPayload.INSTANCE);
			}
		}
		while (FIRE_MODE.consumeClick()) {
			// Only guns with a stun mode; the server checks again and owns the switch.
			if (client.player != null && GunLogic.hasStunMode(GunLogic.effectiveStats(true, GunLogic.heldGun(client.player), false))
					&& ClientPlayNetworking.canSend(ToggleModePayload.TYPE)) {
				ClientPlayNetworking.send(ToggleModePayload.INSTANCE);
			}
		}
	}
}
