package xyz.toesinthesand.ashfall.arms.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentMath;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.GunAttachments;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunStats;
import xyz.toesinthesand.ashfall.arms.gun.GunStatsManager;
import xyz.toesinthesand.ashfall.arms.gun.ScopeMath;

/**
 * The blaster scope's client state. Sneaking in first person with an Optic-fitted blaster aims down the
 * scope: the view eases to the scope's high step (4x for the Optic, the gun's {@code scope_zoom} for a
 * marksman rifle), the scroll wheel switches between the two steps, a scope overlay replaces the crosshair
 * and held blaster, and mouse sensitivity drops with the zoom.
 * <p>
 * The zoom is applied on top of the final field of view ({@code OpticZoomMixin}), not through the
 * player's FOV modifier, so the "FOV Effects" accessibility slider does not weaken it: scoping is a
 * mechanic, like the spyglass's overlay. It still eases in and out instead of snapping.
 */
public final class ScopeClient {
	/** Current eased magnification (1 = none). */
	private static float magnification = 1f;
	/** Magnification the view is easing toward. */
	private static float target = 1f;
	/** Scope step while aiming (index into {@link ScopeMath.Levels}); starts at the high step. */
	private static int level = 1;
	private static ScopeMath.Levels active;
	private static long lastFrameNanos;

	private ScopeClient() {
	}

	/** The scope steps of the blaster the player is aiming down right now, or null when not aiming a scope. */
	public static ScopeMath.Levels aimedLevels(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator() || !player.isShiftKeyDown() || !mc.options.getCameraType().isFirstPerson()) {
			return null;
		}
		ItemStack gun = GunLogic.heldGun(player);
		if (gun.isEmpty()) {
			return null;
		}
		GunAttachments attachments = gun.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE);
		if (attachments.isEmpty()) {
			return null;
		}
		float opticFov = AttachmentMath.zoom(AttachmentStatsManager.fitted(true, attachments));
		GunStats stats = GunStatsManager.get(true, gun.getItem());
		return ScopeMath.levels(opticFov, stats == null ? 0f : stats.scopeZoom());
	}

	/**
	 * Advances the eased zoom by one frame and returns the field-of-view multiplier to apply. Called once
	 * per frame when the camera computes its field of view.
	 */
	public static float frameFovMultiplier(Minecraft mc) {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1.0e9f;
		lastFrameNanos = now;

		ScopeMath.Levels levels = aimedLevels(mc);
		if (levels == null) {
			level = 1; // the next aim starts at full zoom
		}
		active = levels;
		target = levels == null ? 1f : levels.magnification(level);
		magnification = mc.isPaused() ? magnification : ScopeMath.ease(magnification, target, dt);
		return ScopeMath.fovMultiplier(magnification);
	}

	/**
	 * A scroll-wheel notch. While aiming a two-step scope it switches steps and is consumed (returns true),
	 * so the hotbar does not move; at any other time the scroll is left alone.
	 */
	public static boolean onScroll(double scroll) {
		ScopeMath.Levels levels = aimedLevels(Minecraft.getInstance());
		if (levels == null || levels.count() < 2) {
			return false;
		}
		level = levels.step(level, scroll);
		return true;
	}

	/** Whether the scope overlay is showing (the crosshair and first-person blaster are hidden). */
	public static boolean scoped() {
		return ScopeMath.overlayVisible(magnification);
	}

	/** Current eased magnification. */
	public static float magnification() {
		return magnification;
	}

	/** Magnification being eased toward (the selected step while aiming, 1 otherwise). */
	public static float target() {
		return target;
	}

	/** The steps of the scope being aimed, or null. */
	public static ScopeMath.Levels active() {
		return active;
	}

	/** Mouse sensitivity multiplier for the current zoom. */
	public static double sensitivity() {
		return ScopeMath.sensitivity(magnification);
	}
}
