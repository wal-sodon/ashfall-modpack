package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.toesinthesand.ashfall.arms.client.grid.GridMapScreen;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;
import xyz.toesinthesand.ashfall.arms.grid.GridSyncPayload;
import xyz.toesinthesand.ashfall.arms.story.RadioProgress;
import xyz.toesinthesand.ashfall.arms.story.Story;

import java.util.Map;

/**
 * Screenshots of the story UI for a human to review ({@code run/screenshots/p3-*.png}): the Grid HUD beside the
 * hotbar, the Grid Map with every district state on show, a Relay Log book and the radio's chat report. The grid
 * states come from a hand-made {@code grid_sync} packet, so the shots do not depend on the district loop.
 */
public class StoryVisualClientTest implements FabricClientGameTest {
	private static final Map<String, DistrictState> STATES = Map.of("river_arts", DistrictState.RECLAIMED,
			"south_slope", DistrictState.SIEGE, "downtown", DistrictState.ENERGIZED, "mission", DistrictState.DARK);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("gamerule minecraft:spawn_mobs false");
			server.runCommand("gamerule ashfall:director_spawning false");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 0 10");
			context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
			context.waitTicks(40);

			// Before the radio: no Grid HUD.
			context.takeScreenshot("p3-hud-before-radio");

			server.runCommand("give @p ashfall:field_radio");
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerPlayNetworking.send(player, new GridSyncPayload(STATES, 20));
				// Two relays tuned near spawn, so the map shows discovered relays and the player holds a log.
				Story.tuneRelay(player, player.blockPosition().offset(40, 0, 25));
				Story.tuneRelay(player, player.blockPosition().offset(-120, 0, 90));
			});
			context.waitFor(mc -> mc.player != null && RadioProgress.get(mc.player).radio(), 200);
			server.runCommand("item replace entity @p weapon.mainhand with ashfall:field_radio");
			context.waitTicks(20);
			context.takeScreenshot("p3-grid-hud");

			// The radio report in chat.
			context.runOnClient(mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND));
			context.waitTicks(20);
			context.takeScreenshot("p3-radio-report");

			context.setScreen(GridMapScreen::new);
			context.waitTicks(10);
			context.takeScreenshot("p3-grid-map");
			context.setScreen(() -> null);

			ItemStack book = context.computeOnClient(mc -> {
				for (ItemStack stack : mc.player.getInventory()) {
					if (stack.is(Items.WRITTEN_BOOK)) {
						return stack.copy();
					}
				}
				return ItemStack.EMPTY;
			});
			context.setScreen(() -> new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)));
			context.waitTicks(10);
			context.takeScreenshot("p3-relay-log-book");
			context.setScreen(() -> null);
		}
	}
}
