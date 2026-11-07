package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorInfection;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.arms.survivor.Traders;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/**
 * Screenshots of survivors for a human to review ({@code run/screenshots/survivors-*.png}): every skin in two rows of
 * four facing the camera, nameplates shown (a Guard holding a sword, one bitten), then one survivor close up, then a
 * River Arts trader's trading screen with the payment for its first offer in the player's inventory. Like
 * {@link HavensVisualClientTest} it asserts nothing about pixels; the rules are covered by {@code SurvivorGameTest}.
 * The nameplates are forced visible here; in play they show when looked at, like any named mob.
 */
public class SurvivorVisualClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("gamerule minecraft:spawn_mobs false");
			server.runCommand("gamerule ashfall:director_spawning false");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0 ~ 0 180 10");
			context.waitTicks(40);

			// Skins 0-3 in front, 4-7 behind, all facing the player (who looks north, so they face south).
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos base = player.blockPosition().relative(Direction.NORTH, 5);
				for (int i = 0; i < Survivor.SKINS; i++) {
					int row = i / 4, col = i % 4;
					BlockPos at = base.relative(Direction.NORTH, row * 3).relative(Direction.EAST, col * 2 - 3);
					at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
					Survivor survivor = Survivors.spawn(level, at, i == 1 ? SurvivorRole.GUARD : SurvivorRole.SETTLER, null);
					if (survivor == null) {
						throw new AssertionError("Could not spawn survivor " + i);
					}
					survivor.setSkin(i);
					survivor.setNoAi(true);
					survivor.setYRot(0f);
					survivor.setYHeadRot(0f);
					survivor.setYBodyRot(0f);
					survivor.setCustomNameVisible(true);
					if (i == 1) {
						survivor.equipWeapon(new ItemStack(Items.IRON_SWORD));
					}
					if (i == 2) {
						SurvivorInfection.infect(survivor);
					}
				}
			});
			// The mission intro's chat lines and title would cover them.
			context.runOnClient(mc -> {
				mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
				mc.gui.hud.clearTitles();
			});
			context.waitTicks(40);
			context.takeScreenshot("survivors-lineup");

			// Close up on the front row's second survivor (the Guard).
			server.runCommand("tp @a -0.5 ~ -1.5 180 0");
			context.waitTicks(20);
			context.takeScreenshot("survivors-closeup");

			// A district trader beside the player, its trading screen open.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						player.blockPosition().relative(Direction.NORTH, 2));
				Survivor trader = Traders.spawn(level, "river_arts", at);
				if (trader == null) {
					throw new AssertionError("Could not spawn the River Arts trader");
				}
				trader.setNoAi(true);
				player.getInventory().clearContent();
				player.getInventory().add(new ItemStack(SalvageItems.CANNED_FOOD, 9));
				Traders.trade(player, trader);
			});
			context.waitTicks(20);
			context.takeScreenshot("survivors-trader");
		}
	}
}
