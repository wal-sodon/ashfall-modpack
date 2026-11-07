package xyz.toesinthesand.ashfall.salvage.client.loot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import xyz.toesinthesand.ashfall.salvage.client.admin.AdminKeys;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneActionPayload;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneSyncPayload;

/**
 * Client glue for the Loot Zone screen: the sync receiver (opens or refreshes the screen, fills the clipboard on
 * export) and the unbound-by-default admin key, which asks the server to open the screen.
 */
public final class LootZoneClient {
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ashfall.loot_zones",
			InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), AdminKeys.CATEGORY));

	private LootZoneClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LootZoneSyncPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			if (!payload.clipboard().isEmpty()) {
				client.keyboardHandler.setClipboard(payload.clipboard());
			}
			if (client.gui.screen() instanceof LootZoneScreen screen) {
				screen.accept(payload);
			} else if (payload.open()) {
				client.gui.setScreen(new LootZoneScreen(payload));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KEY.consumeClick()) {
				if (AdminKeys.mayOpen(client) && ClientPlayNetworking.canSend(LootZoneActionPayload.TYPE)) {
					ClientPlayNetworking.send(LootZoneActionPayload.of(LootZoneActionPayload.Action.OPEN, ""));
				}
			}
		});
	}
}
