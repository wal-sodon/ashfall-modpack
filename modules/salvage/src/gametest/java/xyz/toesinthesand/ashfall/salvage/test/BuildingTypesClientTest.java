package xyz.toesinthesand.ashfall.salvage.test;

import mcjty.lostcities.setup.Config;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Photographs one example of each loot building type (home, shop, workshop, police station, government, ...) for the
 * wiki: run/screenshots/building-&lt;profile&gt;-{street,inside}.png, plus run/building-types.txt naming the
 * building, zone and position of each. The examples are found from the Lost Cities plan, so the pictures show the
 * buildings players actually meet; it fails only if a common type (home, shop, office, workshop, police) has none.
 * <p>
 * The only client gametest in salvage, so {@code ./gradlew :salvage:runClientGameTest} runs just this.
 */
public class BuildingTypesClientTest implements FabricClientGameTest {
	/** Profiles to photograph, in wiki order. Fixed landmarks (safehouse, hospital) are covered by the landmark tour. */
	static final List<String> TYPES = List.of("home", "shop", "grocery", "pharmacy", "gym", "office", "bank", "hotel",
			"workshop", "warehouse", "police", "government", "school", "electronics", "restaurant", "bar", "brewery",
			"clinic", "museum", "fuel", "station");
	static final List<String> REQUIRED = List.of("home", "shop", "office", "workshop", "police");
	/** Where to look, in order: the start, the suburbs, then each district (block x, z of the centre). */
	static final int[][] CENTRES = {{-150, -800}, {-500, -350}, {-900, 100}, {0, 0}, {100, 800}, {750, 650}};
	static final int RADIUS_CHUNKS = 22;

	/** Sides to look from, in order of preference: south, east, west, north (x, z step and the yaw facing back). */
	static final int[][] SIDES = {{0, 1, 180}, {1, 0, 90}, {-1, 0, -90}, {0, -1, 0}};

