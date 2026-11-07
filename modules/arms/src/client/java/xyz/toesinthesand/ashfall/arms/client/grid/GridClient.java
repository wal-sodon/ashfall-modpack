package xyz.toesinthesand.ashfall.arms.client.grid;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.client.gun.ArmsGunsClient;
import xyz.toesinthesand.ashfall.arms.grid.GridSyncPayload;
import xyz.toesinthesand.ashfall.arms.story.GridMapPayload;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiPayload;

/**
 * Client setup for the grid: the sync receivers, the Grid HUD and the Grid Map key. The key defaults to G
 * because M opens Xaero's World Map in the pack and the nearby letters are taken by Xaero's minimap and
 * Traveler's Backpack.
 */
public final class GridClient {
	public static final KeyMapping MAP_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.ashfall.grid_map", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, ArmsGunsClient.CATEGORY));
	public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("ashfall", "grid_hud");

	private GridClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GridSyncPayload.TYPE, (payload, context) -> GridClientState.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(GridMapPayload.TYPE, (payload, context) -> GridClientState.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(GraffitiPayload.TYPE, (payload, context) -> GraffitiClientState.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GridClientState.clear();
			GraffitiClientState.clear();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HUD_ID, GridHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(GridClient::onEndTick);
	}

	private static void onEndTick(Minecraft client) {
		while (MAP_KEY.consumeClick()) {
			if (client.player != null && client.gui.screen() == null) {
				client.gui.setScreen(new GridMapScreen());
			}
		}
	}
}
