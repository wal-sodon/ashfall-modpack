package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.survivor.SupplyLockerBlockEntity;
import xyz.toesinthesand.ashfall.arms.survivor.SupplyLockers;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorMode;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenState;

import java.util.List;
import java.util.UUID;

/**
 * Survivors, phase 1: one follows its leader and holds still when told to stay; a Guard with a sword kills a zombie
 * and wears the sword; a Supply Locker refuses to place outside a Bunker Core's area; and a Guard made a Settler puts
 * its weapon into the locker in its home claim.
 */
public class SurvivorGameTest {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static Survivor survivor(GameTestHelper helper, BlockPos rel, SurvivorRole role, UUID leader) {
		Survivor survivor = Survivors.spawn(helper.getLevel(), helper.absolutePos(rel), role, leader);
		helper.assertTrue(survivor != null, "survivor spawned");
		return survivor;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	@GameTest(maxTicks = 300)
	public void survivorFollowsLeaderThenStays(GameTestHelper helper) {
		floor(helper);
		ServerPlayer leader = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(new BlockPos(6, 1, 6));
		leader.snapTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		Survivor survivor = survivor(helper, new BlockPos(1, 1, 1), SurvivorRole.SETTLER, leader.getUUID());
		helper.assertValueEqual(survivor.mode(), SurvivorMode.FOLLOW, "a survivor spawned with a leader follows");
		BlockPos[] stayed = new BlockPos[1];
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(survivor.distanceTo(leader) < 3.5,
						"survivor at " + survivor.position() + " did not reach its leader at " + leader.position()))
				.thenExecute(() -> {
					survivor.setMode(SurvivorMode.STAY);
					stayed[0] = survivor.blockPosition();
					BlockPos back = helper.absolutePos(new BlockPos(1, 1, 1));
					leader.snapTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
				})
				.thenExecuteAfter(80, () -> {
					helper.assertTrue(survivor.blockPosition().closerThan(stayed[0], 1.5),
							"a staying survivor walked from " + stayed[0] + " to " + survivor.blockPosition());
					survivor.discard();
					remove(leader);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 500)
	public void guardKillsZombie(GameTestHelper helper) {
		floor(helper);
		Survivor guard = survivor(helper, new BlockPos(1, 1, 1), SurvivorRole.GUARD, null);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		guard.equipWeapon(sword);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(5.5, 1, 5.5));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the test's daylight
		helper.succeedWhen(() -> {
			helper.assertTrue(!zombie.isAlive(), "zombie still alive with " + zombie.getHealth() + " health");
			helper.assertTrue(zombie.getLastHurtByMob() == guard, "the guard did not land the hits");
			helper.assertTrue(guard.getMainHandItem().getDamageValue() > 0, "the guard's sword did not wear");
			guard.discard();
		});
	}

	@GameTest
	public void supplyLockerRefusedOutsideBunker(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Survivors.SUPPLY_LOCKER_ITEM));
			InteractionResult result = Survivors.SUPPLY_LOCKER_ITEM.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false)));
			helper.assertValueEqual(result, InteractionResult.FAIL, "placing outside a Bunker Core area");
			helper.assertBlockNotPresent(Survivors.SUPPLY_LOCKER, new BlockPos(2, 1, 2));
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void roleChangeReturnsWeaponToLocker(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		UUID owner = UUID.randomUUID();
		BlockPos core = helper.absolutePos(new BlockPos(0, 1, 0));
		// The record without its OPAC claim or a region rebuild: the survivor finds lockers through the saved core's area,
		// and a radius of 1 chunk holds the whole test even when it straddles a chunk border.
		HavenState.get(server).putCore(new BunkerCoreRecord(owner, owner, Level.OVERWORLD, core, 1, List.of()));
		try {
			helper.setBlock(new BlockPos(4, 1, 4), Survivors.SUPPLY_LOCKER);
			SupplyLockerBlockEntity locker = helper.getBlockEntity(new BlockPos(4, 1, 4), SupplyLockerBlockEntity.class);
			Survivor guard = survivor(helper, new BlockPos(2, 1, 2), SurvivorRole.GUARD, null);
			guard.setHome(core);
			guard.setMode(SurvivorMode.HOME);
			guard.equipWeapon(new ItemStack(Items.IRON_SWORD));

			SupplyLockers.Stored where = guard.changeRole(SurvivorRole.SETTLER);
			helper.assertValueEqual(where, SupplyLockers.Stored.LOCKER, "where the old guard's weapon went");
			helper.assertTrue(guard.getMainHandItem().isEmpty(), "the settler still holds the weapon");
			boolean found = false;
			for (int i = 0; i < locker.getContainerSize(); i++) {
				found |= locker.getItem(i).is(Items.IRON_SWORD);
			}
			helper.assertTrue(found, "the sword is not in the Supply Locker");
			helper.assertValueEqual(guard.role(), SurvivorRole.SETTLER, "role after the change");
			guard.discard();
		} finally {
			HavenState.get(server).removeCore(owner);
		}
		helper.succeed();
	}
}
