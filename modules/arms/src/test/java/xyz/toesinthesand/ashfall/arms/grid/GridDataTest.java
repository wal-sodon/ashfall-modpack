package xyz.toesinthesand.ashfall.arms.grid;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped grid JSON parses with the codecs, carries the designed numbers, and the siege tier rule holds. */
class GridDataTest {
	private static JsonElement json(String path) throws IOException {
		try (InputStream in = GridDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing resource " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	/**
	 * The shipped core reach covers a zombie pressed against the control room's outer wall (River Arts: 4.3 blocks
	 * from the core in the Phase 3 playtest, every room 5-6 blocks), but not one on the roof or in the cellar.
	 */
	@Test
	void coreReachCoversTheControlRoomWallsOnly() throws IOException {
		GridSettings s = GridSettings.CODEC.parse(JsonOps.INSTANCE, json("/data/ashfall/ashfall/grid/settings.json")).getOrThrow();
		GridSettings.Core core = s.core();
		net.minecraft.world.phys.Vec3 centre = new net.minecraft.world.phys.Vec3(-853.5, 103.5, 67.5);
		assertTrue(GridSiege.attacking(new net.minecraft.world.phys.Vec3(-853.7, 102, 63.2), centre, core),
				"the River Arts wave against the north wall");
		assertTrue(GridSiege.attacking(new net.minecraft.world.phys.Vec3(-853.5, 102, 73.0), centre, core),
				"outside a room wall 5.5 blocks away");
		org.junit.jupiter.api.Assertions.assertFalse(GridSiege.attacking(new net.minecraft.world.phys.Vec3(-853.5, 109, 64), centre, core),
				"on the roof above");
		org.junit.jupiter.api.Assertions.assertFalse(GridSiege.attacking(new net.minecraft.world.phys.Vec3(-853.5, 96, 67.5), centre, core),
				"in the cellar below");
		org.junit.jupiter.api.Assertions.assertFalse(GridSiege.attacking(new net.minecraft.world.phys.Vec3(-842.5, 102, 40.5), centre, core),
				"out on the street");
	}

	@Test
	void shippedSettingsParse() throws IOException {
		GridSettings s = GridSettings.CODEC.parse(JsonOps.INSTANCE, json("/data/ashfall/ashfall/grid/settings.json")).getOrThrow();
		assertEquals(4, s.couplersRequired());
		assertEquals(4, s.cellsRequired());
		assertEquals(300, s.energizeSeconds(), "a 5-minute hold");
		assertEquals(32, s.holdRadius());
		assertEquals(144, s.humRadius(), "3x the relay's 48");
		assertEquals(0.2f, s.reclaimed().darknessScale(), 1e-6, "darkness spawning -80%");
		assertTrue(s.rewards("river_arts").cityPump(), "River Arts runs the city pump");
		assertEquals("ashfall:patient_zero", s.rewards("mission").unsealLair().orElseThrow().toString());
		assertEquals("ashfall_cache:grid_reward_downtown", s.rewardTable("downtown").toString());
		LoopRules rules = s.rules();
		assertEquals(6000, rules.holdTicks());
		assertEquals(11_000, rules.nightTicks(), "nightfall 12000 to dawn 23000");
	}

	@Test
	void shippedSiegeTablesParse() throws IOException {
		SiegeSettings s = SiegeSettings.CODEC.parse(JsonOps.INSTANCE, json("/data/ashfall/ashfall/grid/siege.json")).getOrThrow();
		assertEquals(6, s.tiers().size());
		for (SiegeSettings.Tier tier : s.tiers()) {
			assertTrue(!tier.variants().isEmpty(), "tier " + tier.tier() + " has no variants");
		}
		// balance-v1: waves scale with the party, so the budget is per wave at four players, two waves overlapping
		// (next_wave_alive_at_most lets the next one in early) inside the Director's 150-zombie world cap
		assertTrue(s.waveSize(s.tier(6), 4) * 2 <= 150, "the top siege's waves fit the Director's world cap");
		for (SiegeSettings.Tier tier : s.tiers()) {
			assertTrue(tier.coreHp(0) > 0, "tier " + tier.tier() + " sets its own core health");
			// an undefended core lasts 90-120 s against one solo wave at 1 HP per zombie-second (cut 35% so leakers matter)
			double seconds = tier.coreHp(0) / (double) tier.size();
			assertTrue(seconds >= 90 && seconds <= 120, "tier " + tier.tier() + " core lasts " + seconds + " s");
		}
		assertEquals(s.tier(2).size(), s.waveSize(s.tier(2), 0), "nobody counts as solo");
		assertTrue(s.waveSize(s.tier(2), 4) > s.waveSize(s.tier(2), 2), "bigger parties get bigger waves");
	}

	@Test
	void siegeTierIsTheMaxOfDayAndGrid() {
		SiegeSettings s = new SiegeSettings(java.util.List.of(new SiegeSettings.DayTier(1, 1), new SiegeSettings.DayTier(10, 3)),
				1, 18, 30, 90, 2, SiegeSettings.DEFAULT.tiers());
		assertEquals(2, s.siegeTier(1, 2, 0), "day 1 in a tier-2 district: the zone tier wins");
		assertEquals(3, s.siegeTier(10, 2, 0), "day 10: the day tier wins");
		assertEquals(4, s.siegeTier(10, 2, 3), "three districts lit: the grid tier wins");
		assertEquals(1, s.tier(5).tier(), "a missing tier falls back to the highest one below");
	}

	@Test
	void firstSiegeCapsOnlyTheDayTier() throws IOException {
		java.util.List<SiegeSettings.DayTier> days = java.util.List.of(new SiegeSettings.DayTier(1, 1), new SiegeSettings.DayTier(15, 4));
		SiegeSettings s = new SiegeSettings(days, 1, 18, 30, 90, 2, SiegeSettings.DEFAULT.tiers(), java.util.List.of(1.0), 2);
		assertEquals(2, s.siegeTier(20, 2, 0), "first siege on day 20: the day tier counts as II");
		assertEquals(4, s.siegeTier(20, 4, 0), "the district's own zone tier still applies (Mission)");
		assertEquals(4, s.siegeTier(20, 2, 1), "after the first reclaim the day tier is uncapped");
		SiegeSettings shipped = SiegeSettings.CODEC.parse(JsonOps.INSTANCE, json("/data/ashfall/ashfall/grid/siege.json")).getOrThrow();
		assertEquals(2, shipped.firstSiegeDayTierCap());
	}

	@Test
	void defaultsWithAnEmptyFile() {
		GridSettings s = GridSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
		assertEquals(GridSettings.DEFAULT.couplersRequired(), s.couplersRequired());
		assertEquals(GridSettings.DEFAULT.core().hp(), s.core().hp());
	}
}
