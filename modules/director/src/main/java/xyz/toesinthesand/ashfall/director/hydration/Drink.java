package xyz.toesinthesand.ashfall.director.hydration;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jetbrains.annotations.Nullable;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/**
 * What a drinkable stack is worth in thirst. Clean water is scarce loot or the result of boiling;
 * dirty water is what a bottle and a river give you, and it can make you sick. The vanilla water
 * bottle counts as dirty so cauldrons and old stock are no loophole around the risk.
 */
public enum Drink {
	CLEAN(8, 4.0f, false),
	DIRTY(5, 1.0f, true);

	private final int thirst;
	private final float saturation;
	private final boolean dirty;

	Drink(int thirst, float saturation, boolean dirty) {
		this.thirst = thirst;
		this.saturation = saturation;
		this.dirty = dirty;
	}

	public int thirst() {
		return thirst;
	}

	public float saturation() {
		return saturation;
	}

	/** Dirty drinks roll for nausea and infection. */
	public boolean dirty() {
		return dirty;
	}

	/** The drink a stack counts as, or null when it does not quench thirst. */
	public static @Nullable Drink of(ItemStack stack) {
		if (stack.is(SalvageItems.BOTTLED_WATER)) {
			return CLEAN;
		}
		if (stack.is(SalvageItems.DIRTY_WATER)) {
			return DIRTY;
		}
		if (stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
			return DIRTY;
		}
		return null;
	}
}
