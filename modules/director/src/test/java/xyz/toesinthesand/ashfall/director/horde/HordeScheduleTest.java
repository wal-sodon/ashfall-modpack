package xyz.toesinthesand.ashfall.director.horde;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.director.haven.HordeSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Our horde-night schedule: when, how hard, who, and the breach days. */
class HordeScheduleTest {
	private static final Identifier WALKER = Identifier.fromNamespaceAndPath("ashfall", "walker");
	private static final Identifier RUNNER = Identifier.fromNamespaceAndPath("ashfall", "runner");
	private static final Identifier ARMORED = Identifier.fromNamespaceAndPath("ashfall", "armored");

	private static HordeSchedule schedule(int every, int jitter, int first) {
		HordeSchedule d = HordeSchedule.DEFAULT;
		return new HordeSchedule(every, jitter, first, 12_000, 23_000,
				List.of(new HordeSchedule.DayTier(1, 1), new HordeSchedule.DayTier(6, 2), new HordeSchedule.DayTier(11, 3)),
				List.of(new HordeSchedule.Unlock(1, WALKER), new HordeSchedule.Unlock(3, RUNNER), new HordeSchedule.Unlock(7, ARMORED)),
				d.tiers(), d.spawn(), new HordeSchedule.Breach(7, 7, 150));
	}

	@Test
	void bedsRefuseFromSunsetToDawnOnAHordeDayOnly() {
		HordeSchedule s = schedule(5, 0, 5);
		long day5 = 4 * 24_000L, day6 = 5 * 24_000L;
		assertFalse(s.noSleepAt(day5 + 11_000, 1L), "afternoon of a horde day");
		assertTrue(s.noSleepAt(day5 + 12_600, 1L), "dusk, when beds start working, before the waves");
		assertTrue(s.noSleepAt(day5 + 18_000, 1L), "midnight");
		assertFalse(s.noSleepAt(day6 + 18_000, 1L), "an ordinary night");
	}

	@Test
	void withoutJitterHordesFallEveryNNights() {
		HordeSchedule s = schedule(5, 0, 5);
		for (int day = 1; day <= 40; day++) {
			assertEquals(day >= 5 && (day - 5) % 5 == 0, s.isHordeDay(day, 1234L), "day " + day);
		}
		assertEquals(5, s.nextHordeDay(1, 1234L));
		assertEquals(10, s.nextHordeDay(6, 1234L));
		assertEquals(10, s.nextHordeDay(10, 1234L), "today counts");
	}

	@Test
	void jitterStaysInItsWindowKeepsOrderAndNeverComesEarlyTheFirstTime() {
		HordeSchedule s = schedule(5, 1, 5);
		for (long seed = 0; seed < 200; seed++) {
			int previous = 0;
			for (int k = 0; k < 30; k++) {
				int day = s.hordeDay(k, seed);
				int slot = 5 + 5 * k;
				assertTrue(Math.abs(day - slot) <= 1, "within one night of its slot");
				assertTrue(day > previous, "strictly later than the previous horde night");
				previous = day;
			}
			assertTrue(s.hordeDay(0, seed) >= 5, "the grace nights always hold");
		}
	}

	@Test
	void jitterIsClampedBelowHalfTheInterval() {
		HordeSchedule s = schedule(4, 9, 4);
		assertEquals(1, s.effectiveJitter(), "an interval of 4 allows at most one night of jitter");
		for (long seed = 0; seed < 100; seed++) {
			for (int k = 1; k < 20; k++) {
				assertTrue(s.hordeDay(k, seed) > s.hordeDay(k - 1, seed));
			}
		}
	}

	@Test
	void isHordeDayAndNextHordeDayAgree() {
		HordeSchedule s = schedule(5, 1, 5);
		for (long seed : new long[]{0L, 42L, -7L, Long.MAX_VALUE}) {
			for (int day = 1; day <= 120; day++) {
				int next = s.nextHordeDay(day, seed);
				assertTrue(next >= day);
				assertTrue(s.isHordeDay(next, seed), "the next horde day is a horde day");
				for (int between = day; between < next; between++) {
					assertFalse(s.isHordeDay(between, seed), "nothing in between");
				}
			}
		}
	}

	@Test
	void theScheduleIsFixedPerWorldAndDiffersBetweenWorlds() {
		HordeSchedule s = schedule(5, 1, 5);
		assertEquals(s.hordeDay(7, 99L), s.hordeDay(7, 99L), "same seed, same night");
		boolean differs = false;
		for (int k = 0; k < 20 && !differs; k++) {
			differs = s.hordeDay(k, 1L) != s.hordeDay(k, 2L);
		}
		assertTrue(differs, "another world gets another schedule");
	}

	@Test
	void disabledScheduleHasNoHordes() {
		HordeSchedule s = schedule(0, 1, 5);
		assertFalse(s.isHordeDay(5, 1L));
		assertEquals(Integer.MAX_VALUE, s.nextHordeDay(1, 1L));
		assertEquals(HordeSnapshot.NONE, ScheduledHordeSource.snapshot(s, 5 * 24_000L, 1L));
	}

