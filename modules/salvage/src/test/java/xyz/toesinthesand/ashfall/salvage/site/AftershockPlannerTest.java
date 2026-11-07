package xyz.toesinthesand.ashfall.salvage.site;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aftershock crash-site passes: each pass is a fixed, different scatter at a scaled chance, with its own keys. */
class AftershockPlannerTest {
	private static final long STEP = 0x5EED_A57L;
	private static final CrashSiteRule RIDGE = new CrashSiteRule(List.of("ridge"), 10, 0.4f, 2300,
			List.of(new CrashSiteRule.WeightedTemplate("ashfall:crash_site_small", 1)), CrashSiteRule.Guards.NONE, 64);

	@Test
	void passKeepsTheRuleButMovesTheSaltAndScalesTheChance() {
		CrashSiteRule pass = CrashSitePlanner.aftershockRule(RIDGE, 2, 0.5f, STEP);
		assertEquals(RIDGE.zones(), pass.zones());
		assertEquals(RIDGE.templates(), pass.templates());
		assertEquals(0.2f, pass.chance(), 1e-6);
		assertEquals(RIDGE.salt() + 2 * STEP, pass.salt());
	}

	@Test
	void passesScatterDifferentlyAndKeepDistinctKeys() {
		Set<String> first = new HashSet<>(), second = new HashSet<>();
		Map<String, CrashSiteRule> one = Map.of("ashfall:ridge", CrashSitePlanner.aftershockRule(RIDGE, 1, 1f, STEP));
		Map<String, CrashSiteRule> two = Map.of("ashfall:ridge", CrashSitePlanner.aftershockRule(RIDGE, 2, 1f, STEP));
		for (CrashSitePlanner.Planned p : CrashSitePlanner.inBox(7L, one, -4000, -4000, 4000, 4000, (x, z) -> "ridge",
				CrashSitePlanner.ChunkFilter.ANY)) {
			first.add(p.chunkX() + "," + p.chunkZ());
			assertTrue(CrashSitePlanner.aftershockKey(1, p).startsWith("aftershock1:ashfall:ridge:"));
		}
		for (CrashSitePlanner.Planned p : CrashSitePlanner.inBox(7L, two, -4000, -4000, 4000, 4000, (x, z) -> "ridge",
				CrashSitePlanner.ChunkFilter.ANY)) {
			second.add(p.chunkX() + "," + p.chunkZ());
		}
		assertTrue(!first.isEmpty() && !second.isEmpty());
		assertNotEquals(first, second, "two passes picked the same chunks");
		// The same pass is the same scatter every time (a pure function of the seed).
		assertEquals(first.size(), CrashSitePlanner.inBox(7L, one, -4000, -4000, 4000, 4000, (x, z) -> "ridge",
				CrashSitePlanner.ChunkFilter.ANY).size());
	}
}
