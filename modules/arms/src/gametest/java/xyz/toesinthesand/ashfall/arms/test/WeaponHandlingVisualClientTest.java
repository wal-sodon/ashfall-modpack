package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.ChatVisiblity;
import xyz.toesinthesand.ashfall.arms.client.gun.ArmsGunsClient;

/**
 * Screenshots of the 10.1.0 weapon handling for a human to review ({@code run/screenshots/handling-*.png}): the
 * flamethrower spraying a zombie in first and third person with the fuel line in the HUD, then a Spark Pistol switched
 * to stun mode (C) with the STUN tag after the charge, and the zombie it held. Like {@link SurvivorVisualClientTest} it
 * asserts nothing about pixels; the rules are covered by {@code FlamethrowerGameTest} and {@code StunModeGameTest}.
 */
public class WeaponHandlingVisualClientTest implements FabricClientGameTest {
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
			server.runCommand("effect give @a minecraft:resistance infinite 255 true");
			server.runCommand("tp @a 0 ~ 0 180 0");
			context.waitTicks(40);
			// The mission intro's chat lines would cover the shots (its title is cleared before each one).
			context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));

			// Flamethrower: a full tank, a zombie four blocks north that doesn't walk off (and resists the damage).
			// Helmets keep the noon sun off both zombies.
			server.runCommand("clear @a");
			server.runCommand("execute at @p run summon minecraft:zombie ~ ~ ~-4 {NoAI:1b,Rotation:[0f,0f],equipment:{head:{id:\"minecraft:leather_helmet\"}},"
					+ "active_effects:[{id:\"minecraft:resistance\",duration:-1,amplifier:3b,show_particles:0b}]}");
			server.runCommand("give @a ashfall:flamethrower[ashfall:fuel=320]");
			context.waitTicks(30); // past the 18-tick draw
			shot(context, "handling-flamer-ready");
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(16);
			shot(context, "handling-flamer-firing");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(8);
			shot(context, "handling-flamer-firing-tp");
			context.getInput().releaseKey(options -> options.keyUse);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("kill @e[type=minecraft:zombie]");
			server.runCommand("execute at @p run summon minecraft:zombie ~ ~ ~-5 {NoAI:1b,Rotation:[0f,0f],equipment:{head:{id:\"minecraft:leather_helmet\"}}}");

			// Stun mode: a loaded Spark Pistol, C to switch, then one bolt at the zombie.
			server.runCommand("clear @a");
			server.runCommand("give @a ashfall:spark_pistol");
			server.runCommand("give @a ashfall:cell_pack_small[team_reborn_energy:energy=30]");
			context.waitTicks(20);
			context.getInput().pressKey(ArmsGunsClient.RELOAD);
			context.waitTicks(40);
			context.getInput().pressKey(ArmsGunsClient.FIRE_MODE);
			context.waitTicks(10);
			shot(context, "handling-stun-hud");
			server.runCommand("tp @a 0 ~ 0 180 8");
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			shot(context, "handling-stun-held");
		}
	}

	/** The mission intro's title fades in a while after joining; clear it so it never covers a shot. */
	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.clearTitles());
		context.waitTick();
		context.takeScreenshot(name);
	}
}
