package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.GunAttachments;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;

import java.util.List;

/**
 * Powered weapons in mob hands: a zombie (one that picked up a dropped blaster, or any other mob) can hold
 * a loaded gun but never fire it, and drops it intact when killed.
 */
public class ArmsMobGunTest {
	private static ItemStack loadedGun(Item gun, Item pack, long energy) {
		ItemStack stack = new ItemStack(gun);
		stack.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(EnergyItems.withEnergy(new ItemStack(pack), energy)));
		return stack;
	}

	private static long loadedEnergy(ItemStack gun) {
		ItemStackTemplate pack = gun.get(ArmsComponents.LOADED_PACK);
		return pack == null ? -1 : EnergyItems.stored(pack);
	}

	@GameTest
	public void nonPlayersCannotFire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 2, 1.5));
		try {
			ItemStack pistol = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, pistol);
			helper.assertValueEqual(GunLogic.tryFire(level, zombie, pistol), GunLogic.FireResult.NOT_A_PLAYER, "zombie trigger pull");
			helper.assertValueEqual(loadedEnergy(pistol), 30L, "a refused shot spends nothing");
			helper.assertTrue(GunLogic.FireResult.NOT_A_PLAYER.stopsAutoFire(), "a refused shot ends automatic fire");

			ItemStack carbine = loadedGun(ArmsItems.PULSE_CARBINE, ArmsItems.CELL_PACK_STANDARD, 60);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, carbine);
			zombie.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
			ArmsItems.PULSE_CARBINE.onUseTick(level, zombie, carbine, 100);
			helper.assertValueEqual(loadedEnergy(carbine), 60L, "a held automatic trigger spends nothing");
			helper.assertTrue(!zombie.isUsingItem(), "a mob holding the trigger is made to let go");
			helper.assertTrue(helper.getEntities(ArmsEntities.ENERGY_BOLT).isEmpty(), "no bolt from a mob");
		} finally {
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void mobsDropHeldGunsIntact(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(2.5, 2, 2.5));
		ItemStack gun = loadedGun(ArmsItems.VOLT_SIDEARM, ArmsItems.CELL_PACK_SMALL, 17);
		gun.set(ArmsComponents.ATTACHMENTS, GunAttachments.NONE.with(GunAttachments.DAMPENER));
		ItemStack original = gun.copy();
		zombie.setItemSlot(EquipmentSlot.MAINHAND, gun);
		// vanilla would only drop the hand item 8.5% of the time, and possibly damaged
		zombie.setDropChance(EquipmentSlot.MAINHAND, 0f);
		zombie.kill(level);

		AABB around = zombie.getBoundingBox().inflate(3);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, around,
				e -> e.getItem().is(ArmsItems.VOLT_SIDEARM));
		try {
			helper.assertValueEqual(drops.size(), 1, "exactly one gun drop");
			ItemStack dropped = drops.getFirst().getItem();
			helper.assertTrue(ItemStack.isSameItemSameComponents(dropped, original),
					"the gun drops exactly as it was held: " + dropped.getComponentsPatch() + " vs " + original.getComponentsPatch());
			helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "the hand is emptied");
		} finally {
			drops.forEach(ItemEntity::discard);
		}
		helper.succeed();
	}
}
