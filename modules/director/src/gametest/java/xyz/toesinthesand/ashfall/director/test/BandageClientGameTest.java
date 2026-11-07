package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

/**
 * A Bandage used the way a player does: hold the use key in a singleplayer world for longer than its use time. The
 * server-side gametests call the use path directly, so they cannot see a client mod that cancels the key or the use
 * packet; run this with {@code -PashfallPackTest=<the instance's mods folder>} to check against the whole pack.
 */
public class BandageClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a ashfall:bandage 3");
			// Lost Cities spawns can drop the player onto a rooftop; land them unhurt before the half-health set below.
			server.runCommand("effect give @a slow_falling 30 0 true");
			server.runCommand("effect give @a resistance 30 4 true");
			context.waitTicks(100);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				player.getInventory().setSelectedSlot(0);
				player.setHealth(10f);
			});
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(20);
			context.getInput().holdKeyFor(options -> options.keyUse, 45); // one 32-tick use; held longer it eats the next, like food
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				int left = player.getInventory().getItem(0).is(DirectorItems.BANDAGE) ? player.getInventory().getItem(0).getCount() : 0;
				float heal = Infection.rules().treatments().bandageHeal();
				if (left != 2 || player.getHealth() < 10f + heal) {
					throw new AssertionError("holding use with a Bandage should spend one and heal: bandages left " + left
							+ ", health " + player.getHealth());
				}
			});
		}
	}
}
