package xyz.toesinthesand.ashfall.arms;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The singleplayer world-reopen guard. A singleplayer client runs every world it opens in the same JVM, so static
 * state of world A is still there when world B starts unless something clears it. A gametest cannot stop and restart
 * its own server, so this test reads the source instead: every static mutable collection in the three mods must
 * either be cleared in a {@code SERVER_STOPPED} handler (in its own file, or in the file named below) or be listed
 * as not holding world state, with the reason. A new static map that forgets the reset fails the build.
 */
class StaticStateAuditTest {
	/** Static collection fields matched by the scan: {@code static final Map<..> NAME = new HashMap<>()} and friends. */
	private static final Pattern FIELD = Pattern.compile(
			"static\\s+final\\s+[\\w<>,.?\\s\\[\\]]+?\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*new\\s+(?:[\\w]+\\.)*"
					+ "(?:\\w*(?:Hash|Tree|Linked|Enum|Concurrent|CopyOnWrite|Array|Weak|Identity)\\w*(?:Map|Set|List|Queue|Deque)|ProtectionTimers)[<(]");

	/** Fields that hold no world state, keyed {@code Class.FIELD}, with why that is safe across worlds. */
	private static final Map<String, String> NOT_WORLD_STATE = Map.ofEntries(
			Map.entry("ArmsNoise.LISTENERS", "listeners registered once at mod init"),
			Map.entry("ItemLight.SOURCES", "light sources registered once at mod init"),
			Map.entry("WeaponWeight.ASSIGNED", "weapon weight classes assigned once at mod init"),
			Map.entry("SpawnDirector.DARKNESS_MODIFIERS", "modifiers registered once at mod init"),
			Map.entry("StoryHooks.RESET_LISTENERS", "listeners registered once at mod init"),
			Map.entry("SpecialSounds.AMBIENT", "sound registry entries, fixed at mod init"),
			Map.entry("SpecialSounds.HURT", "sound registry entries, fixed at mod init"),
			Map.entry("SpecialSounds.DEATH", "sound registry entries, fixed at mod init"),
			Map.entry("SpecialEntities.BY_KIND", "entity type registry entries, fixed at mod init"),
			Map.entry("Sites.RESOLVED", "template palettes resolved to block states, cleared on every datapack (re)load"),
			Map.entry("BladeLogic.SWINGS", "keyed by player and game tick, removed on disconnect (every player disconnects on stop)"),
			Map.entry("EndingProfiles.TEST", "gametest-only overrides, removed by the tests that add them"),
			Map.entry("GridDistricts.TESTS", "gametest-only districts, removed by the tests that add them"),
			Map.entry("BossLairs.TEST_LAIRS", "gametest-only lairs, removed by the tests that add them"));

	/** Fields cleared on SERVER_STOPPED from another file: {@code Class.FIELD} -> {file name, the clearing call}. */
	private static final Map<String, String[]> CLEARED_ELSEWHERE = Map.of(
			"DirectorTracker.LOADED", new String[]{"SpawnDirector.java", "DirectorTracker.clear()"},
			"BuildingPopulations.PLANS", new String[]{"SpawnDirector.java", "BuildingPopulations.clearCache()"},
			"PresenceTracker.BUILDING_RULES", new String[]{"AshfallSalvage.java", "PresenceTracker.reset()"});

	@Test
	void everyStaticCollectionIsResetBetweenWorlds() throws IOException {
		Path modules = Path.of("..").toAbsolutePath().normalize();
		List<Path> sources = new ArrayList<>();
		for (String module : List.of("salvage", "director", "arms")) {
			Path root = modules.resolve(module).resolve("src/main/java");
			assertTrue(Files.isDirectory(root), "missing sources: " + root);
			try (Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
			}
		}

		List<String> problems = new ArrayList<>();
		int seen = 0;
		for (Path file : sources) {
			String text = Files.readString(file);
			String cls = file.getFileName().toString().replace(".java", "");
			Matcher m = FIELD.matcher(text);
			while (m.find()) {
				seen++;
				String key = cls + "." + m.group(1);
				if (NOT_WORLD_STATE.containsKey(key)) {
					continue;
				}
				String[] elsewhere = CLEARED_ELSEWHERE.get(key);
				if (elsewhere != null) {
					if (!clearedOnStop(sources, elsewhere[0], elsewhere[1])) {
						problems.add(key + ": expected " + elsewhere[0] + " to call " + elsewhere[1] + " on SERVER_STOPPED");
					}
					continue;
				}
				String field = m.group(1);
				// Its own file registers a stop handler and clears the field (directly, or in a clear() it calls).
				boolean cleared = text.contains("SERVER_STOPPED") && text.contains(field + ".clear()");
				if (!cleared) {
					problems.add(key + " (" + modules.relativize(file) + ") is static mutable state that is never cleared on "
							+ "SERVER_STOPPED: world A's entries would leak into world B in singleplayer");
				}
			}
		}
		assertTrue(seen >= 30, "the scan found only " + seen + " static collections; the pattern no longer matches the code");
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	private static boolean clearedOnStop(List<Path> sources, String fileName, String call) throws IOException {
		for (Path p : sources) {
			if (p.getFileName().toString().equals(fileName)) {
				String text = Files.readString(p);
				int stop = text.indexOf("SERVER_STOPPED");
				return stop >= 0 && text.indexOf(call, stop) > stop;
			}
		}
		return false;
	}
}
