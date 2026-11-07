package xyz.toesinthesand.ashfall.arms.client.mission;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.client.gun.ArmsGunsClient;
import xyz.toesinthesand.ashfall.arms.mission.MissionSyncPayload;
import xyz.toesinthesand.ashfall.arms.radio.RadioJobPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client setup for the mission: the {@code ashfall:objective} receiver, the objective HUD line and its toggle key.
 * <p>
 * The toggle ships unbound. The letters near the movement keys are mostly taken in this pack (Xaero's maps,
 * Traveler's Backpack), the defaults of the others could not all be checked, and an unbound key can never
 * clash. The line is on by default, so only a player who wants it gone needs the key, and they can bind it once in
 * Controls under "Ashfall: Arms". The choice is remembered in
 * {@code config/ashfall-objective.txt}, so it survives a restart without a config library.
 */
public final class MissionClient {
	public static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.ashfall.objective", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), ArmsGunsClient.CATEGORY));
	public static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("ashfall", "objective_hud");
	private static final String HIDDEN = "hidden";
	private static volatile boolean visible = true;

	private MissionClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MissionSyncPayload.TYPE, (payload, context) -> MissionClientState.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(RadioJobPayload.TYPE,
				(payload, context) -> RadioJobClientState.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MissionClientState.clear();
			RadioJobClientState.clear();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HUD_ID, ObjectiveHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(MissionClient::onEndTick);
		visible = !HIDDEN.equals(read());
	}

	public static boolean visible() {
		return visible;
	}

	/** Shows or hides the line and remembers it (also used by the client screenshot test). */
	public static void setVisible(boolean show) {
		visible = show;
		write(show ? "shown" : HIDDEN);
	}

	private static void onEndTick(Minecraft client) {
		while (TOGGLE_KEY.consumeClick()) {
			setVisible(!visible);
			if (client.player != null) {
				client.gui.hud.setOverlayMessage(Component.translatable(visible ? "message.ashfall.objective.shown" : "message.ashfall.objective.hidden"), false);
			}
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ashfall-objective.txt");
	}

	private static String read() {
		try {
			Path f = file();
			return Files.exists(f) ? Files.readString(f).trim() : "";
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	private static void write(String value) {
		try {
			Files.writeString(file(), value + "\n");
		} catch (IOException | RuntimeException e) {
			AshfallArms.LOGGER.warn("Could not save the objective line setting: {}", e.toString());
		}
	}
}
