package xyz.toesinthesand.ashfall.salvage.site;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteTemplateTest {
	private static final Path TEMPLATES = Path.of("src/main/resources/data/ashfall/ashfall/site_template");
	/** Light sources the Director's light sense would turn into zombie beacons; templates must not use them. */
	private static final List<String> GLOWING = List.of("glowstone", "lantern", "torch", "sea_lantern", "shroomlight",
			"crying_obsidian", "magma_block", "amethyst_cluster", "_bud", "froglight", "end_rod", "campfire", "lamp", "candle");

	private static SiteTemplate parse(String json) {
		return SiteTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test
	void cellsSkipSpacesAndMarkersAreFound() {
		SiteTemplate t = parse("""
				{"palette": {"#": "minecraft:stone", "B": "minecraft:reinforced_deepslate"},
				 "sink": 1,
				 "layers": [["# ", " B"], ["  ", "# "]],
				 "markers": {"B": "boss"}}""");
		assertEquals(2, t.width());
		assertEquals(2, t.depth());
		assertEquals(2, t.height());
		assertEquals(3, t.cells().size());
		var markers = t.markerCells();
		assertEquals(1, markers.size());
		assertEquals("boss", markers.getFirst().getKey());
		assertEquals(new SiteTemplate.Cell(1, 0, 1, 'B'), markers.getFirst().getValue());
	}

	@Test
	void raggedOrUnknownCharactersAreRejected() {
		var ragged = SiteTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"palette\": {\"#\": \"minecraft:stone\"}, \"layers\": [[\"##\", \"#\"]]}"));
		assertTrue(ragged.error().isPresent());
		var unknown = SiteTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"{\"palette\": {\"#\": \"minecraft:stone\"}, \"layers\": [[\"#x\"]]}"));
		assertTrue(unknown.error().isPresent());
	}

	@Test
	void shippedTemplatesParseAndFollowTheRules() throws IOException {
		Map<String, SiteTemplate> templates;
		try (var files = Files.list(TEMPLATES)) {
			templates = files.collect(Collectors.toMap(p -> p.getFileName().toString().replace(".json", ""), p -> {
				try {
					return parse(Files.readString(p));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}));
		}
		assertTrue(templates.containsKey("wreck_hull"));
		for (Map.Entry<String, SiteTemplate> e : templates.entrySet()) {
			SiteTemplate t = e.getValue();
			for (String state : t.palette().values()) {
				for (String glow : GLOWING) {
					assertTrue(!state.contains(glow), e.getKey() + " uses a light source: " + state);
				}
			}
		}
		// Every crash site has exactly one loot chest; the hull has exactly one boss marker and a sealed hatch.
		for (String crash : List.of("crash_site_small", "crash_site_pod", "crash_site_debris")) {
			SiteTemplate t = templates.get(crash);
			long chests = t.cells().stream().filter(c -> t.loot().containsKey(String.valueOf(c.symbol()))).count();
			assertEquals(1, chests, crash + " chests");
			assertTrue(t.width() <= 16 && t.depth() <= 16, crash + " should fit a chunk or two");
		}
		SiteTemplate hull = templates.get("wreck_hull");
		assertEquals(1, hull.markerCells().stream().filter(m -> m.getKey().equals("boss")).count());
		long doors = hull.cells().stream().filter(c -> hull.palette().get(String.valueOf(c.symbol())).startsWith("minecraft:iron_door")).count();
		assertEquals(4, doors, "the hatch is two iron doors (four halves)");
	}
}
