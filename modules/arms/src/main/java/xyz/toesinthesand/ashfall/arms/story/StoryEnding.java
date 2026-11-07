package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * Which ending the world is in (endgame-design section 4, "the choice"): none yet, Sever (the rebuild ending) or Bind
 * (the veteran ending). Stored by its lower-case id so the save stays readable.
 */
public enum StoryEnding {
	NONE,
	SEVER,
	BIND;

	public static final Codec<StoryEnding> CODEC = Codec.STRING.xmap(StoryEnding::byId, StoryEnding::id);

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The ending for {@code id}; NONE for anything unknown, so a damaged save falls back to "no ending yet". */
	public static StoryEnding byId(String id) {
		for (StoryEnding e : values()) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return NONE;
	}

	/** Whether this is a real ending (SEVER or BIND). */
	public boolean chosen() {
		return this != NONE;
	}

	/** The translation key of the ending's name. */
	public String translationKey() {
		return "ending.ashfall." + id();
	}
}