	/** {@code side} indexes SIDES: the side whose neighbouring chunks are all open street, so the camera is outside. */
	record Example(String type, CityChunk origin, String building, String zone, int side, int streetY) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		Config.profileFromClient = LostCityContext.PROFILE;
		Config.resetProfileCache();
		StringBuilder log = new StringBuilder();
		List<String> missing = new ArrayList<>();
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
			server.runCommand("gamemode spectator @a");
			server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			Map<String, Example> found = server.computeOnServer(s -> find(s.overworld()));
			for (String type : TYPES) {
				Example ex = found.get(type);
				if (ex == null) {
					log.append(type).append(": none found\n");
					if (REQUIRED.contains(type)) {
						missing.add(type);
					}
					continue;
				}
				CityChunk o = ex.origin();
				log.append(String.format("%s: %s in %s at %d, %d (%dx%d chunks, %d floors)%n", type, ex.building(), ex.zone(),
						o.originX() << 4, o.originZ() << 4, o.multiW(), o.multiH(), o.floors()));
				shoot(context, world, server, ex);
			}
		}
		try {
			Files.writeString(Path.of("building-types.txt"), log.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println("[BUILDINGS]\n" + log);
		if (!missing.isEmpty()) {
			throw new AssertionError("no example building for " + missing);
		}
	}

	/** The first intact example of each type, searching outward from each centre. Plans only: nothing generates. */
	private static Map<String, Example> find(ServerLevel level) {
		Map<String, Example> found = new LinkedHashMap<>();
		for (int[] centre : CENTRES) {
			int ccx = centre[0] >> 4, ccz = centre[1] >> 4;
			List<int[]> chunks = new ArrayList<>();
			for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
				for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
					chunks.add(new int[]{ccx + dx, ccz + dz});
				}
			}
			chunks.sort(Comparator.comparingInt(c -> (c[0] - ccx) * (c[0] - ccx) + (c[1] - ccz) * (c[1] - ccz)));
			for (int[] c : chunks) {
				CityChunk plan = LostCityContext.chunk(level, c[0], c[1]).orElse(null);
				// Origin chunks only (one visit per building), intact, at least two storeys so it reads as a building.
				// The Downtown landmark station classifies as police_armory, so "police" finds a random precinct.
				if (plan == null || !plan.hasBuilding() || plan.multiX() != 0 || plan.multiZ() != 0
						|| plan.ruinLevel() >= 0 || plan.floors() < 2) {
					continue;
				}
				Building building = AshfallSalvage.buildings().inChunk(c[0], c[1]).stream().findFirst().orElse(null);
				if (building == null) {
					continue;
				}
				Identifier id = BuildingProfiles.classifyByTags(building);
				String type = id == null ? null : id.getPath();
				if (type == null || !TYPES.contains(type) || found.containsKey(type)) {
					continue;
				}
				int[] street = streetSide(level, plan);
				if (street != null) {
					found.put(type, new Example(type, plan,
							plan.multiBuilding() != null ? plan.multiBuilding() : plan.buildingId(),
							building.tags().getOrDefault("lc_zone", "?"), street[0], street[1]));
				}
			}
		}
		return found;
	}

	/** The first side of the building whose whole row of neighbouring chunks has no building, and that street's height. */
	private static int[] streetSide(ServerLevel level, CityChunk o) {
		for (int i = 0; i < SIDES.length; i++) {
			int sx = SIDES[i][0], sz = SIDES[i][1];
			int len = sx == 0 ? o.multiW() : o.multiH();
			int y = Integer.MIN_VALUE;
			boolean open = true;
			for (int k = 0; k < len && open; k++) {
				int nx = sx == 0 ? o.originX() + k : sx > 0 ? o.originX() + o.multiW() : o.originX() - 1;
				int nz = sz == 0 ? o.originZ() + k : sz > 0 ? o.originZ() + o.multiH() : o.originZ() - 1;
				CityChunk n = LostCityContext.chunk(level, nx, nz).orElse(null);
				open = n != null && !n.hasBuilding();
				if (open) {
					y = Math.max(y, n.groundY());
				}
			}
			if (open) {
				return new int[]{i, y};
			}
		}
		return null;
	}

	/** A street view from the open side and the ground floor. (Aerials showed the whole block, not which building.) */
	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, Example ex) {
		CityChunk o = ex.origin();
		int bx = o.originX() << 4, bz = o.originZ() << 4, w = o.multiW() * 16, h = o.multiH() * 16;
		int ground = o.groundY(), top = ground + o.floors() * 6;
		double cx = bx + w / 2.0, cz = bz + h / 2.0;
		server.runCommand(String.format("tp @a %.1f %d %.1f", cx, top + 30, cz));
		context.waitTicks(10);
		world.getConnection().waitForChunksRender();
		// From across the street, tilted up just enough to fit the roof (taller buildings lose the pavement first).
		int[] side = SIDES[ex.side()];
		double d = 13;
		double camX = side[0] > 0 ? bx + w + d : side[0] < 0 ? bx - d : cx;
		double camZ = side[1] > 0 ? bz + h + d : side[1] < 0 ? bz - d : cz;
		int eye = ex.streetY() + 2;
		double up = Math.toDegrees(Math.atan2(top - eye, d));
		float pitch = (float) -Math.max(5, Math.min(40, up - 28));
		server.runCommand(String.format("tp @a %.1f %d %.1f %d %.1f", camX, eye, camZ, side[2], pitch));
		capture(context, world, ex.type() + "-street");

		// Inside, a few blocks in from the street wall, looking across the ground floor.
		double inX = side[0] > 0 ? bx + w - 4 : side[0] < 0 ? bx + 4 : cx;
		double inZ = side[1] > 0 ? bz + h - 4 : side[1] < 0 ? bz + 4 : cz;
		server.runCommand(String.format("tp @a %.1f %d %.1f %d 20", inX, ground + 2, inZ, side[2]));
		capture(context, world, ex.type() + "-inside");
	}

	private static void capture(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		context.waitTicks(4);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("building-" + name);
	}
}
