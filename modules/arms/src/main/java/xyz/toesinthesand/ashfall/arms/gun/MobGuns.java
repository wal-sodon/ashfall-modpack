package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Energy guns in mob hands. Mobs never fire them ({@link GunLogic#canFire}); they only carry them, mostly
 * zombies that picked up what a dead player dropped, and must give them back when killed.
 */
public final class MobGuns {
	private MobGuns() {
	}

	/**
	 * Drops every energy gun the mob holds or wears as an exact copy (charge, heat, attachments kept) and
	 * empties the slot so the vanilla equipment drop can't drop or damage it a second time.
	 *
	 * @return how many guns were dropped
	 */
	public static int dropHeldGuns(ServerLevel level, Mob mob) {
		int dropped = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.getItem() instanceof EnergyGunItem) {
				mob.spawnAtLocation(level, stack.copy());
				mob.setItemSlot(slot, ItemStack.EMPTY);
				dropped++;
			}
		}
		return dropped;
	}
}
