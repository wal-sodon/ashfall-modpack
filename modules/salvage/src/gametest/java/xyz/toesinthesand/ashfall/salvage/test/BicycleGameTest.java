package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.salvage.atlas.StreetProps;
import xyz.toesinthesand.ashfall.salvage.bike.Bicycle;
import xyz.toesinthesand.ashfall.salvage.bike.Bikes;

/** Bicycles: a player gets on with a use and becomes the rider; sneak-use puts the bike in their inventory. */
public class BicycleGameTest {
	@GameTest
	public void rideThenPickUp(GameTestHelper helper) {
		helper.assertTrue(StreetProps.spawnBike(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 90f), "a street bike spawns");
		Bicycle bike = helper.spawn(Bikes.BICYCLE, new Vec3(3.5, 1, 3.5));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		bike.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.assertTrue(player.getVehicle() == bike, "use mounts the bike");
		helper.assertTrue(bike.getControllingPassenger() == player, "the rider steers, no saddle needed");
		player.stopRiding();
		player.setShiftKeyDown(true);
		bike.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.assertTrue(bike.isRemoved(), "sneak-use picks the bike up");
		helper.assertTrue(player.getInventory().contains(stack -> stack.is(Bikes.ITEM)), "the bike is in the inventory");
		helper.succeed();
	}
}
