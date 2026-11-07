package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import xyz.toesinthesand.ashfall.director.haven.BunkerCores;
import xyz.toesinthesand.ashfall.director.haven.HavenBlocks;

/**
 * Screenshots of the Phase 2 player-facing pieces for a human to review ({@code run/screenshots/p2-*.png}):
 * the infection HUD at stage 3, a placed Bunker Core, the respawn choice prompt after a death, and two bosses.
 * Like {@link ArmsVisualClientTest} it asserts nothing about pixels; the server-side rules behind each of these
 * are covered by the director gametests, and this test only catches what those cannot: how it looks.
 */
public class HavensVisualClientTest implements FabricClientGameTest {
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

			// Infection stage 3: the HUD readout and its symptoms.
			server.runCommand("ashfall infection set @p 3");
			context.waitTicks(60);
			context.takeScreenshot("p2-infection-stage3");
			server.runCommand("ashfall infection clear @p");
			server.runCommand("effect clear @a");

			// A Bunker Core three blocks ahead, registered as the player's core so the respawn prompt offers it.
			server.runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);
				level.setBlockAndUpdate(pos, HavenBlocks.BUNKER_CORE.defaultBlockState());
				BunkerCores.onPlaced(level, pos, player.getUUID());
			});
			context.waitTicks(20);
			context.takeScreenshot("p2-bunker-core");

			// Death and respawn: the clickable choice lines arrive in chat.
			server.runCommand("kill @p");
			context.waitTicks(30);
			context.takeScreenshot("p2-death-screen");
			context.runOnClient(mc -> {
				mc.player.respawn();
				mc.gui.setScreen(null);
			});
			context.waitTicks(40);
			context.takeScreenshot("p2-respawn-choice");

			// Bosses six blocks ahead; creative so they do not attack the camera.
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 40 ~ 40 0 5");
			context.waitTicks(20);
			server.runCommand("execute at @p positioned ^ ^ ^7 run ashfall boss spawn ashfall:patient_zero here");
			context.waitTicks(40);
			context.takeScreenshot("p2-boss-patient-zero");
			server.runCommand("kill @e[tag=ashfall_boss]");
			server.runCommand("kill @e[type=item]");
			server.runCommand("tp @a 80 ~ 80 0 5");
			context.waitTicks(20);
			server.runCommand("execute at @p positioned ^ ^ ^8 run ashfall boss spawn ashfall:juggernaut here");
			context.waitTicks(40);
			context.takeScreenshot("p2-boss-juggernaut");
		}
	}
}
