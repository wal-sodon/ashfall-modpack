package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import xyz.toesinthesand.ashfall.arms.supply.SupplyDropState;
import xyz.toesinthesand.ashfall.arms.supply.SupplyDrops;

import java.util.Optional;

/** Supply drops: the crate lands with its loot table, and an unclaimed crate is lost at the deadline. */
public class SupplyDropGameTest {
	@GameTest
	public void dropLandsAndIsLostAtTheDeadline(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SupplyDropState state = SupplyDropState.get(level.getServer());
		BlockPos aim = helper.absolutePos(new BlockPos(2, 1, 2));
		try {
			SupplyDropState.Active drop = SupplyDrops.start(level, aim.getX(), aim.getZ(), false);
			helper.assertTrue(state.active().isPresent(), "the drop is under way");
			BlockPos crate = SupplyDrops.land(level, drop).orElseThrow();
			helper.assertTrue(level.getBlockState(crate).is(Blocks.CHEST), "a chest landed at " + crate);
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(crate);
			helper.assertTrue(chest.getLootTable() != null && chest.getLootTable().identifier().equals(SupplyDrops.TABLE),
					"the crate carries the supply-drop table");

			state.setActive(Optional.of(new SupplyDropState.Active(drop.x(), drop.z(), level.getGameTime() - 1, Optional.of(crate))));
			SupplyDrops.tick(level);
			helper.assertTrue(level.getBlockState(crate).isAir(), "an unclaimed crate is gone after the deadline");
			helper.assertTrue(state.active().isEmpty(), "the drop is over");
		} finally {
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}
}
