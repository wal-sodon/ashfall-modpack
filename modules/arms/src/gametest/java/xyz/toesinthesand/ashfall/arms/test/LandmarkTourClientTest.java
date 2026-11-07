package xyz.toesinthesand.ashfall.arms.test;

import mcjty.lostcities.setup.Config;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.LandmarkCensus;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tours the landmark buildings in a real client on a fresh {@code ashfall}-profile world and saves
 * screenshots of their interiors (run/screenshots/landmark-*.png) plus a census per landmark
 * (run/landmark-tour.txt) for a human to review. It fails if a landmark is missing from its fixed chunk,
 * if the new player does not start inside the safehouse, or if a landmark has ladders below the roof or
 * any light source; how the rooms look is for the reviewer.
 * <p>
 * Spectator mode with night vision, because the buildings are deliberately unlit. Camera spots are in
 * landmark-local coordinates: x/z from the landmark's north-west corner, floor 0 = ground floor.
 */
public class LandmarkTourClientTest implements FabricClientGameTest {
	private final StringBuilder log = new StringBuilder();
	private final List<String> failures = new ArrayList<>();

	/** A camera position: landmark-local block coordinates, floor, view direction (vanilla yaw/pitch). */
	/**
	 * @param lift blocks above the floor's standing height. Some views (the Mission control room's overlook)
	 *             are blocked at eye height by a console row and only open up from a step higher.
	 */
	record Shot(String label, int floor, double x, double z, float yaw, float pitch, int lift) {
		Shot(String label, int floor, double x, double z, float yaw, float pitch) {
			this(label, floor, x, z, yaw, pitch, 0);
		}
	}

	/**
	 * @param building the Lost Cities building id expected in the north-west chunk
	 * @param cx       absolute chunk of the north-west corner (from the predefined city)
	 * @param w        size in chunks along x, {@code h} along z
	 */
	record Landmark(String name, String building, int cx, int cz, int w, int h, int cellars, int floors, List<Shot> shots) {
	}

