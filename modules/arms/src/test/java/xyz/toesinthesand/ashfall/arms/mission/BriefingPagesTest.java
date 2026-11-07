package xyz.toesinthesand.ashfall.arms.mission;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Mission Briefing as shipped: every page rendered from {@code en_us.json} with the numbers from the datapack
 * fits one vanilla book page, every objective has its short text and its how-to, and none of it borrows space-opera
 * vocabulary.
 * <p>
 * A book page is 114 pixels wide and 128 tall ({@code BookViewScreen.TEXT_WIDTH} / {@code TEXT_HEIGHT}), which is 14
 * lines of the 9-pixel font. The wrap below uses the default font's glyph advances (most letters 6 px with spacing,
 * narrow ones less, bold one more) and breaks at spaces like the game does. The client screenshot test checks the
 * same pages against the real font.
 */
class BriefingPagesTest {
	static final int PAGE_WIDTH = 114;
	static final int PAGE_LINES = 14;
	private static final List<String> BANNED = List.of("jedi", "sith", "lightsaber", "kyber", "droid", "holocron", "hyperdrive",
			"the force", "stormtrooper", "wookiee", "death star", "padawan", "rebel alliance", "galactic empire");

	private static JsonObject json(String path) throws IOException {
		try (InputStream in = BriefingPagesTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static JsonObject lang() throws IOException {
		return json("/assets/ashfall/lang/en_us.json");
	}

	/** The numbers the server would quote, read from the shipped data files the same way the settings codecs do. */
	static BriefingPages.Numbers shippedNumbers() throws IOException {
		JsonObject grid = json("/data/ashfall/ashfall/grid/settings.json");
		JsonObject finale = json("/data/ashfall/ashfall/finale/settings.json");
		JsonObject mission = json("/data/ashfall/ashfall/mission/settings.json");
		float darkness = grid.getAsJsonObject("reclaimed").get("darkness_scale").getAsFloat();
		return new BriefingPages.Numbers(grid.get("couplers_required").getAsInt(), grid.get("cells_required").getAsInt(),
				grid.get("energize_seconds").getAsInt(), grid.get("hold_radius").getAsInt(), grid.get("hum_radius").getAsInt(),
				grid.get("siren_warning_seconds").getAsInt(), xyz.toesinthesand.ashfall.arms.grid.DistrictLoop.gridPercent(4, 0), Math.round((1f - darkness) * 100f), mission.get("crystals_needed").getAsInt(),
				finale.getAsJsonObject("buffer").get("cell_seconds").getAsInt(), finale.getAsJsonObject("breakers").get("hold_seconds").getAsInt());
	}

	/** {@code Component.translatable}'s formatting for the patterns we use: %s, %n$s and %%. */
	static String format(String pattern, List<Object> args) {
		try {
			return String.format(Locale.ROOT, pattern, args.toArray());
		} catch (IllegalFormatException e) {
			fail("bad pattern \"" + pattern + "\": " + e);
			return "";
		}
	}

	/** Advance of one glyph of the default font, spacing included. */
	static int advance(char c, boolean bold) {
		int w = switch (c) {
			case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
			case 'l', '`' -> 3;
			case 'I', 't', '[', ']', ' ' -> 4;
			case 'f', 'k', '<', '>', '"', '(', ')', '{', '}', '*' -> 5;
			case '@', '~' -> 7;
			default -> 6;
		};
		return w + (bold ? 1 : 0);
	}

	/** Lines {@code text} takes when word-wrapped to {@code width} pixels; explicit newlines start new lines. */
	static int wrappedLines(String text, boolean bold, int width) {
		int lines = 0;
		for (String paragraph : text.split("\n", -1)) {
			int current = 0;
			lines++;
			for (String word : paragraph.split(" ")) {
				int w = 0;
				for (char c : word.toCharArray()) {
					w += advance(c, bold);
				}
				int space = current > 0 ? advance(' ', bold) : 0;
				if (current > 0 && current + space + w > width) {
					lines++;
					current = w;
				} else {
					current += space + w;
				}
				// A single word wider than the page is split by the game, one line per page width.
				while (current > width) {
					lines++;
					current -= width;
				}
			}
		}
		return lines;
	}

	@Test
	void everyPageFitsABookPage() throws IOException {
		JsonObject lang = lang();
		List<BriefingPages.Page> pages = BriefingPages.pages(shippedNumbers());
		assertTrue(pages.size() >= 6 && pages.size() <= 100, "page count " + pages.size());
		for (BriefingPages.Page page : pages) {
			assertTrue(lang.has(page.headingKey()), "missing " + page.headingKey());
			assertTrue(lang.has(page.bodyKey()), "missing " + page.bodyKey());
			String heading = lang.get(page.headingKey()).getAsString();
			String body = format(lang.get(page.bodyKey()).getAsString(), page.args());
			// A placeholder without an argument already failed in format() (String.format throws for it).
			// Heading (bold), a blank line, then the body: the layout MissionBriefing.pageComponents builds.
			int lines = wrappedLines(heading, true, PAGE_WIDTH) + 1 + wrappedLines(body, false, PAGE_WIDTH);
			assertTrue(lines <= PAGE_LINES, page.id() + " needs " + lines + " lines, a book page holds " + PAGE_LINES);
		}
	}

	@Test
	void pagesQuoteTheDatapackNumbers() throws IOException {
		BriefingPages.Numbers n = shippedNumbers();
		assertEquals("5", n.holdMinutes(), "300 s reads as 5 minutes");
		assertEquals("2.5", new BriefingPages.Numbers(4, 4, 150, 32, 144, 150, 80, 80, 3, 45, 75).holdMinutes());
		JsonObject lang = lang();
		String energize = format(lang.get("briefing.ashfall.page.energize.body").getAsString(),
				BriefingPages.pages(n).stream().filter(p -> p.id().equals("energize")).findFirst().orElseThrow().args());
		assertTrue(energize.contains(n.cells() + " full Energy Cells") && energize.contains(n.holdRadius() + " blocks"), energize);
	}

	@Test
	void everyObjectiveHasTextAndHowTo() throws IOException {
		JsonObject lang = lang();
		for (String key : MissionResolverTest.allKeys()) {
			assertTrue(lang.has(Objective.PREFIX + key), "missing " + Objective.PREFIX + key);
			assertTrue(lang.has(Objective.PREFIX + key + ".how"), "missing " + Objective.PREFIX + key + ".how");
		}
		for (String row : List.of("radio", "arm", "haven", "district", "crystals", "uplink", "black_start", "choose", "after")) {
			assertTrue(lang.has(Objective.PREFIX + "list." + row), "missing checklist row " + row);
		}
		for (String key : List.of("briefing.ashfall.title", "briefing.ashfall.subtitle", "briefing.ashfall.chat.header",
				"briefing.ashfall.chat.situation", "briefing.ashfall.chat.mission", "briefing.ashfall.chat.first", "briefing.ashfall.link.read",
				"briefing.ashfall.link.objectives", "mission.ashfall.header", "hud.ashfall.objective", "radio.ashfall.home.bunker",
				"radio.ashfall.home.safehouse", "key.ashfall.objective", "screen.ashfall.grid_map.bunker")) {
			assertTrue(lang.has(key), "missing " + key);
		}
		assertTrue(lang.get("advancements.ashfall.relight.root.description").getAsString().contains("/ashfall mission"),
				"the Relight tab's root should point at /ashfall mission");
	}

	@Test
	void briefingStaysOriginal() throws IOException {
		JsonObject lang = lang();
		List<String> texts = new ArrayList<>();
		for (String key : lang.keySet()) {
			if (key.startsWith("briefing.ashfall.") || key.startsWith("objective.ashfall.") || key.startsWith("mission.ashfall.")) {
				texts.add(lang.get(key).getAsString().toLowerCase(Locale.ROOT));
			}
		}
		assertTrue(texts.size() > 60, "found only " + texts.size() + " briefing texts");
		for (String text : texts) {
			for (String word : BANNED) {
				assertFalse(text.contains(word), "\"" + text + "\" uses \"" + word + "\"");
			}
		}
	}
}
