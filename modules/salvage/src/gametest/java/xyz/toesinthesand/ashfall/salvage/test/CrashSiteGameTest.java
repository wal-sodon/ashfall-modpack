package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.site.CrashSitePlanner;
import xyz.toesinthesand.ashfall.salvage.site.CrashSiteRule;
import xyz.toesinthesand.ashfall.salvage.site.PlacedSite;
import xyz.toesinthesand.ashfall.salvage.site.SiteState;
import xyz.toesinthesand.ashfall.salvage.site.SiteTemplate;
import xyz.toesinthesand.ashfall.salvage.site.Sites;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crash sites and the Wreck hull with the shipped data on a real server: the layout is a pure function of the
 * world seed, sites only land in the ridge and wasteland, the hull's position comes from the ridge zone file, and a
 * placed crash site has its loot chest wired to the crash-site table.
 */
public class CrashSiteGameTest {
	private static final CrashSitePlanner.ZoneLookup ZONES = (x, z) -> ZoneMap.zoneAt(x, z).id();

	@GameTest
	public void crashSiteLayoutIsDeterministicPerSeed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, CrashSiteRule> rules = Sites.rules();
		helper.assertTrue(rules.size() >= 2, "crash-site rules not loaded: " + rules.keySet());
		long seed = level.getSeed();
		List<CrashSitePlanner.Planned> a = CrashSitePlanner.inBox(seed, rules, -2600, -2600, 2600, 2600, ZONES, CrashSitePlanner.ChunkFilter.ANY);
		List<CrashSitePlanner.Planned> b = CrashSitePlanner.inBox(seed, rules, -2600, -2600, 2600, 2600, ZONES, CrashSitePlanner.ChunkFilter.ANY);
		List<CrashSitePlanner.Planned> other = CrashSitePlanner.inBox(seed + 1, rules, -2600, -2600, 2600, 2600, ZONES,
				CrashSitePlanner.ChunkFilter.ANY);
		helper.assertTrue(!a.isEmpty(), "no crash sites planned in the whole layout");
		helper.assertTrue(a.equals(b), "the same seed must give the same crash sites");
		helper.assertTrue(!a.equals(other), "a different seed should move the crash sites");
		boolean ridge = false, wasteland = false;
		for (CrashSitePlanner.Planned p : a) {
			String zone = ZoneMap.zoneAt(p.centerX(), p.centerZ()).id();
			helper.assertTrue(zone.equals("ridge") || zone.equals("wasteland"), p.key() + " landed in " + zone);
			helper.assertTrue(Sites.template(p.template()) != null, p.key() + " uses unknown template " + p.template());
			ridge |= zone.equals("ridge");
			wasteland |= zone.equals("wasteland");
		}
		helper.assertTrue(ridge && wasteland, "sites should appear in both the ridge and the wasteland");
		helper.succeed();
	}

	@GameTest
	public void hullPositionComesFromTheRidgeZone(GameTestHelper helper) {
		Zone.FixedSite hull = Sites.fixedSites().get("ashfall:wreck_hull");
		helper.assertTrue(hull != null, "the ridge zone should declare the Wreck hull site");
		helper.assertTrue(hull.x() == 2300 && hull.z() == -100, "hull at " + hull.x() + "," + hull.z());
		helper.assertTrue(ZoneMap.zoneAt(hull.x(), hull.z()).id().equals("ridge"), "hull must sit in the ridge");
		helper.assertTrue(Sites.template(hull.template()) != null, "hull template not loaded");
		helper.succeed();
	}

	@GameTest
	public void placedCrashSiteHasItsLootChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Identifier templateId = AshfallSalvage.id("crash_site_small");
		SiteTemplate template = Sites.templates().get(templateId);
		helper.assertTrue(template != null, "crash_site_small not loaded");
		for (int x = 0; x < template.width(); x++) {
			for (int z = 0; z < template.depth(); z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT);
				helper.setBlock(x, 1, z, Blocks.DIRT);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
		String key = "crash:test:" + UUID.randomUUID();
		PlacedSite site = Sites.placeAt(level, key, templateId, template, origin, origin.offset(5, template.sink(), 5), 1234L,
				new CrashSiteRule.Guards(1, 2, 5), 2);
		try {
			helper.assertTrue(site != null, "placement failed");
			helper.assertTrue(site.chests().size() == 1, "chests: " + site.chests());
			BlockEntity be = level.getBlockEntity(site.chests().getFirst());
			helper.assertTrue(be instanceof RandomizableContainer rc && rc.getLootTable() != null
					&& rc.getLootTable().identifier().toString().equals("ashfall:chests/crash_site"), "chest loot table: " + be);
			helper.assertTrue(be.hasAttached(AshfallSalvage.LOOT_STATE), "the atlas container pass must see the chest as managed");
			helper.assertTrue(site.guards() == 2, "guards owed: " + site.guards());
			helper.assertTrue(SiteState.get(level.getServer()).placed(key), "site recorded");
		} finally {
			SiteState.get(level.getServer()).forget(key);
		}
		helper.succeed();
	}

	/**
	 * Regression from the Phase 2 playtest: {@code /ashfall site place} on a site nobody had visited buried the hull at
	 * bedrock, because {@code Level#getHeight} reports the minimum Y for a chunk that is not loaded. {@link Sites#place}
	 * now loads its footprint before it looks for the surface.
	 */
	@GameTest
	public void placeFindsTheSurfaceInUnloadedChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 48_000 + level.getRandom().nextInt(4096) * 16, z = 48_000;
		helper.assertTrue(!level.hasChunk(x >> 4, z >> 4), "test column should start unloaded");
		String key = "crash:test:" + UUID.randomUUID();
		PlacedSite site = Sites.place(level, key, "ashfall:crash_site_small", x, z, 99L, CrashSiteRule.Guards.NONE, 0);
		try {
			helper.assertTrue(site != null, "placement failed");
			// Ground beside the footprint (the site's own blocks now cover its centre column).
			level.getChunk((x + 48) >> 4, z >> 4);
			int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 48, z);
			helper.assertTrue(site.center().getY() > level.getMinY(),
					"site surface " + site.center().getY() + " is at the world floor " + level.getMinY());
			helper.assertTrue(Math.abs(site.center().getY() - ground) <= 8, "site surface " + site.center().getY()
					+ " far from the ground at " + ground);
		} finally {
			SiteState.get(level.getServer()).forget(key);
		}
		helper.succeed();
	}
}
