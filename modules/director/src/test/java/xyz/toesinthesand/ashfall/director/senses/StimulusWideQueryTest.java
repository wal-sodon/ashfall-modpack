package xyz.toesinthesand.ashfall.director.senses;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The horde query: a reach scale above the single-zombie clamp, and blood's long life. */
class StimulusWideQueryTest {
	@Test
	void hordesHearFurtherThanOneZombie() {
		StimulusField field = new StimulusField();
		field.emit(StimulusType.SOUND, 0, 64, 0, 20, Stimulus.NO_SOURCE, 0);
		assertTrue(field.query(36, 64, 0, 2.0, 1).isEmpty(), "a single zombie's scale is clamped to 1.3");
		List<Stimulus> wide = new ArrayList<>();
		field.queryWide(36, 64, 0, 2.0, 1, wide::add);
		assertEquals(1, wide.size(), "a horde at scale 2 hears a radius-20 sound 36 blocks away");
		wide.clear();
		field.queryWide(41, 64, 0, 2.0, 1, wide::add);
		assertTrue(wide.isEmpty(), "but not beyond radius x 2");
	}

	@Test
	void wideQueryScansEnoughSectionsForIndexedStimuli() {
		StimulusField field = new StimulusField();
		// radius 24 sits in the section index; at scale 2 it reaches 48 blocks, three sections away
		field.emit(StimulusType.SOUND, 0, 64, 0, 24, Stimulus.NO_SOURCE, 0);
		List<Stimulus> wide = new ArrayList<>();
		field.queryWide(47, 64, 0, 2.0, 1, wide::add);
		assertEquals(1, wide.size());
	}

	@Test
	void bloodOutlivesSoundSoATrailForms() {
		StimulusField field = new StimulusField();
		field.emit(StimulusType.BLOOD, 0, 64, 0, 10, Stimulus.NO_SOURCE, 0);
		field.emit(StimulusType.BLOOD, 6, 64, 0, 10, Stimulus.NO_SOURCE, 40);
		assertEquals(2, field.size(), "drops 6 blocks apart stay separate");
		field.sweep(StimulusType.SOUND.lifetimeTicks());
		assertEquals(2, field.size(), "still there long after a noise would be gone");
		field.sweep(StimulusType.BLOOD.lifetimeTicks() + 40);
		assertTrue(field.isEmpty());
	}
}
