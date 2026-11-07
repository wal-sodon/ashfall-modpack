package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.client.grid.GridMapScreen;
import xyz.toesinthesand.ashfall.arms.client.mission.MissionClient;
import xyz.toesinthesand.ashfall.arms.client.mission.MissionClientState;
import xyz.toesinthesand.ashfall.arms.mission.MissionBriefing;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenBlocks;
import xyz.toesinthesand.ashfall.director.haven.HavenState;

import java.util.List;

/**
 * Screenshots of the mission UI for a human to review ({@code run/screenshots/mission-*.png}): the first-join title and
 * chat debrief, the objective line by the hotbar, the {@code /ashfall mission} checklist, the briefing dialog, the
 * Mission Briefing book (cover and a dense page) and the Grid Map with the Bunker Core marker.
 * <p>
 * It also checks every briefing page against the real font: the unit test estimates glyph widths, this one asks the
 * game how many lines each page wraps to at the book's 114-pixel width (at most 14 fit).
 */
public class MissionVisualClientTest implements FabricClientGameTest {
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
			context.runOnClient(mc -> {
				mc.options.chatVisibility().set(ChatVisiblity.FULL);
				MissionClient.setVisible(true);
			});

			// The briefing arrives a few seconds after joining: title, chat and the book.
			context.waitFor(mc -> mc.player != null && hasBriefing(mc.player.getInventory()), 400);
			context.waitTicks(10);
			context.takeScreenshot("mission-briefing-title");
			context.waitTicks(140);
			context.takeScreenshot("mission-briefing-chat");

			// The objective line by the hotbar (first objective: the radio, with a heading to the safehouse).
			context.waitFor(mc -> MissionClientState.objective().isPresent(), 200);
			context.takeScreenshot("mission-hud-line");

			context.runOnClient(mc -> mc.player.connection.sendCommand("ashfall mission"));
			context.waitTicks(20);
			context.takeScreenshot("mission-checklist");

			context.runOnClient(mc -> mc.player.connection.sendCommand("ashfall mission briefing"));
			context.waitTicks(20);
			context.takeScreenshot("mission-briefing-dialog");
			context.setScreen(() -> null);

			ItemStack book = context.computeOnClient(mc -> {
				for (ItemStack stack : mc.player.getInventory()) {
					if (MissionBriefing.isBriefing(stack)) {
						return stack.copy();
					}
				}
				return ItemStack.EMPTY;
			});
			checkPagesFit(context, book);
			context.setScreen(() -> new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)));
			context.waitTicks(10);
			context.takeScreenshot("mission-book-cover");
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof BookViewScreen screen) {
					screen.setPage(6);
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("mission-book-energize");
			context.setScreen(() -> null);

			// A Bunker Core for the player, then the Grid Map with its marker.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(60, 0, 40);
				s.overworld().setBlockAndUpdate(pos, HavenBlocks.BUNKER_CORE.defaultBlockState());
				HavenState.get(s).putCore(new BunkerCoreRecord(player.getUUID(), player.getUUID(), Level.OVERWORLD, pos, 0, List.of()));
			});
			context.waitFor(mc -> MissionClientState.bunker().isPresent(), 200);
			context.setScreen(GridMapScreen::new);
			context.waitTicks(10);
			context.takeScreenshot("mission-grid-map-bunker");
			context.setScreen(() -> null);
		}
	}

	private static boolean hasBriefing(Iterable<ItemStack> inventory) {
		for (ItemStack stack : inventory) {
			if (MissionBriefing.isBriefing(stack)) {
				return true;
			}
		}
		return false;
	}

	/** Every page wraps to at most 14 lines of the real font at the book's text width. */
	private static void checkPagesFit(ClientGameTestContext context, ItemStack book) {
		context.runOnClient(mc -> {
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (content == null) {
				throw new AssertionError("no Mission Briefing book in the inventory");
			}
			for (int i = 0; i < content.pages().size(); i++) {
				Component page = content.pages().get(i).raw();
				int lines = mc.font.split(page, 114).size();
				if (lines > 14) {
					throw new AssertionError("briefing page " + (i + 1) + " wraps to " + lines + " lines, a book page holds 14");
				}
			}
		});
	}
}
