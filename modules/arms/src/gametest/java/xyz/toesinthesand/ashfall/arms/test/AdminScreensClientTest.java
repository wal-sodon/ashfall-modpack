package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import xyz.toesinthesand.ashfall.arms.client.drops.MobDropsScreen;

/**
 * Screenshots of the op-only admin screens for a human to review ({@code run/screenshots/m7-*.png}): the Loot
 * Zone screen with one zone made by command, and the Mob Drops screen on the shipped rules. Both screens are
 * opened the way an op opens them, through {@code /lootzone gui} and {@code /mobdrops gui}.
 */
public class AdminScreensClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("gamerule minecraft:spawn_mobs false");
			server.runCommand("gamerule ashfall:director_spawning false");
			context.waitTicks(40);
			server.runCommand("execute as @p at @s run lootzone create test_clinic 12 any ashfall:buildings/clinic");
			server.runCommand("execute as @p run lootzone gui");
			context.waitFor(mc -> mc.gui.screen() != null
					// Salvage's client classes are not on arms' compile path: match the screen by name.
					&& mc.gui.screen().getClass().getSimpleName().equals("LootZoneScreen"), 200);
			context.waitTicks(10);
			context.takeScreenshot("m7-loot-zones");
			context.setScreen(() -> null);

			server.runCommand("execute as @p run mobdrops gui");
			context.waitFor(mc -> mc.gui.screen() instanceof MobDropsScreen, 200);
			context.waitTicks(10);
			context.takeScreenshot("m7-mob-drops");
			context.setScreen(() -> null);
		}
	}
}
