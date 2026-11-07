package xyz.toesinthesand.ashfall.salvage.test;

import mcjty.lostcities.LostCities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.CitySpawn;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;

import java.util.Optional;

/**
 * The zone layout on a real 26.3 server with The Lost Cities loaded.
 * <p>
 * The gametest overworld is a flat world, where Lost Cities never builds (its ground is below
 * {@code cityMinHeight}), and we must not turn it into a city anyway: other tests build structures there.
 * So these tests use {@code ashfall_test:lc_city}, a dimension from this test mod's data whose noise
 * settings make level ground at y=70 everywhere, and register it with our {@code ashfall} profile through
 * Lost Cities' API. City placement then depends only on chunk coordinates and our hooks, not on the seed.
 */
public class CityZoneGameTest {
	private static final ResourceKey<Level> LC_CITY = ResourceKey.create(Registries.DIMENSION,
			Identifier.fromNamespaceAndPath("ashfall_test", "lc_city"));

	@GameTest
	public void zoneDataLoads(GameTestHelper helper) {
		helper.assertFalse(ZoneMap.current().isEmpty(), "no zone data loaded from data/ashfall/ashfall/zones");
		check(helper, 0, 0, "downtown");
		check(helper, -900, 100, "river_arts");
		check(helper, 100, 800, "south_slope");
		check(helper, 750, 650, "mission");
		check(helper, -150, -800, "montford");
		check(helper, 1000, -1000, "suburbs");
		check(helper, -2000, 1000, "wasteland");
		check(helper, 2300, -100, "ridge");
		check(helper, 3000, 0, "undiscovered");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, int x, int z, String expected) {
		String actual = ZoneMap.zoneAt(x, z).id();
		helper.assertTrue(expected.equals(actual), "zone at " + x + ", " + z + " is " + actual + ", expected " + expected);
	}

	@GameTest(maxTicks = 400)
	public void lostCitiesFollowsZones(GameTestHelper helper) {
		ServerLevel level = cityLevel(helper);
		helper.assertTrue(LostCityContext.isAshfall(level), "the test dimension is not on the ashfall profile");

		// Downtown (disc r 400 around the origin): Lost Cities' onlycities-style settings make nearly every
		// chunk city; allow a few misses for chunks where structure avoidance or rounding says no.
		int sampled = 0, city = 0;
		// Street chunks take the majority style of their 3x3 neighbourhood (Lost Cities keeps streets from
		// flickering between styles), so check styles one chunk in from the border.
		for (int cx = -24; cx <= 24; cx += 3) {
			for (int cz = -24; cz <= 24; cz += 3) {
				if (!"downtown".equals(ZoneMap.zoneAtChunk(cx, cz).id())) {
					continue;
				}
				CityChunk c = chunk(helper, level, cx, cz);
				sampled++;
				boolean inner = Math.hypot((cx << 4) + 8, (cz << 4) + 8) < 400 - 24;
				if (c.isCity()) {
					city++;
					helper.assertTrue(!inner || "ashfall:downtown".equals(c.cityStyle()),
							"downtown chunk " + cx + ", " + cz + " has style " + c.cityStyle());
				}
			}
		}
		helper.assertTrue(sampled > 40, "sampled only " + sampled + " downtown chunks");
		helper.assertTrue(city * 10 >= sampled * 9, "only " + city + " of " + sampled + " downtown chunks are city");

		// A district picks its own style.
		CityChunk river = firstCity(helper, level, -900 >> 4, 100 >> 4);
		helper.assertTrue("ashfall:river_arts".equals(river.cityStyle()), "River Arts style is " + river.cityStyle());

		// Wilderness: the Ridge wedge and everything past 2.6 km are never city.
		for (int i = 0; i < 8; i++) {
			int x = 1800 + i * 100, z = -800 + i * 200;
			helper.assertFalse(chunk(helper, level, x >> 4, z >> 4).isCity(), "ridge chunk at " + x + ", " + z + " is city");
			helper.assertFalse(chunk(helper, level, (3000 + i * 500) >> 4, (i * 700 - 2000) >> 4).isCity(),
					"undiscovered chunk " + i + " is city");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void spawnLandsOnAMontfordStreet(GameTestHelper helper) {
		ServerLevel level = cityLevel(helper);
		Optional<BlockPos> spawn = CitySpawn.findSpawn(level);
		helper.assertTrue(spawn.isPresent(), "no spawn found");
		BlockPos pos = spawn.get();
		helper.assertTrue("montford".equals(ZoneMap.zoneAt(pos.getX(), pos.getZ()).id()), "spawn " + pos + " is not in Montford");
		CityChunk c = chunk(helper, level, pos.getX() >> 4, pos.getZ() >> 4);
		helper.assertTrue(c.isStreet(), "spawn chunk is not a street: " + c);
		helper.assertTrue(pos.getY() >= 70, "spawn " + pos + " is below the city ground");
		helper.succeed();
	}

	private static ServerLevel cityLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(LC_CITY);
		helper.assertTrue(level != null, "test dimension ashfall_test:lc_city is missing");
		LostCities.lostCitiesImp.registerDimension(level, LC_CITY, LostCityContext.PROFILE);
		return level;
	}

	private static CityChunk chunk(GameTestHelper helper, ServerLevel level, int cx, int cz) {
		Optional<CityChunk> c = LostCityContext.chunk(level, cx, cz);
		helper.assertTrue(c.isPresent(), "Lost Cities has no info for the test dimension");
		return c.get();
	}

	private static CityChunk firstCity(GameTestHelper helper, ServerLevel level, int cx, int cz) {
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					CityChunk c = chunk(helper, level, cx + dx, cz + dz);
					if (c.isCity()) {
						return c;
					}
				}
			}
		}
		helper.fail("no city chunk near " + cx + ", " + cz);
		return null;
	}
}
