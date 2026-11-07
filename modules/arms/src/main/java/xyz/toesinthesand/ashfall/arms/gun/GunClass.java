package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.world.item.Item;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

import java.util.function.Supplier;

/**
 * Weapon class: decides which Cell Pack a gun accepts (pistols take small, rifles standard, heavies
 * heavy). Packs are deliberately not interchangeable so each class has its own ammo economy.
 */
public enum GunClass {
	PISTOL(() -> ArmsItems.CELL_PACK_SMALL),
	RIFLE(() -> ArmsItems.CELL_PACK_STANDARD),
	HEAVY(() -> ArmsItems.CELL_PACK_HEAVY);

	private final Supplier<Item> pack;

	GunClass(Supplier<Item> pack) {
		this.pack = pack;
	}

	/** The one pack item this class loads (lazy: items register after this enum may load). */
	public Item pack() {
		return pack.get();
	}
}
