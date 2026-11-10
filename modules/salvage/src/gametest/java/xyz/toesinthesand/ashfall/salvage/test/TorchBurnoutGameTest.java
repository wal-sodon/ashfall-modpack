package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;
import xyz.toesinthesand.ashfall.salvage.torch.BurntTorches;
import xyz.toesinthesand.ashfall.salvage.torch.TorchBurnout;

/**
 * Torch burnout: torches a player places burn out on their timer into dark burnt-out blocks, Matches light one again
 * for one use with a new timer, and torches put down any other way never burn out (not even where a player's torch
 * was just broken). Relighting needs a player who may build.
 */
public class TorchBurnoutGameTest {
	/** The test burn time (the data value is 12 minutes). */
	private static final int BURN = 20;

	private static BlockHitResult face(GameTestHelper helper, BlockPos relative, Direction side) {
		BlockPos pos = helper.absolutePos(relative);
		return new BlockHitResult(Vec3.atCenterOf(pos).relative(side, 0.5), side, pos, false);
	}

	// Gametest ticks run as fast as the server can, so 200 ticks can be about a second: too little for the light
	// engine's own thread to darken the burnt-out torch on a slow CI runner. The wait below ends as soon as it does.
	@GameTest(maxTicks = 1200)
	public void placedTorchBurnsOutAndRelights(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
		BlockPos standing = new BlockPos(1, 2, 1);
		BlockPos wall = new BlockPos(4, 1, 2);
		BlockPos standingAbs = helper.absolutePos(standing);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;
		TorchBurnout.setBurnTicksForTesting(BURN);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 2));
		Items.TORCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, face(helper, new BlockPos(1, 1, 1), Direction.UP)));
		Items.TORCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, face(helper, new BlockPos(4, 1, 1), Direction.SOUTH)));
		TorchBurnout.setBurnTicksForTesting(null);
		helper.assertBlockPresent(Blocks.TORCH, standing);
		helper.assertBlockPresent(Blocks.WALL_TORCH, wall);
		helper.assertTrue(level.getBlockTicks().hasScheduledTick(standingAbs, Blocks.TORCH), "a placed torch has no burn timer");
		helper.assertTrue(level.getBlockTicks().hasScheduledTick(helper.absolutePos(wall), Blocks.WALL_TORCH),
				"a placed wall torch has no burn timer");

		helper.startSequence()
				.thenExecuteAfter(BURN + 5, () -> {
					helper.assertBlockPresent(BurntTorches.BURNT_TORCH, standing);
					helper.assertBlockPresent(BurntTorches.BURNT_WALL_TORCH, wall);
					BlockState burntWall = helper.getBlockState(wall);
					helper.assertValueEqual(burntWall.getValue(WallTorchBlock.FACING), Direction.SOUTH, "the burnt wall torch's facing");
					helper.assertValueEqual(helper.getBlockState(standing).getLightEmission(), 0, "burnt-out torch light");
				})
				// light updates run on their own queue and lag behind on a busy test server, so wait for them to settle
				.thenWaitUntil(() -> helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, standingAbs), 0,
						"block light at a burnt-out torch"))
				.thenExecute(() -> {

					ItemStack matches = new ItemStack(SalvageItems.MATCHES);
					player.setItemInHand(InteractionHand.MAIN_HAND, matches);
					InteractionResult result = level.getBlockState(standingAbs).useItemOn(matches, level, player, InteractionHand.MAIN_HAND,
							face(helper, standing, Direction.UP));
					helper.assertTrue(result.consumesAction(), "Matches did nothing to a burnt-out torch: " + result);
					helper.assertBlockPresent(Blocks.TORCH, standing);
					helper.assertValueEqual(matches.getDamageValue(), 1, "Matches uses spent on one relight");
					helper.assertTrue(level.getBlockTicks().hasScheduledTick(standingAbs, Blocks.TORCH), "a relit torch has no new burn timer");

					ItemStack stick = new ItemStack(Items.STICK);
					InteractionResult other = level.getBlockState(helper.absolutePos(wall)).useItemOn(stick, level, player,
							InteractionHand.MAIN_HAND, face(helper, wall, Direction.SOUTH));
					helper.assertFalse(other.consumesAction(), "a stick relit a burnt-out torch");
					helper.assertBlockPresent(BurntTorches.BURNT_WALL_TORCH, wall);
					level.getServer().getPlayerList().remove(player);
				})
				.thenSucceed();
	}

	@GameTest
	public void worldTorchesNeverBurnOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(TorchBurnout.rules().burnTicks(), 14400, "data burn time (12 minutes)");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		BlockPos torch = new BlockPos(1, 2, 1);
		helper.setBlock(torch, Blocks.TORCH);
		helper.assertFalse(level.getBlockTicks().hasScheduledTick(helper.absolutePos(torch), Blocks.TORCH),
				"a torch set without a player got a burn timer");
		helper.setBlock(torch, Blocks.AIR);
		helper.succeed();
	}

	@GameTest
	public void brokenTorchTakesItsTimer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		BlockPos torch = new BlockPos(1, 2, 1);
		BlockPos torchAbs = helper.absolutePos(torch);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
		Items.TORCH.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, face(helper, new BlockPos(1, 1, 1), Direction.UP)));
		level.getServer().getPlayerList().remove(player);
		helper.assertTrue(level.getBlockTicks().hasScheduledTick(torchAbs, Blocks.TORCH), "a placed torch has no burn timer");

		level.destroyBlock(torchAbs, false);
		helper.assertFalse(level.getBlockTicks().hasScheduledTick(torchAbs, Blocks.TORCH), "a broken torch left its burn timer behind");
		helper.setBlock(torch, Blocks.TORCH);
		helper.assertFalse(level.getBlockTicks().hasScheduledTick(torchAbs, Blocks.TORCH), "a torch set without a player inherited a timer");
		helper.setBlock(torch, Blocks.AIR);
		helper.succeed();
	}

	@GameTest
	public void adventurePlayerCannotRelight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		BlockPos torch = new BlockPos(1, 2, 1);
		helper.setBlock(torch, BurntTorches.BURNT_TORCH);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.ADVENTURE);
		ItemStack matches = new ItemStack(SalvageItems.MATCHES);
		player.setItemInHand(InteractionHand.MAIN_HAND, matches);
		InteractionResult result = helper.getBlockState(torch).useItemOn(matches, level, player, InteractionHand.MAIN_HAND,
				face(helper, torch, Direction.UP));
		level.getServer().getPlayerList().remove(player);
		helper.assertFalse(result.consumesAction(), "an adventure-mode player relit a burnt-out torch: " + result);
		helper.assertBlockPresent(BurntTorches.BURNT_TORCH, torch);
		helper.assertValueEqual(matches.getDamageValue(), 0, "Matches uses spent in adventure mode");
		helper.setBlock(torch, Blocks.AIR);
		helper.succeed();
	}
}
