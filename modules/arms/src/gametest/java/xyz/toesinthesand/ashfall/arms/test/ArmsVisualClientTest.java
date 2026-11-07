package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import xyz.toesinthesand.ashfall.arms.client.gun.ArmsGunsClient;

import java.util.List;

/**
 * Renders the arms content in a real client and saves screenshots (run/screenshots) for a human to
 * check: 3D gun models in first and third person, bolts and the heat/charge HUD while firing, the Optic
 * scope overlay (4x, 2x, the Lancer's 7x), the thirst bar, the chargers, and the inventory icons. It asserts nothing about pixels; it fails only if
 * the client crashes or a command errors.
 */
public class ArmsVisualClientTest implements FabricClientGameTest {
	private static final List<String> GUNS = List.of("spark_pistol", "volt_sidearm", "pulse_carbine", "ion_rifle",
			"arc_magnum", "lancer_longrifle", "storm_repeater");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule minecraft:advance_time false");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a minecraft:resistance infinite 255 true");

			context.waitTicks(40);

			// Firing: a charged Standard Cell Pack, reload with R, hold use on the Pulse Carbine.
			server.runCommand("clear @a");
			server.runCommand("give @a ashfall:pulse_carbine");
			server.runCommand("give @a ashfall:cell_pack_standard[team_reborn_energy:energy=60] 2");
			context.waitTicks(10);
			context.getInput().pressKey(ArmsGunsClient.RELOAD);
			context.waitTicks(40);
			context.takeScreenshot("ashfall-hud-loaded");
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(12);
			context.takeScreenshot("ashfall-firing");
			context.waitTicks(30);
			context.getInput().releaseKey(options -> options.keyUse);
			context.takeScreenshot("ashfall-after-burst");

			// Every gun in first and third person.
			for (String gun : GUNS) {
				server.runCommand("item replace entity @a weapon.mainhand with ashfall:" + gun);
				context.waitTicks(8);
				context.takeScreenshot("ashfall-fp-" + gun);
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			for (String gun : GUNS) {
				server.runCommand("item replace entity @a weapon.mainhand with ashfall:" + gun);
				context.waitTicks(8);
				context.takeScreenshot("ashfall-tp-" + gun);
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Optic scope: sneak to aim. The Ion Rifle at the Optic's 4x, one scroll notch down to 2x, then the Lancer's
			// 7x marksman scope (its scope_zoom), and the view back to normal after letting go. The overlay should hide
			// the crosshair and the held blaster while the heat/charge HUD stays visible.
			server.runCommand("item replace entity @a weapon.mainhand with ashfall:ion_rifle[ashfall:attachments=[\"optic\"]]");
			context.waitTicks(8);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(20);
			context.takeScreenshot("scope-optic-4x");
			context.getInput().scroll(-1);
			context.waitTicks(15);
			context.takeScreenshot("scope-optic-2x");
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(10);
			server.runCommand("item replace entity @a weapon.mainhand with ashfall:lancer_longrifle[ashfall:attachments=[\"optic\"]]");
			context.waitTicks(8);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(20);
			context.takeScreenshot("scope-lancer-7x");
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(15);
			context.takeScreenshot("scope-released");

			// Chargers in front of the player.
			server.runCommand("execute at @p run setblock ^ ^ ^3 ashfall:field_charger");
			server.runCommand("execute at @p run setblock ^1 ^ ^3 ashfall:power_relay");
			context.waitTicks(20);
			context.takeScreenshot("ashfall-chargers");

			// Plasma Blade (Phase 4.5): lit and dark, first and third person, and the Weaponsmith Bench.
			server.runCommand("item replace entity @a weapon.mainhand with ashfall:plasma_blade[team_reborn_energy:energy=120]");
			context.waitTicks(8);
			context.takeScreenshot("p45-blade-fp-lit");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(8);
			context.takeScreenshot("p45-blade-tp-lit");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("item replace entity @a weapon.mainhand with ashfall:plasma_blade");
			context.waitTicks(8);
			context.takeScreenshot("p45-blade-fp-dark");
			// Resonant Plasma Blade (phase 6, Bind ending): the cyan-and-gold beam next to the plain blade's violet.
			server.runCommand("item replace entity @a weapon.mainhand with ashfall:resonant_plasma_blade[team_reborn_energy:energy=120]");
			context.waitTicks(8);
			context.takeScreenshot("p6-resonant-blade-fp-lit");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(8);
			context.takeScreenshot("p6-resonant-blade-tp-lit");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("execute at @p run setblock ^-1 ^ ^3 ashfall:weaponsmith_bench");
			context.waitTicks(20);
			context.takeScreenshot("p45-bench");

			// Special infected (Phase 4.5 playtest owes this shot): all six in a row, frozen and harmless, so their
			// textures, overlay layer and the brute's scale and crawler's low hitbox can be checked side by side.
			// Dusk, so they don't burn in daylight on camera.
			server.runCommand("time set 13000");
			String[] specials = {"crawler", "bloated", "spitter", "screamer", "stalker", "brute"};
			for (int i = 0; i < specials.length; i++) {
				server.runCommand("execute at @p run summon ashfall:" + specials[i] + " ^" + (5 - 2 * i) + " ^ ^7"
						+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}");
			}
			context.waitTicks(20);
			context.takeScreenshot("p45-specials");
			server.runCommand("kill @e[type=#minecraft:zombies]");

			// Black Start (Phase 6): the finale's blocks in a row (socket dark and lit, a tripped and a closed breaker,
			// a Heart pylon, the Hull Seal), and the Choir beside them, frozen, to check its tint, shard overlay and scale.
			server.runCommand("execute at @p run setblock ^3 ^ ^6 ashfall:uplink_socket");
			server.runCommand("execute at @p run setblock ^2 ^ ^6 ashfall:uplink_socket[power=2]");
			server.runCommand("execute at @p run setblock ^1 ^ ^6 ashfall:relay_breaker[triggered=true]");
			server.runCommand("execute at @p run setblock ^ ^ ^6 ashfall:relay_breaker[triggered=false]");
			server.runCommand("execute at @p run setblock ^-1 ^ ^6 ashfall:heart_pylon");
			server.runCommand("execute at @p run setblock ^-2 ^ ^6 ashfall:hull_seal");
			server.runCommand("execute at @p run summon ashfall:choir ^-5 ^ ^8"
					+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}");
			context.waitTicks(20);
			context.takeScreenshot("p6-finale-blocks-choir");
			server.runCommand("kill @e[type=ashfall:choir]");
			server.runCommand("time set noon");

			// Inventory icons.
			server.runCommand("clear @a");
			for (String item : List.of("spark_pistol", "volt_sidearm", "pulse_carbine", "ion_rifle", "arc_magnum",
					"lancer_longrifle", "storm_repeater", "cell_pack_small", "cell_pack_standard", "cell_pack_heavy",
					"dampener", "energy_cell", "field_charger", "power_relay", "bottled_water", "dirty_water",
					"canned_food", "battery", "matches", "lighter_fluid", "optic", "heat_sink", "cell_regulator", "overcharger",
					"plasma_blade", "resonant_plasma_blade", "weaponsmith_bench", "uplink_frame", "capacitor_bank",
					"grid_uplink")) {
				server.runCommand("give @a ashfall:" + item);
			}
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			context.takeScreenshot("ashfall-inventory");
			context.getInput().pressKey(options -> options.keyInventory);

			// Thirst: drain to 3 droplets to show the bar.
			server.runCommand("thirst set @p 6");
			context.waitTicks(20);
			context.takeScreenshot("ashfall-thirst-low");
		}
	}
}
