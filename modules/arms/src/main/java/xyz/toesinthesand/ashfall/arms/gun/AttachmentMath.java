package xyz.toesinthesand.ashfall.arms.gun;

import java.util.List;

/**
 * How fitted attachments change a gun: every multiplier of every attachment is applied to the gun's
 * base {@link GunStats}. Pure (no registries, no world) so JUnit covers the stat math, and used by both
 * the server ({@link GunLogic}) and the client HUD so heat bars match the server exactly.
 */
public final class AttachmentMath {
	private AttachmentMath() {
	}

	/**
	 * {@code base} with {@code fitted} applied. {@code aiming} (sneaking) adds each attachment's aim spread:
	 * the Optic tightens spread only while you are steadying the shot.
	 */
	public static GunStats apply(GunStats base, List<AttachmentStats> fitted, boolean aiming) {
		float damage = base.damage(), noise = base.noiseRadius(), heatCapacity = base.heatCapacity();
		float heatPerShot = base.heatPerShot(), cool = base.coolPerSecond(), energy = base.energyPerShot();
		float spread = base.spread();
		for (AttachmentStats a : fitted) {
			damage *= a.damage();
			noise *= a.noise();
			heatCapacity *= a.heatCapacity();
			heatPerShot *= a.heatPerShot();
			cool *= a.coolPerSecond();
			energy *= a.energyPerShot();
			if (aiming) {
				spread *= a.aimSpread();
			}
		}
		return new GunStats(damage, base.rpm(), energy, heatPerShot, cool, noise, base.boltSpeed(), spread, base.boltColor(),
				heatCapacity, base.headshotMultiplier(), base.range(), base.scopeZoom(), base.drawTicks(), base.stunEnergyMultiplier());
	}

	/** Product of the fitted attachments' shot-volume multipliers. */
	public static float volume(List<AttachmentStats> fitted) {
		float volume = 1f;
		for (AttachmentStats a : fitted) {
			volume *= a.volume();
		}
		return volume;
	}

	/** Strongest zoom (lowest field-of-view multiplier) among the fitted attachments (1 = none); see {@link ScopeMath}. */
	public static float zoom(List<AttachmentStats> fitted) {
		float zoom = 1f;
		for (AttachmentStats a : fitted) {
			zoom = Math.min(zoom, a.zoom());
		}
		return zoom;
	}
}
