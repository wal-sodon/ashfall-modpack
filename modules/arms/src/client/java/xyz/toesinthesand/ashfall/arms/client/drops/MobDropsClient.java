package xyz.toesinthesand.ashfall.arms.client.drops;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsActionPayload;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsSyncPayload;
import xyz.toesinthesand.ashfall.arms.client.admin.AdminKeys;

/** Client glue for the Mob Drops screen: the sync receiver and the unbound-by-default admin key. */
public final class MobDropsClient {
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ashfall.mob_drops",
			InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), AdminKeys.CATEGORY));

	private MobDropsClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MobDropsSyncPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			if (client.gui.screen() instanceof MobDropsScreen screen) {
				screen.accept(payload);
			} else if (payload.open()) {
				client.gui.setScreen(new MobDropsScreen(payload));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KEY.consumeClick()) {
				if (AdminKeys.mayOpen(client) && ClientPlayNetworking.canSend(MobDropsActionPayload.TYPE)) {
					ClientPlayNetworking.send(MobDropsActionPayload.of(MobDropsActionPayload.Action.OPEN, ""));
				}
			}
		});
	}
}
