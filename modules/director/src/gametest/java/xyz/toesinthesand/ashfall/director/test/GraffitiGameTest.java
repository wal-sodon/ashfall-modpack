package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.graffiti.Graffiti;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiBlock;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiSymbol;
import xyz.toesinthesand.ashfall.director.graffiti.MarkState;
import xyz.toesinthesand.ashfall.director.graffiti.SprayCanItem;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.spawn.BuildingRecord;
import xyz.toesinthesand.ashfall.director.spawn.DirectorState;
import xyz.toesinthesand.ashfall.director.spawn.SpawnRules;

import java.util.List;
import java.util.UUID;

/**
 * Graffiti on a real server: the Spray Can paints on the clicked wall face and switches symbol on sneak + use, and a
 * Cleared mark tells the truth about its building (warn while populated, fresh once cleared, stale once refilled).
 * The building is a test lookup with a random id, so no Lost Cities building or real population is needed.
 */
public class GraffitiGameTest {
	private static final int PADDING = 8;

	@GameTest(padding = PADDING)
	public void sprayCanPaintsClickedWallFace(GameTestHelper helper) {
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.setBlock(wall, Blocks.STONE);
		BlockPos wallAbs = helper.absolutePos(wall);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack can = new ItemStack(DirectorItems.SPRAY_CAN);
		player.setItemInHand(InteractionHand.MAIN_HAND, can);

		for (Direction face : List.of(Direction.EAST, Direction.NORTH)) {
			InteractionResult result = DirectorItems.SPRAY_CAN.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(wallAbs).relative(face, 0.5), face, wallAbs, false)));
			helper.assertValueEqual(result, InteractionResult.SUCCESS, "spraying the " + face + " face");
			BlockState mark = helper.getLevel().getBlockState(wallAbs.relative(face));
			helper.assertTrue(mark.is(DirectorBlocks.GRAFFITI), "a mark appears in front of the " + face + " face, got " + mark);
			helper.assertValueEqual(mark.getValue(GraffitiBlock.FACING), face, "the mark faces away from the wall");
			helper.assertValueEqual(mark.getValue(GraffitiBlock.SYMBOL), GraffitiSymbol.CLEARED, "a new can sprays Cleared");
		}
		helper.assertValueEqual(can.getDamageValue(), 2, "each spray uses one of the can's " + SprayCanItem.USES);

		InteractionResult floor = DirectorItems.SPRAY_CAN.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(wallAbs).relative(Direction.UP, 0.5), Direction.UP, wallAbs, false)));
		helper.assertValueEqual(floor, InteractionResult.FAIL, "spraying the top of a block");
		helper.assertBlockNotPresent(DirectorBlocks.GRAFFITI, wall.above());

		player.setShiftKeyDown(true);
		DirectorItems.SPRAY_CAN.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(SprayCanItem.symbol(can), GraffitiSymbol.DANGER, "sneak + use switches to the next symbol");
		player.setShiftKeyDown(false);
		helper.assertTrue(Graffiti.spray(helper.getLevel(), wallAbs, Direction.SOUTH, SprayCanItem.symbol(can), player), "spraying Danger");
		helper.assertValueEqual(helper.getLevel().getBlockState(wallAbs.relative(Direction.SOUTH)).getValue(GraffitiBlock.SYMBOL),
				GraffitiSymbol.DANGER, "the switched symbol is what gets sprayed");
		BlockPos danger = wallAbs.relative(Direction.SOUTH);
		helper.assertTrue(Graffiti.spray(helper.getLevel(), danger, Direction.SOUTH, GraffitiSymbol.LOOT, player),
				"aiming at the paint sprays over it");
		helper.assertValueEqual(helper.getLevel().getBlockState(danger).getValue(GraffitiBlock.SYMBOL), GraffitiSymbol.LOOT,
				"the new symbol replaces the old one in place");
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void clearedMarkFollowsBuildingRecord(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wall = new BlockPos(2, 1, 2);
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.setBlock(wall, Blocks.STONE);
		BlockPos wallAbs = helper.absolutePos(wall);
		BlockPos markAbs = wallAbs.relative(Direction.WEST);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long buildingId = level.getRandom().nextLong();
		DirectorState state = DirectorState.get(level.getServer());
		BuildingRecord record = state.getOrCreate(buildingId);
		UUID member = UUID.randomUUID();
		record.populated(4, List.of(member));

		Graffiti.setBuildingLookupForTesting(pos -> pos.equals(wallAbs) ? buildingId : null);
		try {
			helper.assertTrue(Graffiti.spray(level, wallAbs, Direction.WEST, GraffitiSymbol.CLEARED, player), "spraying Cleared");
			helper.assertValueEqual(level.getBlockState(markAbs).getValue(GraffitiBlock.STATE), MarkState.WARN,
					"a Cleared mark on a populated building gets the warn overlay");
			helper.assertTrue(state.graffiti().get(markAbs.asLong()) != null, "the mark is in the Director's index");

			helper.assertTrue(record.memberGone(member), "the last member dying clears the building");
			Graffiti.buildingChanged(level.getServer(), buildingId);
			helper.assertValueEqual(level.getBlockState(markAbs).getValue(GraffitiBlock.STATE), MarkState.FRESH,
					"the mark is true once the building is cleared");

			helper.assertTrue(record.tickCleared(false, SpawnRules.CLEARED_TICKS, SpawnRules.CLEARED_TICKS), "the cleared period ends");
			helper.assertValueEqual(record.status(), BuildingRecord.Status.READY, "the record is READY");
			Graffiti.buildingChanged(level.getServer(), buildingId);
			helper.assertValueEqual(level.getBlockState(markAbs).getValue(GraffitiBlock.STATE), MarkState.STALE,
					"a READY record turns its Cleared marks stale");

			helper.assertTrue(Graffiti.spray(level, wallAbs, Direction.NORTH, GraffitiSymbol.DANGER, player), "spraying Danger");
			helper.assertValueEqual(level.getBlockState(wallAbs.relative(Direction.NORTH)).getValue(GraffitiBlock.STATE), MarkState.FRESH,
					"only Cleared marks follow the building");

			level.setBlockAndUpdate(markAbs, Blocks.AIR.defaultBlockState());
			helper.assertTrue(state.graffiti().get(markAbs.asLong()) == null, "a removed mark leaves the index");
		} finally {
			Graffiti.setBuildingLookupForTesting(null);
		}
		helper.succeed();
	}
}
