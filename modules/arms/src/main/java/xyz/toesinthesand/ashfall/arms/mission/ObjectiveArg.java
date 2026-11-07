package xyz.toesinthesand.ashfall.arms.mission;

import net.minecraft.network.chat.Component;

/**
 * One argument of an objective's text. Either a plain value (a count, a percentage) or a translation key (a district
 * name, an ending name), so the server can say "River Arts" without knowing the client's language: the client
 * resolves the key in its own lang file when it draws the line.
 *
 * @param translate true: {@code value} is a translation key
 * @param value     the literal text, or the key
 */
public record ObjectiveArg(boolean translate, String value) {
	public static ObjectiveArg of(int number) {
		return new ObjectiveArg(false, Integer.toString(number));
	}

	public static ObjectiveArg key(String translationKey) {
		return new ObjectiveArg(true, translationKey);
	}

	/** A district's display name ({@code zone.ashfall.<id>}, the same names the radio and the map use). */
	public static ObjectiveArg district(String id) {
		return key("zone.ashfall." + id);
	}

	public Component component() {
		return translate ? Component.translatable(value) : Component.literal(value);
	}
}