	static final List<Landmark> LANDMARKS = List.of(
			new Landmark("safehouse", "ashfall:safehouse", -10, -50, 1, 1, 1, 2, List.of(
					new Shot("f0-hall", 0, 6.5, 13.5, 180, 10),
					new Shot("f0-living", 0, 3.5, 12.5, 160, 15),
					new Shot("f0-kitchen", 0, 9.5, 13.5, -135, 20),
					new Shot("f0-store", 0, 9.5, 3.5, -100, 15),
					new Shot("f0-stairs", 0, 6.5, 5.5, 110, 20),
					new Shot("f1-bunks", 1, 2.5, 7.5, -10, 20),
					new Shot("f1-bedroom", 1, 9.5, 8.5, -60, 20),
					new Shot("f1-bath", 1, 6.5, 3.5, 160, 35),
					new Shot("f1-radio", 1, 10.5, 4.5, -100, 15),
					new Shot("c1-cellar", -1, 5.5, 5.5, -45, 15),
					new Shot("c1-pantry", -1, 8.5, 10.5, 10, 20),
					new Shot("roof", 2, 8.5, 9.5, -150, 15))),
			new Landmark("pharmacy", "ashfall:pharmacy", -8, -50, 1, 1, 0, 2, List.of(
					new Shot("f0-shop", 0, 7.5, 13.5, 180, 10),
					new Shot("f0-aisle", 0, 5.5, 12.5, 170, 15),
					new Shot("f0-dispensary", 0, 9.5, 4.5, -150, 15),
					new Shot("f1-stock", 1, 4.5, 13.5, 180, 15),
					new Shot("f1-break", 1, 12.5, 13.5, 180, 20),
					new Shot("f1-office", 1, 7.5, 3.0, 180, 20))),
			new Landmark("police", "ashfall:police_station_00", -4, 1, 2, 1, 1, 2, List.of(
					new Shot("f0-lobby", 0, 15.5, 13.5, 180, 10),
					new Shot("f0-cells", 0, 6.5, 5.5, 180, 15),
					new Shot("f0-bullpen", 0, 23.5, 13.5, -135, 15),
					new Shot("f0-booking", 0, 8.5, 12.5, 120, 15),
					new Shot("f0-stairs", 0, 13.5, 6.5, 180, 25),
					new Shot("f1-armory", 1, 27.5, 3.5, 180, 20),
					new Shot("f1-lockers", 1, 3.5, 2.5, -90, 15),
					new Shot("f1-conference", 1, 12.5, 13.5, -135, 15),
					new Shot("f1-evidence", 1, 20.5, 3.5, 180, 15),
					new Shot("c1-range", -1, 24.5, 13.5, 180, 5),
					new Shot("c1-records", -1, 7.5, 8.5, 20, 15))),
			new Landmark("hospital", "ashfall:hospital_00", 44, 39, 3, 2, 2, 3, List.of(
					new Shot("f0-lobby", 0, 23.5, 29.5, 180, 10),
					new Shot("f0-er", 0, 7.5, 20.5, 0, 15),
					new Shot("f0-pharmacy", 0, 34.5, 24.5, -60, 15),
					new Shot("f0-corridor", 0, 5.5, 15.5, -90, 5),
					new Shot("f0-cafeteria", 0, 32.5, 12.5, 180, 10),
					new Shot("f0-exam", 0, 10.5, 12.5, 180, 15),
					new Shot("f1-ward-north", 1, 11.5, 12.5, 160, 15),
					new Shot("f1-ward-south", 1, 4.5, 19.5, 0, 15),
					new Shot("f1-dayroom", 1, 24.5, 21.5, 0, 10),
					new Shot("f2-theatre", 2, 11.5, 12.5, 180, 20),
					new Shot("f2-icu", 2, 26.5, 12.5, 180, 15),
					new Shot("f2-records", 2, 35.5, 22.5, -90, 10),
					new Shot("c1-morgue", -1, 14.5, 12.5, 180, 10),
					new Shot("c1-plant", -1, 36.5, 12.5, 180, 10),
					new Shot("c1-stores", -1, 23.5, 19.5, 0, 10),
					new Shot("c2-vestibule", -2, 23.5, 15.5, 0, 20),
					new Shot("c2-deep-ward", -2, 12.5, 26.5, -90, 10),
					new Shot("c2-chamber", -2, 23.5, 27.5, 180, 25),
					new Shot("roof", 3, 24.5, 26.5, 180, 25))),
			new Landmark("kiln", "ashfall:kiln_warehouse_00", -60, 7, 2, 2, 0, 2, List.of(
					new Shot("hall", 0, 18.5, 28.5, 180, 10),
					new Shot("lair", 0, 15.5, 19.5, 180, 20),
					new Shot("kilns", 0, 15.5, 8.5, 180, 10),
					new Shot("racks", 0, 25.5, 6.5, -60, 15),
					new Shot("office", 0, 8.5, 29.5, 180, 15),
					new Shot("gallery", 1, 8.5, 25.5, 180, 25))),
			new Landmark("pumphouse", "ashfall:substation_river_arts_00", -55, 4, 2, 2, 1, 1, List.of(
					new Shot("hall", 0, 16.5, 27.5, 180, 20),
					new Shot("channel", 0, 10.5, 20.5, 180, 40),
					new Shot("control", 0, 23.5, 5.0, -120, 20),
					new Shot("c1-channel", -1, 5.5, 12.0, -90, 20),
					new Shot("c1-pumps", -1, 16.5, 12.0, 180, 10),
					new Shot("c1-tanks", -1, 16.5, 27.5, 180, 10))),
			new Landmark("brewery", "ashfall:substation_south_slope_00", 5, 49, 2, 2, 0, 2, List.of(
					new Shot("yard", 0, 15.5, 29.5, 180, 10),
					new Shot("brewhouse", 0, 10.5, 9.5, 180, 10),
					new Shot("bar", 0, 20.5, 12.0, 180, 15),
					new Shot("control", 0, 24.5, 28.5, -135, 30),
					new Shot("f1", 1, 12.5, 10.5, 180, 25))),
			new Landmark("parkdeck", "ashfall:substation_downtown_00", 3, -2, 2, 2, 0, 3, List.of(
					new Shot("entrance", 0, 12.5, 28.5, 180, 5),
					new Shot("deck0", 0, 12.5, 20.5, -60, 10),
					new Shot("ramp", 0, 24.5, 22.5, 180, 20),
					new Shot("control", 0, 22.5, 28.5, -135, 25),
					new Shot("deck1", 1, 12.5, 20.5, 0, 10),
					new Shot("stairs", 0, 2.5, 5.5, 180, 25),
					new Shot("roof", 3, 12.5, 20.5, 180, 15))),
			new Landmark("generator", "ashfall:substation_mission_00", 48, 39, 2, 2, 0, 2, List.of(
					new Shot("hall", 0, 16.5, 15.5, 180, 10),
					new Shot("gensets", 0, 10.5, 12.5, 150, 10),
					new Shot("control", 1, 8.5, 27.5, -100, 20),
					new Shot("overlook", 1, 23.5, 26.5, 180, 10, 1),
					new Shot("workshop", 0, 8.5, 26.5, 180, 15))));

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
			context.waitTicks(20);
			BlockPos start = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			String startBuilding = server.computeOnServer(s -> LostCityContext.chunk(s.overworld(), start.getX() >> 4, start.getZ() >> 4)
					.map(CityChunk::buildingId).orElse(null));
			line("player starts at " + start.toShortString() + " in " + startBuilding);
			if (!"ashfall:safehouse".equals(startBuilding)) {
				failures.add("new player starts in " + startBuilding + ", not the safehouse");
			}
			context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("landmark-00-spawn-view");
			server.runCommand("gamemode spectator @a");
			server.runCommand("effect give @a minecraft:night_vision infinite 0 true");

