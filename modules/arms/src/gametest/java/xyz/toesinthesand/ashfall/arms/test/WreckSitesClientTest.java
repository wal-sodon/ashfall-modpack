package xyz.toesinthesand.ashfall.arms.test;

import mcjty.lostcities.setup.Config;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.ChatVisiblity;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.site.CrashSitePlanner;
import xyz.toesinthesand.ashfall.salvage.site.PlacedSite;
import xyz.toesinthesand.ashfall.salvage.site.SiteState;
import xyz.toesinthesand.ashfall.salvage.site.Sites;

import java.util.List;

/**
 * Visits the Wreck hull on the ridge and the nearest ridge crash site in a fresh Lost Cities world, lets the
 * lazy site placement run with a real player nearby, and takes screenshots ({@code run/screenshots/wreck-*.png})
 * for a human to review. It fails if either site is not placed after the player has stood by it.
 */
public class WreckSitesClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Config.profileFromClient = LostCityContext.PROFILE;
		Config.resetProfileCache();
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(ui -> ui.setSeed("ashfall-landmarks"))
				.create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("gamerule minecraft:advance_weather false");
			server.runCommand("gamerule minecraft:spawn_mobs false");
			server.runCommand("gamerule ashfall:director_spawning false");
			context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			server.runCommand("gamemode spectator @a");
			server.runCommand("effect give @a minecraft:night_vision infinite 0 true");

			// The hull: stand over it until the site placer (every 40 ticks, footprint loaded) has run.
			server.runCommand("tp @a 2300 200 -100");
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.waitTicks(100);
			PlacedSite hull = server.computeOnServer(s -> SiteState.get(s).site(PlacedSite.fixedKey("ashfall:wreck_hull")));
			if (hull == null) {
				throw new AssertionError("the Wreck hull was not placed with a player standing over it");
			}
			BlockPos c = hull.center();
			server.runCommand(String.format("tp @a %d %d %d 160 25", c.getX() - 6, c.getY() + 8, c.getZ() + 26));
			shoot(context, world, "wreck-hull-south");
			server.runCommand(String.format("tp @a %d %d %d -120 35", c.getX() - 26, c.getY() + 16, c.getZ() - 14));
			shoot(context, world, "wreck-hull-aerial");

			// The nearest ridge crash site to the hull.
			List<CrashSitePlanner.Planned> near = server.computeOnServer(s -> Sites.plannedNear(s.overworld(), 2300, -100, 600));
			if (near.isEmpty()) {
				throw new AssertionError("no crash site planned within 600 blocks of the hull");
			}
			CrashSitePlanner.Planned site = near.getFirst();
			server.runCommand(String.format("tp @a %d 200 %d", site.centerX(), site.centerZ()));
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.waitTicks(100);
			PlacedSite placed = server.computeOnServer(s -> SiteState.get(s).site(site.key()));
			if (placed == null) {
				throw new AssertionError("crash site " + site.key() + " was not placed with a player standing over it");
			}
			BlockPos p = placed.center();
			server.runCommand(String.format("tp @a %d %d %d 180 35", p.getX(), p.getY() + 7, p.getZ() + 10));
			shoot(context, world, "wreck-crash-site");
			System.out.println("[WRECK] hull " + hull.origin().toShortString() + ", crash site " + site.key() + " ("
					+ site.template() + ") at " + placed.origin().toShortString());
		}
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		context.waitTicks(6);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(name);
	}
}
