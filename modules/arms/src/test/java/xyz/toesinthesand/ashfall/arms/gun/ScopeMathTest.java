package xyz.toesinthesand.ashfall.arms.gun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scope steps, easing, overlay and sensitivity for the Optic. */
class ScopeMathTest {
	private static final float EPS = 1e-4f;
	private static final float OPTIC_FOV = 0.25f; // gun_attachment/optic.json

	@Test
	void noOpticNoScope() {
		assertNull(ScopeMath.levels(1f, 0f));
		assertNull(ScopeMath.levels(1f, 7f), "scope_zoom alone does nothing without an Optic");
	}

	@Test
	void opticGivesFourAndTwo() {
		ScopeMath.Levels levels = ScopeMath.levels(OPTIC_FOV, 0f);
		assertEquals(4f, levels.high(), EPS);
		assertEquals(2f, levels.low(), EPS);
		assertEquals(2, levels.count());
		assertEquals("2x / 4x", levels.describe());
	}

	@Test
	void marksmanScopeZoomOverridesTheOptic() {
		ScopeMath.Levels lancer = ScopeMath.levels(OPTIC_FOV, 7f);
		assertEquals(7f, lancer.high(), EPS);
		assertEquals(3.5f, lancer.low(), EPS);
		assertEquals("3.5x / 7x", lancer.describe());
	}

	@Test
	void magnificationIsClampedAndWeakScopesHaveOneStep() {
		assertEquals(ScopeMath.MAX_MAGNIFICATION, ScopeMath.levels(0.05f, 0f).high(), EPS);
		assertEquals(ScopeMath.MAX_MAGNIFICATION, ScopeMath.levels(OPTIC_FOV, 50f).high(), EPS);
		ScopeMath.Levels weak = ScopeMath.levels(0.6f, 0f); // the old 1.67x optic
		assertEquals(1, weak.count());
		assertEquals(weak.high(), weak.magnification(0), EPS);
	}

	@Test
	void scrollUpZoomsInScrollDownZoomsOut() {
		ScopeMath.Levels levels = ScopeMath.levels(OPTIC_FOV, 0f);
		assertEquals(4f, levels.magnification(1), EPS);
		assertEquals(0, levels.step(1, -1));
		assertEquals(0, levels.step(0, -1));
		assertEquals(1, levels.step(0, 1));
		assertEquals(0, levels.step(0, 0));
		assertEquals(2f, levels.magnification(levels.step(1, -0.3)), EPS);
		assertEquals(1, ScopeMath.levels(0.6f, 0f).step(1, -1), "a one-step scope ignores scrolling");
	}

	@Test
	void easingIsSmoothFrameRateIndependentAndSettles() {
		float m = 1f;
		m = ScopeMath.ease(m, 4f, 1f / 60f);
		assertTrue(m > 1f && m < 4f, "partway after one frame: " + m);
		// Two 30 fps frames land where four 60 fps frames do (exponential approach).
		float a = 1f, b = 1f;
		for (int i = 0; i < 2; i++) a = ScopeMath.ease(a, 4f, 1f / 30f);
		for (int i = 0; i < 4; i++) b = ScopeMath.ease(b, 4f, 1f / 60f);
		assertEquals(a, b, 1e-3f);
		float settled = 1f;
		for (int i = 0; i < 60; i++) settled = ScopeMath.ease(settled, 4f, 1f / 60f);
		assertEquals(4f, settled, EPS);
		assertEquals(1f, ScopeMath.ease(1f, 1f, 0.05f), EPS);
		assertEquals(1f, ScopeMath.ease(1f, 4f, -1f), EPS, "negative frame time is ignored");
	}

	@Test
	void fovAndSensitivityFollowTheZoom() {
		assertEquals(0.25f, ScopeMath.fovMultiplier(4f), EPS);
		assertEquals(1f, ScopeMath.fovMultiplier(0.5f), EPS);
		assertEquals(0.25, ScopeMath.sensitivity(4f), EPS);
		assertEquals(1.0 / 7.0, ScopeMath.sensitivity(7f), EPS);
		assertEquals(1.0, ScopeMath.sensitivity(1f), EPS);
	}

	@Test
	void overlayShowsPastTheThresholdAndOpensAsItSettles() {
		assertFalse(ScopeMath.overlayVisible(1f));
		assertFalse(ScopeMath.overlayVisible(1.3f));
		assertTrue(ScopeMath.overlayVisible(2f));
		assertEquals(ScopeMath.OVERLAY_SCALE_START, ScopeMath.overlayScale(ScopeMath.OVERLAY_THRESHOLD, 4f), EPS);
		assertEquals(ScopeMath.OVERLAY_SCALE_FULL, ScopeMath.overlayScale(4f, 4f), EPS);
		float mid = ScopeMath.overlayScale(2.7f, 4f);
		assertTrue(mid > ScopeMath.OVERLAY_SCALE_START && mid < ScopeMath.OVERLAY_SCALE_FULL);
		assertEquals(ScopeMath.OVERLAY_SCALE_FULL, ScopeMath.overlayScale(4f, 2f), EPS, "zooming out keeps it open");
	}

	@Test
	void labels() {
		assertEquals("4x", ScopeMath.label(4f));
		assertEquals("3.5x", ScopeMath.label(3.5f));
		assertEquals("7x", ScopeMath.label(6.999f));
	}
}
