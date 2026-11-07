package xyz.toesinthesand.ashfall.arms.gun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PureHelpersTest {
	@Test
	void headshotBandSitsJustBelowTheEyes() {
		// Player: eyes 1.62 above feet, height 1.8 -> head from 1.35.
		assertTrue(Headshots.isHeadshot(1.7, 1.62, 1.8));
		assertTrue(Headshots.isHeadshot(1.36, 1.62, 1.8));
		assertFalse(Headshots.isHeadshot(1.2, 1.62, 1.8));
		// Small mobs get a proportionally small band (15% of height).
		assertFalse(Headshots.isHeadshot(0.6, 0.765, 0.9));
		assertTrue(Headshots.isHeadshot(0.7, 0.765, 0.9));
	}

	@Test
	void colorsRoundTrip() {
		assertEquals(0xff5a3c, ColorHex.parse("#ff5a3c"));
		assertEquals(0x3cc8ff, ColorHex.parse("3CC8FF"));
		assertEquals("#b45aff", ColorHex.format(0xb45aff));
		assertThrows(IllegalArgumentException.class, () -> ColorHex.parse("#fff"));
		assertThrows(IllegalArgumentException.class, () -> ColorHex.parse("#gggggg"));
	}

	@Test
	void heatRampGoesBlueOrangeRed() {
		assertEquals(0xFF000000 | HeatColors.COLD, HeatColors.color(0f));
		assertEquals(0xFF000000 | HeatColors.WARM, HeatColors.color(0.5f));
		assertEquals(0xFF000000 | HeatColors.HOT, HeatColors.color(1f));
		assertEquals(HeatColors.color(1f), HeatColors.color(3f));
		assertEquals(HeatColors.color(0f), HeatColors.color(-1f));
	}
}
