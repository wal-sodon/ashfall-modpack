package xyz.toesinthesand.ashfall.salvage.loot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards every Ashfall loot table against the pre-26.3 key names.
 * <p>
 * 26.3 renamed {@code functions} to {@code modifier} and {@code conditions} to a single {@code condition}
 * (several are combined with {@code minecraft:all_of}), and each entry names its kind with {@code type}
 * instead of {@code function} / {@code condition}. The codec ignores unknown keys silently, so an
 * old-style table still loads but drops its conditions and functions: boss cells arrived empty and
 * player-kill-only drops paid out to anything. Scans all three modules from the repository root.
 */
class LootTableFormatTest {
	@Test
	void noLootTableUsesPre263Keys() throws IOException {
		Path modules = Path.of("").toAbsolutePath().getParent();
		List<Path> offenders;
		try (Stream<Path> files = Files.walk(modules)) {
			offenders = files
					.filter(p -> p.toString().endsWith(".json"))
					.filter(p -> p.toString().contains("/src/") && p.toString().contains("/loot_table/"))
					.filter(p -> {
						try {
							String json = Files.readString(p);
							return json.contains("\"functions\"") || json.contains("\"conditions\"")
									|| json.contains("\"function\":") || json.matches("(?s).*\"condition\":\\s*\".*");
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					})
					.toList();
		}
		assertTrue(offenders.isEmpty(), "loot tables with pre-26.3 keys: " + offenders);
	}
}
