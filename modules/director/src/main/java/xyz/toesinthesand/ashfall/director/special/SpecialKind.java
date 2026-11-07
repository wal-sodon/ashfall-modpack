package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * The six special infected (plan M9). Each is its own entity type extending the vanilla zombie, so every system
 * that already handles zombies (Zombies Break and Build, the senses goal, infection on hit, the zombies-only spawn
 * rule, the Director's hordes) treats them as zombies with no extra code.
 * <p>
 * The kind's name is also its entity id path, its {@code special_infected/<name>.json} file and the Director
 * variant name its spawns are tagged with ({@code ashfall_variant=bloated}), which is how the infection table
 * gives a special its own bite chance.
 */
public enum SpecialKind {
	/** Low and fast; fits under one-block gaps. */
	CRAWLER,
	/** Swollen; bursts into an infectious cloud when it dies. */
	BLOATED,
	/** Thin; shrieks when it finds prey, pulling every zombie in earshot and speeding up the ones nearby. */
	SCREAMER,
	/** Spits acid at range: poison plus an infection chance. */
	SPITTER,
	/** Huge and tough; breaks ordinary blocks faster under ZB&B but still not fortified ones. */
	BRUTE,
	/** Vanishes while it is unseen in the dark, then ambushes. */
	STALKER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Identifier key() {
		return Identifier.fromNamespaceAndPath("ashfall", id());
	}

	/** The kind with this id ({@code "brute"}), or null. */
	public static SpecialKind byId(String id) {
		for (SpecialKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
