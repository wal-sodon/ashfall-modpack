package xyz.toesinthesand.ashfall.director.client.skills;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.director.skills.SkillPayloads;
import xyz.toesinthesand.ashfall.director.skills.SkillRules;
import xyz.toesinthesand.ashfall.director.skills.Skills;

/** Client glue for Survivor Skills: the K key, the forced class picker and the server's skill balance. */
public final class SkillsClient {
	public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("ashfall", "director"));
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ashfall.skills",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));

	/** The connected server's balance (the built-in one until it arrives). */
	private static volatile SkillRules rules = SkillRules.DEFAULT;
	/** The server asked for the picker; it opens as soon as no other screen (the world loading screen) is up. */
	private static boolean pickPending;

	private SkillsClient() {
	}

	public static SkillRules rules() {
		return rules;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SkillPayloads.Rules.TYPE, (payload, context) -> rules = payload.rules());
		ClientPlayNetworking.registerGlobalReceiver(SkillPayloads.PickClass.TYPE, (payload, context) -> pickPending = true);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> pickPending = false);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				// still loading in: keep the request for when the player exists
			} else if (pickPending && Skills.survivorClass(client.player) != null) {
				pickPending = false;
			} else if (pickPending && client.gui.screen() == null) {
				client.gui.setScreen(new ClassPickerScreen());
			}
			while (KEY.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(Skills.survivorClass(client.player) == null ? new ClassPickerScreen() : new SkillsScreen());
				}
			}
		});
	}
}
