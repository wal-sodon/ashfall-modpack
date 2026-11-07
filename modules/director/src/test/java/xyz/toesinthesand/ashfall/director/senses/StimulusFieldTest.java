package xyz.toesinthesand.ashfall.director.senses;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StimulusFieldTest {
	@Test
	void soundExpiresAfterItsLifetime() {
		StimulusField field = new StimulusField();
		field.emit(StimulusType.SOUND, 0, 64, 0, 10, Stimulus.NO_SOURCE, 1000);
		assertEquals(1, field.query(3, 64, 0, 1.0, 1000 + StimulusType.SOUND.lifetimeTicks() - 1).size());
		assertTrue(field.query(3, 64, 0, 1.0, 1000 + StimulusType.SOUND.lifetimeTicks()).isEmpty());
	}

	@Test
	void sweepDropsExpiredEntries() {
		StimulusField field = new StimulusField();
		field.emit(StimulusType.LIGHT, 0, 64, 0, 20, 7, 0);
		field.emit(StimulusType.SOUND, 100, 64, 100, 50, Stimulus.NO_SOURCE, 0);
		assertEquals(2, field.size());
		field.sweep(StimulusType.LIGHT.lifetimeTicks());
		assertEquals(1, field.size(), "the light lives 40 ticks, the sound 100");
		field.sweep(StimulusType.SOUND.lifetimeTicks());
		assertTrue(field.isEmpty());
	}

	@Test
	void queryHonoursRadiusAndReachScale() {
		StimulusField field = new StimulusField();
		field.emit(StimulusType.SOUND, 0, 64, 0, 10, Stimulus.NO_SOURCE, 0);
		assertEquals(1, field.query(10, 64, 0, 1.0, 1).size(), "exactly at the radius");
		assertTrue(field.query(11, 64, 0, 1.0, 1).isEmpty(), "past the radius");
		assertEquals(1, field.query(12, 64, 0, 1.25, 1).size(), "good hearing stretches the reach");
		assertTrue(field.query(0, 64, 30, 9.0, 1).isEmpty(), "reach scale is clamped");
	}

	@Test
	void smallStimuliAreFoundAcrossSectionBorders() {
		StimulusField field = new StimulusField();
		// radius 24 at the corner of a section; the asker is three sections away diagonally in x
		field.emit(StimulusType.SOUND, 15.9, 64, 15.9, 24, Stimulus.NO_SOURCE, 0);
		assertEquals(1, field.query(15.9 + 24 * 1.3 - 0.1, 64, 15.9, 1.3, 1).size());
		assertEquals(1, field.query(-5, 60, 10, 1.0, 1).size(), "a neighbouring section in every axis");
	}

	@Test
	void hugeStimuliReachFarAway() {
		StimulusField field = new StimulusField();
		field.emit(StimulusType.SOUND, 0, 64, 0, 96, Stimulus.NO_SOURCE, 0);
		assertEquals(1, field.query(90, 64, 0, 1.0, 1).size(), "an explosion is heard 90 blocks away");
		assertTrue(field.query(97, 64, 0, 1.0, 1).isEmpty());
	}

	@Test
	void aSourceRefreshesItsOwnStimulus() {
		StimulusField field = new StimulusField();
		Stimulus first = field.emit(StimulusType.LIGHT, 0, 64, 0, 40, 42, 0);
		Stimulus again = field.emit(StimulusType.LIGHT, 1, 64, 0, 40, 42, 30);
		assertSame(first, again, "same place, same size: refreshed in place");
		assertEquals(30 + StimulusType.LIGHT.lifetimeTicks(), first.expiresAt());
		Stimulus moved = field.emit(StimulusType.LIGHT, 20, 64, 0, 40, 42, 40);
		assertNotNull(moved);
		assertEquals(1, field.size(), "a moved light replaces the old one");
		assertEquals(20, field.query(20, 64, 0, 1.0, 41).getFirst().x());
	}

	@Test
	void sourcelessRepeatsMerge() {
		StimulusField field = new StimulusField();
		for (int tick = 0; tick < 50; tick++) {
			field.emit(StimulusType.SOUND, 5, 64, 5, 40, Stimulus.NO_SOURCE, tick);
		}
		assertEquals(1, field.size(), "automatic fire from one spot is one stimulus");
		List<Stimulus> heard = field.query(5, 64, 5, 1.0, 120);
		assertEquals(1, heard.size(), "and it stays alive while the firing goes on");
	}

	@Test
	void capDropsNewStimuliUntilSomethingExpires() {
		StimulusField field = new StimulusField();
		for (int i = 0; i < StimulusField.MAX_STIMULI; i++) {
			assertNotNull(field.emit(StimulusType.SOUND, i * 10, 64, 0, 4, Stimulus.NO_SOURCE, 0));
		}
		assertNull(field.emit(StimulusType.SOUND, -100, 64, 0, 4, Stimulus.NO_SOURCE, 1));
		assertNotNull(field.emit(StimulusType.SOUND, -100, 64, 0, 4, Stimulus.NO_SOURCE, StimulusType.SOUND.lifetimeTicks()));
		assertEquals(1, field.size());
	}

	@Test
	void nonPositiveRadiusIsIgnored() {
		StimulusField field = new StimulusField();
		assertNull(field.emit(StimulusType.SOUND, 0, 0, 0, 0, Stimulus.NO_SOURCE, 0));
		assertNull(field.emit(StimulusType.SOUND, 0, 0, 0, Double.NaN, Stimulus.NO_SOURCE, 0));
		assertTrue(field.isEmpty());
	}
}