			for (Landmark lm : LANDMARKS) {
				visit(context, world, server, lm);
			}
		}
		try {
			Files.writeString(Path.of("landmark-tour.txt"), log.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Landmark tour: " + String.join("; ", failures));
		}
	}

	private void line(String s) {
		log.append(s).append('\n');
		System.out.println("[LANDMARKS] " + s);
	}

	private void visit(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, Landmark lm) {
		int bx = lm.cx() << 4, bz = lm.cz() << 4;
		// Park above the landmark first so its chunks generate and render.
		server.runCommand(String.format("tp @a %d 160 %d", bx + lm.w() * 8, bz + lm.h() * 8));
		context.waitTicks(10);
		world.getConnection().waitForChunksRender();
		CityChunk plan = server.computeOnServer(s -> LostCityContext.chunk(s.overworld(), lm.cx(), lm.cz()).orElse(null));
		line("== " + lm.name() + " at chunk " + lm.cx() + ", " + lm.cz() + ": " + plan);
		if (plan == null || !lm.building().equals(plan.buildingId())) {
			failures.add(lm.name() + " expected " + lm.building() + " at chunk " + lm.cx() + ", " + lm.cz()
					+ " but Lost Cities planned " + (plan == null ? "nothing" : plan.buildingId()));
			return;
		}
		int ground = plan.groundY();
		int top = ground + lm.floors() * 6;
		LandmarkCensus census = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			return LandmarkCensus.scan(level, bx, ground - lm.cellars() * 6, bz, bx + lm.w() * 16 - 1, top + 8, bz + lm.h() * 16 - 1);
		});
		LandmarkCensus belowRoof = server.computeOnServer(s -> LandmarkCensus.scan(s.overworld(),
				bx, ground - lm.cellars() * 6, bz, bx + lm.w() * 16 - 1, top - 1, bz + lm.h() * 16 - 1));
		line("   census " + census);
		if (belowRoof.ladders() > 0) {
			failures.add(lm.name() + " has " + belowRoof.ladders() + " ladders below the roof");
		}
		if (census.lights() > 0) {
			failures.add(lm.name() + " has light-emitting blocks " + census.lightBlocks());
		}

		// Exterior from the street (south side) and from above.
		double cxm = bx + lm.w() * 8.0;
		server.runCommand(String.format("tp @a %.1f %d %.1f 180 5", cxm, ground + 3, bz + lm.h() * 16 + 10.0));
		shoot(context, world, lm.name() + "-street");
		// From above the south-west, aimed at the landmark's centre.
		double ccx = bx + lm.w() * 8.0, ccz = bz + lm.h() * 8.0;
		double camX = bx - 12.0, camZ = bz + lm.h() * 16 + 12.0, camY = top + 14 + 6 * Math.max(lm.w(), lm.h());
		double ddx = ccx - camX, ddz = ccz - camZ;
		float yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
		float pitch = (float) Math.toDegrees(Math.atan2(camY - top, Math.hypot(ddx, ddz)));
		server.runCommand(String.format("tp @a %.1f %.0f %.1f %.1f %.1f", camX, camY, camZ, yaw, pitch));
		shoot(context, world, lm.name() + "-aerial");
		for (Shot shot : lm.shots()) {
			int y = ground + shot.floor() * 6 + 1 + shot.lift();
			server.runCommand(String.format("tp @a %.2f %d %.2f %.1f %.1f", bx + shot.x(), y, bz + shot.z(), shot.yaw(), shot.pitch()));
			shoot(context, world, lm.name() + "-" + shot.label());
		}
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		context.waitTicks(4);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("landmark-" + name);
	}
}
