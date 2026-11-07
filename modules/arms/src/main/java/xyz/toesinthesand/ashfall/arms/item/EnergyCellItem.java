package xyz.toesinthesand.ashfall.arms.item;

/**
 * The Energy Cell: the scarce power currency (special-zombie drops, rare loot). It is a 240-unit Team
 * Reborn Energy item that chargers drain into their buffer. A drained cell stays a 0-energy cell, which
 * only a Power Relay can refill.
 * <p>
 * Cells from drops, loot and crafting arrive full: those sources set the energy explicitly, because Team
 * Reborn Energy removes the component at 0 and a non-zero default would make empty cells refill
 * themselves. Cells stack (16), and the energy API divides stored energy across a stack.
 */
public class EnergyCellItem extends CellPackItem {
	public static final long CAPACITY = 240L;

	public EnergyCellItem(Properties properties) {
		super(properties, CAPACITY);
	}
}
