package xyz.toesinthesand.ashfall.arms.gun;

import java.util.Locale;

/** Parses and prints the {@code #rrggbb} colours used for bolt colours in gun stat files. */
public final class ColorHex {
	private ColorHex() {
	}

	/** Parses {@code #rrggbb} (the hash is optional) to {@code 0xRRGGBB}; throws on anything else. */
	public static int parse(String text) {
		String hex = text.startsWith("#") ? text.substring(1) : text;
		if (hex.length() != 6) {
			throw new IllegalArgumentException("expected #rrggbb, got " + text);
		}
		return Integer.parseInt(hex, 16);
	}

	public static String format(int rgb) {
		return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
	}
}
