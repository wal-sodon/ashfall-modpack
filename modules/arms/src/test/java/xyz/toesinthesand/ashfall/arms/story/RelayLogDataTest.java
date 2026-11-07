package xyz.toesinthesand.ashfall.arms.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped Relay Log texts: thirty numbered logs with no gaps, four site documents, pages short enough for a
 * book page, and none of the borrowed space-opera vocabulary the story is meant to stay clear of.
 */
class RelayLogDataTest {
	private static final String ROOT = "/data/ashfall/ashfall/relay_logs/";
	/** Longest page we ship; a vanilla book page holds about 14 lines of 19 characters. */
	private static final int MAX_PAGE = 250;
	private static final List<String> BANNED = List.of("jedi", "sith", "lightsaber", "blaster", "kyber", "droid", "holocron",
			"hyperdrive", "the force", "stormtrooper", "wookiee", "death star");

	private static JsonObject read(String path) throws IOException {
		try (InputStream in = RelayLogDataTest.class.getResourceAsStream(ROOT + path)) {
			assertNotNull(in, "missing " + ROOT + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void checkPages(String name, JsonObject log) {
		JsonArray pages = log.getAsJsonArray("pages");
		assertTrue(pages.size() >= 1 && pages.size() <= 20, name + " page count " + pages.size());
		StringBuilder all = new StringBuilder(log.get("title").getAsString()).append(' ');
		for (JsonElement page : pages) {
			String text = page.getAsString();
			assertTrue(text.length() <= MAX_PAGE, name + " has a " + text.length() + "-character page");
			all.append(text).append(' ');
		}
		String lower = all.toString().toLowerCase(Locale.ROOT);
		for (String word : BANNED) {
			assertFalse(lower.contains(word), name + " uses \"" + word + "\"");
		}
	}

	@Test
	void thirtyNumberedLogs() throws IOException {
		for (int n = 1; n <= 30; n++) {
			String name = String.format(Locale.ROOT, "log_%02d.json", n);
			JsonObject log = read(name);
			assertEquals(n, log.get("number").getAsInt(), name + " number");
			assertTrue(log.get("title").getAsString().length() <= 40, name + " title too long");
			checkPages(name, log);
		}
	}

	/**
	 * The three landmark documents plus the Survivor's Notes, the new player's primer in the safehouse radio cache.
	 * The primer goes through the same checks: it is a book page like any other, and it must not join the relay pool,
	 * or a relay could hand out a beginner's guide as story.
	 */
	@Test
	void siteDocuments() throws IOException {
		for (String site : List.of("warehouse", "hospital", "hull", "survivors_notes")) {
			JsonObject doc = read("site/" + site + ".json");
			assertFalse(doc.has("number"), site + " must not join the relay pool");
			// The title becomes the book title, which vanilla caps at 32 characters.
			assertTrue(doc.get("title").getAsString().length() <= 32, site + " title too long");
			checkPages("site/" + site, doc);
		}
	}
}
