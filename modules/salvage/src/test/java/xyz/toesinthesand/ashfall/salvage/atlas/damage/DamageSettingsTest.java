package xyz.toesinthesand.ashfall.salvage.atlas.damage;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneShape;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageSettingsTest {
	private static Zone zone(String id, float damageScale) {
		return new Zone(id, "district", 0, 1, 0, new ZoneShape.All(), true, Optional.empty(), 0.1f, damageScale, 1f, List.of(), List.of());
	}

	@Test
	void lostCitiesKeepsOnlyWhatItLacksScaledByZone() {
		DamagePass.Settings downtown = DamagePass.Settings.of(zone("downtown", 0.6f), true);
		DamagePass.Settings wasteland = DamagePass.Settings.of(zone("wasteland", 1.5f), true);
		assertTrue(downtown.windowBreak() < DamagePass.WINDOW_BREAK);
		assertTrue(wasteland.windowBreak() > DamagePass.WINDOW_BREAK);
		assertTrue(wasteland.carChance() > downtown.carChance());
		// Lost Cities makes its own ruins, rubble and vines.
		assertEquals(0, wasteland.wallWeather());
		assertEquals(0, wasteland.wallVines());
		assertEquals(0, wasteland.roadDebris());
		assertEquals(0, wasteland.deadBush());
		assertTrue(DamagePass.Settings.of(zone("x", 100f), true).carChance() <= 1.0);
	}

	@Test
	void arnisIgnoresZonesAndLandmarksAreOnlyBoarded() {
		assertSame(DamagePass.Settings.ARNIS, DamagePass.Settings.of(zone("downtown", 0.6f), false));
		DamagePass.Settings landmark = DamagePass.Settings.of(zone("mission", 1f), true).boardingOnly();
		assertEquals(0, landmark.windowBreak());
		assertTrue(landmark.windowBoard() > 0);
	}
}