	@Test
	void tierIsTheMaxOfDayTierAndGridTier() {
		HordeSchedule s = schedule(5, 0, 5);
		assertEquals(1, s.dayTier(5));
		assertEquals(2, s.dayTier(6));
		assertEquals(3, s.dayTier(30));
		assertEquals(1, s.tier(3, 0), "no grid: the day tier");
		assertEquals(3, s.tier(3, 3), "a lit city outranks an early day");
		assertEquals(3, s.tier(12, 2), "a late day outranks a small grid");
	}

	@Test
	void classesUnlockByDayAndEachWaveKeepsItsOwnTable() {
		HordeSchedule s = schedule(5, 0, 5);
		HordeSchedule.Wave wave = new HordeSchedule.Wave(6, 0, Map.of(WALKER, 6, RUNNER, 3, ARMORED, 1),
				Optional.of(Identifier.fromNamespaceAndPath("ashfall", "horde/wave_common")));
		assertEquals(Map.of(WALKER, 6), s.pool(wave, 1), "day 1: walkers only");
		assertEquals(Map.of(WALKER, 6, RUNNER, 3), s.pool(wave, 3), "runners from day 3");
		assertEquals(Map.of(WALKER, 6, RUNNER, 3, ARMORED, 1), s.pool(wave, 7), "armored from day 7");
		Identifier unlisted = Identifier.fromNamespaceAndPath("ashfall", "something_new");
		assertTrue(s.isUnlocked(unlisted, 1), "a class no unlock names is there from day 1");
		assertEquals(java.util.Set.of(WALKER, RUNNER), s.unlockedOn(5));
	}

	@Test
	void breachDaysAreWeekly() {
		HordeSchedule s = schedule(5, 0, 5);
		assertFalse(s.isBreachDay(6));
		assertTrue(s.isBreachDay(7));
		assertTrue(s.isBreachDay(14));
		assertFalse(s.isBreachDay(15));
		assertEquals(7, s.nextBreachDay(1));
		assertEquals(14, s.nextBreachDay(8));
		assertEquals(14, s.nextBreachDay(14));
	}

	@Test
	void breachesMoveOffHordeNights() {
		HordeSchedule s = schedule(5, 0, 5);
		assertTrue(s.isHordeDay(20, 0L), "precondition: a horde on day 20");
		assertEquals(-1, s.dueBreachDay(6));
		assertEquals(21, s.dueBreachDay(22), "the day-21 Breach is still due on day 22");
		assertFalse(s.breachNightAllowed(20, 0L), "never on a horde night");
		assertFalse(s.breachNightAllowed(21, 0L), "nor the night after one");
		assertTrue(s.breachNightAllowed(22, 0L), "so it comes on day 22");
		assertTrue(s.breachNightAllowed(14, 0L), "a free night keeps its Breach");
	}

	@Test
	void theSirenSeesTonightAndTheNightWindow() {
		HordeSchedule s = schedule(5, 0, 5);
		long day5 = 4 * 24_000L; // world day 5 starts at clock 96000
		HordeSnapshot morning = ScheduledHordeSource.snapshot(s, day5 + 6000, 1L);
		assertTrue(morning.hordeTonight(), "horde day, before nightfall: tonight");
		assertFalse(morning.hordeNight());
		HordeSnapshot night = ScheduledHordeSource.snapshot(s, day5 + 13_000, 1L);
		assertTrue(night.hordeNight(), "after nightfall the horde night runs");
		assertFalse(ScheduledHordeSource.snapshot(s, day5 + 23_500, 1L).hordeNight(), "over at dawn");
		HordeSnapshot dayBefore = ScheduledHordeSource.snapshot(s, day5 - 24_000 + 6000, 1L);
		assertFalse(dayBefore.hordeTonight(), "the day before is quiet");
		assertEquals(2, dayBefore.daysCounter());
	}

	@Test
	void datapackScheduleParsesWithEveryVariantUnlockedSomewhere() throws IOException {
		Path file = Path.of("src/main/resources/data/ashfall/ashfall/horde/schedule.json");
		HordeSchedule s = HordeSchedule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
				.getOrThrow();
		assertEquals(6, s.everyNights());
		assertNotEquals(0, s.tiers().size());
		for (HordeSchedule.Tier tier : s.tiers()) {
			assertFalse(tier.waves().isEmpty(), "tier " + tier.tier() + " has waves");
			for (HordeSchedule.Wave wave : tier.waves()) {
				assertTrue(wave.lootTable().isPresent(), "every wave has its own drop table");
				for (Identifier variant : wave.spawns().keySet()) {
					assertTrue(Files.exists(Path.of("src/main/resources/data/" + variant.getNamespace() + "/ashfall/zombie_variant/"
							+ variant.getPath() + ".json")), "wave names a real variant: " + variant);
				}
				Identifier table = wave.lootTable().get();
				assertTrue(Files.exists(Path.of("src/main/resources/data/" + table.getNamespace() + "/loot_table/" + table.getPath() + ".json")),
						"wave loot table exists: " + table);
			}
		}
		assertEquals(7, s.breach().everyDays(), "weekly breach");
	}

	@Test
	void hordeSettingsParse() throws IOException {
		Path file = Path.of("src/main/resources/data/ashfall/ashfall/horde/hordes.json");
		HordeSettings s = HordeSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file))).getOrThrow();
		assertTrue(s.stimulusScale() > 1.3, "a horde perceives further than one zombie (whose reach caps at 1.3)");
		assertTrue(s.mergeRadius() > s.groupRadius());
	}
}
