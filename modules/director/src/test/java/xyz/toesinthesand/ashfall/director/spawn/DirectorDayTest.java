package xyz.toesinthesand.ashfall.director.spawn;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One day clock for all difficulty: the Director's day is the horde schedule's day, and no main source in any
 * module asks for "today" from elapsed game time any more.
 * <p>
 * Why a source guard: the bug this replaces (ambient caps, specials and building populations a week behind horde
 * nights in a world that sleeps) came from a one-line {@code SpawnRules.day(level.getGameTime())} at each call site.
 * Sleeping cannot be simulated in JUnit, and a gametest world never sleeps, so the cheapest lasting check is that the
 * pattern never comes back.
 */
class DirectorDayTest {
	@Test
	void theDirectorDayIsTheHordeDay() {
		for (long clock : new long[]{0, 23_999, 24_000, 258_000, 10L * 24_000 + 12_000}) {
			assertEquals(HordeSchedule.day(clock), DirectorDay.fromClock(clock), "clock " + clock);
		}
		assertEquals(11, DirectorDay.fromClock(258_000), "the playtests' clock day 11 at midnight");
	}

	@Test
	void theAmbientCapFollowsNightsSurvived() {
		// Clock day 11 with game time still on day 1 (ten nights slept through): the cap is day 11's, not day 1's.
		int slept = SpawnRules.ambientCap(DirectorDay.fromClock(258_000));
		assertEquals(SpawnRules.ambientCap(11), slept);
		assertTrue(slept > SpawnRules.ambientCap(1), "ten nights slept still raise the ambient cap");
	}

	@Test
	void noMainSourceTakesTheDayFromGameTime() throws IOException {
		Path modules = Path.of("").toAbsolutePath().getParent();
		Pattern gameTimeDay = Pattern.compile("SpawnRules\\.day\\([^;]*getGameTime\\(\\)");
		List<Path> offenders;
		try (Stream<Path> files = Files.walk(modules)) {
			offenders = files
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> p.toString().contains("/src/main/") || p.toString().contains("/src/gametest/"))
					.filter(p -> {
						try {
							return gameTimeDay.matcher(Files.readString(p)).find();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					})
					.toList();
		}
		assertTrue(offenders.isEmpty(), "sources taking today from game time instead of DirectorDay: " + offenders);
	}
}
