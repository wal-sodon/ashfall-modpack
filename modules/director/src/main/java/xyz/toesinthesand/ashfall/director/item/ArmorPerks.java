package xyz.toesinthesand.ashfall.director.item;

/** The armour-set perk maths, kept free of Minecraft types so a unit test can check it. */
public final class ArmorPerks {
	private ArmorPerks() {
	}

	/** {@code 1 - perPiece * pieces}, never below 0: the multiplier a set perk applies to a chance or radius. */
	public static float factor(int pieces, float perPiece) {
		return Math.max(0f, 1f - perPiece * pieces);
	}
}
