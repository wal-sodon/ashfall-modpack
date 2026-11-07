package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * The first names survivors introduce themselves with: short, ordinary and from all over, so a base of six reads as a
 * group of strangers who ended up together. Our own list; nothing taken from another game.
 */
public final class SurvivorNames {
	public static final List<String> NAMES = List.of(
			"Mara", "Dev", "Tomas", "Rae", "Ines", "Wes", "Priya", "Odell", "June", "Kofi",
			"Hollis", "Nadia", "Sam", "Lupe", "Arlo", "Bea", "Cyrus", "Dana", "Emeka", "Fern",
			"Gus", "Hana", "Idris", "Jolene", "Kit", "Lev", "Marisol", "Nate", "Opal", "Reuben",
			"Sunny", "Teo", "Vera", "Wren", "Yusuf", "Zelda");

	private SurvivorNames() {
	}

	public static String pick(RandomSource random) {
		return NAMES.get(random.nextInt(NAMES.size()));
	}
}
