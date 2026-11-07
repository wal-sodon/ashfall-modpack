package xyz.toesinthesand.ashfall.arms.gun;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Attachment stat math: multipliers stack per stat, aim-only spread, volume and zoom. */
class AttachmentMathTest {
	private static final float EPS = 1e-4f;
	private static final GunStats ION = new GunStats(9f, 200f, 3f, 14f, 25f, 48f, 6f, 0.6f, 0x5aff8c, 100f, 1.75f, 96f, 0f, 10, 0f);

	private static AttachmentStats attachment(String slot, float damage, float noise, float volume, float heatCapacity,
											  float heatPerShot, float cool, float energy, float aimSpread, float zoom) {
		return new AttachmentStats(slot, damage, noise, volume, heatCapacity, heatPerShot, cool, energy, aimSpread, zoom, Optional.empty());
	}

	private static final AttachmentStats OPTIC = attachment("optic", 1, 1, 1, 1, 1, 1, 1, 0.4f, 0.25f);
	private static final AttachmentStats DAMPENER = AttachmentStats.DAMPENER_FALLBACK;
	private static final AttachmentStats HEAT_SINK = attachment("cooling", 1, 1, 1, 1.35f, 1, 1.3f, 1, 1, 1);
	private static final AttachmentStats REGULATOR = attachment("core", 1, 1, 1, 1, 1, 1, 0.7f, 1, 1);
	private static final AttachmentStats OVERCHARGER = attachment("core", 1.25f, 1, 1, 1, 1.4f, 1, 1.2f, 1, 1);

	@Test
	void noAttachmentsChangeNothing() {
		assertEquals(ION, AttachmentMath.apply(ION, List.of(), true));
		assertEquals(1f, AttachmentMath.volume(List.of()), EPS);
		assertEquals(1f, AttachmentMath.zoom(List.of()), EPS);
	}

	@Test
	void drawAndStunStatsPassThrough() {
		GunStats s = AttachmentMath.apply(ION, List.of(OPTIC, DAMPENER, OVERCHARGER), true);
		assertEquals(10, s.drawTicks());
		assertEquals(0f, s.stunEnergyMultiplier(), EPS);
	}

	@Test
	void dampenerMatchesTheOldHardCodedNumbers() {
		GunStats s = AttachmentMath.apply(ION, List.of(DAMPENER), false);
		assertEquals(9f * 0.9f, s.damage(), EPS);
		assertEquals(48f * 0.4f, s.noiseRadius(), EPS);
		assertEquals(0.45f, AttachmentMath.volume(List.of(DAMPENER)), EPS);
	}

	@Test
	void heatSinkRaisesCapacityAndCooling() {
		GunStats s = AttachmentMath.apply(ION, List.of(HEAT_SINK), false);
		assertEquals(135f, s.heatCapacity(), EPS);
		assertEquals(32.5f, s.coolPerSecond(), EPS);
		assertEquals(14f, s.heatPerShot(), EPS);
	}

	@Test
	void regulatorAndOverchargerTradeEnergyHeatAndDamage() {
		assertEquals(2.1f, AttachmentMath.apply(ION, List.of(REGULATOR), false).energyPerShot(), EPS);
		GunStats over = AttachmentMath.apply(ION, List.of(OVERCHARGER), false);
		assertEquals(11.25f, over.damage(), EPS);
		assertEquals(19.6f, over.heatPerShot(), EPS);
		assertEquals(3.6f, over.energyPerShot(), EPS);
	}

	@Test
	void opticTightensSpreadOnlyWhileAiming() {
		assertEquals(0.6f, AttachmentMath.apply(ION, List.of(OPTIC), false).spread(), EPS);
		assertEquals(0.24f, AttachmentMath.apply(ION, List.of(OPTIC), true).spread(), EPS);
		assertEquals(0.25f, AttachmentMath.zoom(List.of(DAMPENER, OPTIC)), EPS);
	}

	@Test
	void multipliersStack() {
		GunStats s = AttachmentMath.apply(ION, List.of(DAMPENER, HEAT_SINK, OVERCHARGER), false);
		assertEquals(9f * 0.9f * 1.25f, s.damage(), EPS);
		assertEquals(135f, s.heatCapacity(), EPS);
		assertEquals(200f, s.rpm(), EPS);
		assertEquals(0x5aff8c, s.boltColor());
	}
}
