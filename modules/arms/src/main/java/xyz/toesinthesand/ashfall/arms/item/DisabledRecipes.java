package xyz.toesinthesand.ashfall.arms.item;

import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Other mods' recipes the pack switches off (see {@code RecipeMapMixin}).
 * <p>
 * Traveler's Backpacks are loot-only: the four recipes that make the Standard backpack from scratch are gone, so
 * every big bag starts as a find. Its upgrades and themed versions are crafted from a Standard backpack, so they
 * still work once a player has one. The craftable small bag is the Satchel.
 */
public final class DisabledRecipes {
	private static final Set<Identifier> DISABLED = Set.of(
			Identifier.fromNamespaceAndPath("travelersbackpack", "standard"),
			Identifier.fromNamespaceAndPath("travelersbackpack", "standard_no_sleeping_bag"),
			Identifier.fromNamespaceAndPath("travelersbackpack", "standard_no_tanks"),
			Identifier.fromNamespaceAndPath("travelersbackpack", "standard_no_tanks_sleeping_bag"));

	private DisabledRecipes() {
	}

	public static boolean isDisabled(Identifier id) {
		return DISABLED.contains(id);
	}
}
