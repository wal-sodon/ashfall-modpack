package xyz.toesinthesand.ashfall.arms.gun;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** sounds.json only names audio files that ship, and every shipped file is mono (positional) Ogg Vorbis. */
class SoundAssetsTest {
	private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"ashfall:([a-z0-9_/]+)\"");
	private static final Pattern EVENT = Pattern.compile("^\\t\"([a-z0-9_.]+)\"\\s*:\\s*\\{", Pattern.MULTILINE);

	private static byte[] resource(String path) throws IOException {
		try (InputStream in = SoundAssetsTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing resource " + path);
			return in.readAllBytes();
		}
	}

	@Test
	void everyReferencedSoundShipsAsMonoVorbis() throws IOException {
		String json = new String(resource("/assets/ashfall/sounds.json"), StandardCharsets.UTF_8);
		Matcher m = NAME.matcher(json);
		Set<String> names = new HashSet<>();
		while (m.find()) {
			names.add(m.group(1));
		}
		assertTrue(names.size() >= 30, "expected the sci-fi sound set, found " + names.size() + " files");
		for (String name : names) {
			byte[] ogg = resource("/assets/ashfall/sounds/" + name + ".ogg");
			assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII), name + " is not an Ogg file");
			int header = indexOf(ogg, "\u0001vorbis".getBytes(StandardCharsets.ISO_8859_1));
			assertTrue(header > 0, name + " is not Vorbis");
			assertEquals(1, ogg[header + 11], name + " must be mono, or Minecraft plays it without positioning");
		}
	}

	@Test
	void keepsTheOriginalEventIds() throws IOException {
		String json = new String(resource("/assets/ashfall/sounds.json"), StandardCharsets.UTF_8);
		Matcher m = EVENT.matcher(json);
		Set<String> events = new HashSet<>();
		while (m.find()) {
			events.add(m.group(1));
		}
		for (String id : new String[]{"gun.fire.light", "gun.fire.heavy", "gun.fire.auto", "gun.overheat", "gun.empty",
				"gun.reload", "bolt.impact", "charger.hum", "relay.hum", "gun.fire.pistol", "gun.fire.magnum",
				"gun.fire.carbine", "gun.fire.rifle", "gun.fire.lancer", "gun.fire.storm", "bolt.hit"}) {
			assertTrue(events.contains(id), "sounds.json lacks event " + id);
		}
	}

	private static int indexOf(byte[] data, byte[] needle) {
		outer:
		for (int i = 0; i + needle.length <= data.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
