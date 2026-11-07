package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;
import xyz.toesinthesand.ashfall.arms.travel.Travel;
import xyz.toesinthesand.ashfall.arms.travel.TravelRules;
import xyz.toesinthesand.ashfall.director.haven.ChunkArea;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.haven.SafeZone;

import java.util.Optional;

/**
 * The transit network between two safe zones: a trip waits out the warm-up, lands at the other zone's respawn
 * point and takes the distance cost from the player's Cell Pack; without enough charge it never starts. A trip takes
 * the owner's standing dogs along.
 */
public class TravelGameTest {
	private static final String TAG_TRIP = "gametest_travel_trip";
	private static final String TAG_NONE = "gametest_travel_none";
	// Each test has its own zones: tests in a batch run at once, and one test's clean-up must not remove another's.

	private static BlockPos[] zones(GameTestHelper helper, String tag) {
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos to = from.offset(300, 0, 0);
		HavenState state = HavenState.get(level.getServer());
		state.putCustomZone(tag + "_from", new SafeZone("Travel From", Level.OVERWORLD, ChunkArea.around(from.getX(), from.getZ(), 0),
				Optional.of(from), false, Optional.empty()));
		state.putCustomZone(tag + "_to", new SafeZone("Travel To", Level.OVERWORLD, ChunkArea.around(to.getX(), to.getZ(), 0),
				Optional.of(to), false, Optional.empty()));
		Havens.rebuild(level.getServer());
		return new BlockPos[]{from, to};
	}

	private static void cleanUp(GameTestHelper helper, String tag) {
		HavenState state = HavenState.get(helper.getLevel().getServer());
		state.removeCustomZone(tag + "_from");
		state.removeCustomZone(tag + "_to");
		Havens.rebuild(helper.getLevel().getServer());
	}

	private static ServerPlayer playerAt(GameTestHelper helper, BlockPos pos, long charge) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().clearContent();
		player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (charge >= 0) {
			player.getInventory().add(EnergyItems.withEnergy(new ItemStack(ArmsItems.CELL_PACK_SMALL), charge));
		}
		return player;
	}

	@GameTest(maxTicks = 200)
	public void tripPaysAndArrives(GameTestHelper helper) {
		BlockPos[] z = zones(helper, TAG_TRIP);
		ServerPlayer player = playerAt(helper, z[0], 30);
		Optional<?> problem = Travel.request(player, "zone:" + TAG_TRIP + "_to");
		if (problem.isPresent()) {
			cleanUp(helper, TAG_TRIP);
			helper.fail("trip refused: " + problem.get());
			return;
		}
		int cost = TravelRules.get().cost(300);
		helper.runAfterDelay(TravelRules.get().warmupTicks() + 5, () -> {
			try {
				helper.assertTrue(player.blockPosition().closerThan(z[1], 2), "did not arrive: at " + player.blockPosition());
				long left = Travel.carried(player);
				helper.assertValueEqual(left, 30L - cost, "charge left after the trip");
			} finally {
				cleanUp(helper, TAG_TRIP);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void noChargeNoTrip(GameTestHelper helper) {
		BlockPos[] z = zones(helper, TAG_NONE);
		try {
			ServerPlayer player = playerAt(helper, z[0], 5);
			helper.assertTrue(Travel.request(player, "zone:" + TAG_NONE + "_to").isPresent(), "a trip started with 5 charge");
			ServerPlayer none = playerAt(helper, z[0], -1);
			helper.assertTrue(Travel.request(none, "zone:" + TAG_NONE + "_to").isPresent(), "a trip started with no pack");
			ItemStack pack = player.getInventory().getItem(0);
			helper.assertValueEqual(SimpleEnergyItem.getStoredEnergyUnchecked(pack), 5L, "refused trip took charge");
		} finally {
			cleanUp(helper, TAG_NONE);
		}
		helper.succeed();
	}

	/** Fast travel takes the owner's standing dog within 10 blocks along; a sitting dog stays where it was told. */
	@GameTest
	public void travelBringsNearbyDog(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		ServerPlayer player = playerAt(helper, helper.absolutePos(new BlockPos(1, 2, 1)), -1);
		Wolf dog = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(3.5, 2, 1.5));
		dog.tame(player);
		dog.setOrderedToSit(false);
		Wolf sitter = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(1.5, 2, 3.5));
		sitter.tame(player);
		sitter.setOrderedToSit(true);
		Vec3 sitterBefore = sitter.position();
		BlockPos to = helper.absolutePos(new BlockPos(6, 2, 6));
		Travel.teleport(player, level, to);
		helper.assertTrue(player.blockPosition().closerThan(to, 1.5), "the player did not arrive: at " + player.blockPosition());
		helper.assertTrue(dog.blockPosition().closerThan(to, 1.5), "the dog did not come along: at " + dog.blockPosition());
		helper.assertTrue(sitter.position().equals(sitterBefore), "a sitting dog must stay, moved to " + sitter.position());
		helper.succeed();
	}
}
