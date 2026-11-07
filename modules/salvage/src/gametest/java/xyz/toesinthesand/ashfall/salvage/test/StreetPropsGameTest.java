package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.StreetProps;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;

/** Exterior loot: a placed dumpster or crate carries its one-time table and is marked Ashfall-managed. */
public class StreetPropsGameTest {
	@GameTest
	public void exteriorContainersCarryOneTimeLoot(GameTestHelper helper) {
		BlockPos dumpster = new BlockPos(1, 1, 1), crate = new BlockPos(3, 1, 1);
		helper.assertTrue(StreetProps.placeContainer(helper.getLevel(), helper.absolutePos(dumpster), true, 1L), "dumpster placed");
		helper.assertTrue(StreetProps.placeContainer(helper.getLevel(), helper.absolutePos(crate), false, 2L), "crate placed");
		helper.assertBlockPresent(Blocks.BARREL, dumpster);
		helper.assertBlockPresent(Blocks.CHEST, crate);
		check(helper, dumpster, StreetProps.DUMPSTER_TABLE.toString());
		check(helper, crate, StreetProps.CRATE_TABLE.toString());
		helper.succeed();
	}

	private static void check(GameTestHelper helper, BlockPos pos, String table) {
		RandomizableContainerBlockEntity be = helper.getBlockEntity(pos, RandomizableContainerBlockEntity.class);
		helper.assertTrue(be.getLootTable() != null && be.getLootTable().identifier().toString().equals(table),
				"loot table " + table + ", got " + be.getLootTable());
		LootState state = be.getAttached(AshfallSalvage.LOOT_STATE);
		helper.assertTrue(state != null && state.profile().isEmpty() && state.zone().isEmpty(), "one-time loot is not tied to a building or zone");
	}
}
