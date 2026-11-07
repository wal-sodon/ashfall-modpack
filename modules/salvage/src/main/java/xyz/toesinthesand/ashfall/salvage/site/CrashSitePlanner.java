package xyz.toesinthesand.ashfall.salvage.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure, seed-driven crash-site layout (see {@link CrashSiteRule}). No level state: the same seed, rules and
 * zone layout always give the same sites, which is what lets placement happen lazily (when a player first
 * comes near) without the layout depending on who went where first.
 */
public final class CrashSitePlanner {
	/** Answers "which zone owns this block column" (a pure lookup such as {@code ZoneMap::zoneAt}). */
	@FunctionalInterface
	public interface ZoneLookup {
		String zoneAt(int x, int z);
	}

	/** Answers "may a site go in this chunk" beyond zones: no Lost Cities building there, no fixed site near. */
	@FunctionalInterface
	public interface ChunkFilter {
		boolean allowed(int chunkX, int chunkZ);

		ChunkFilter ANY = (x, z) -> true;
	}

	/**
	 * One planned site.
	 *
	 * @param key      stable id: {@code crash:<rule>:<chunkX>,<chunkZ>}
	 * @param centerX  block x of the site's centre column (the chunk's centre)
	 * @param centerZ  block z of the site's centre column
	 * @param lootSeed seed for the site's containers, so the loot is fixed per world too
	 */
	public record Planned(String key, String rule, String template, int chunkX, int chunkZ, int centerX, int centerZ,
			long lootSeed) {
	}

	private CrashSitePlanner() {
	}

	/** SplitMix64 finaliser: cheap and well mixed, so neighbouring regions do not get correlated rolls. */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	static long regionHash(long seed, long salt, int regionX, int regionZ) {
		return mix(seed ^ mix(salt + 0x9E3779B97F4A7C15L) ^ mix(((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL)));
	}

	/** Uniform in [0, 1) from the top 53 bits. */
	static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}

	/**
	 * The site region (regionX, regionZ) holds under {@code rule}, ignoring zones and filters: whether the
	 * region rolled a site at all, and if so where and which template.
	 */
	public static Optional<Planned> roll(long seed, String ruleId, CrashSiteRule rule, int regionX, int regionZ) {
		if (rule.templates().isEmpty() || rule.chance() <= 0) {
			return Optional.empty();
		}
		long h = regionHash(seed, rule.salt(), regionX, regionZ);
		if (unit(h) >= rule.chance()) {
			return Optional.empty();
		}
		int size = rule.regionChunks();
		// Keep one chunk of margin on each side so sites in neighbouring regions are never adjacent.
		int span = Math.max(1, size - 2);
		long h2 = mix(h + 1);
		int offX = 1 + (int) Math.floorMod(h2, (long) span);
		int offZ = 1 + (int) Math.floorMod(h2 >>> 20, (long) span);
		int chunkX = regionX * size + Math.min(offX, size - 1);
		int chunkZ = regionZ * size + Math.min(offZ, size - 1);
		long h3 = mix(h + 2);
		String template = pickTemplate(rule, unit(h3));
		return Optional.of(new Planned("crash:" + ruleId + ":" + chunkX + "," + chunkZ, ruleId, template, chunkX, chunkZ,
				(chunkX << 4) + 8, (chunkZ << 4) + 8, mix(h + 3)));
	}

	static String pickTemplate(CrashSiteRule rule, double roll) {
		int total = 0;
		for (CrashSiteRule.WeightedTemplate t : rule.templates()) {
			total += t.weight();
		}
		double target = roll * total;
		for (CrashSiteRule.WeightedTemplate t : rule.templates()) {
			target -= t.weight();
			if (target < 0) {
				return t.template();
			}
		}
		return rule.templates().getLast().template();
	}

	/** True when the planned site's centre lies in one of the rule's zones and the filter allows its chunk. */
	public static boolean accepted(Planned site, CrashSiteRule rule, ZoneLookup zones, ChunkFilter filter) {
		return rule.zones().contains(zones.zoneAt(site.centerX(), site.centerZ())) && filter.allowed(site.chunkX(), site.chunkZ());
	}

	/**
	 * Every accepted site whose centre lies within {@code radius} blocks
	 * of (x, z), over all rules, in a stable order (rule id, then region) when {@code rules} is sorted.
	 */
	public static List<Planned> near(long seed, Map<String, CrashSiteRule> rules, int x, int z, int radius, ZoneLookup zones,
			ChunkFilter filter) {
		List<Planned> out = new ArrayList<>();
		for (Map.Entry<String, CrashSiteRule> e : rules.entrySet()) {
			CrashSiteRule rule = e.getValue();
			int regionBlocks = rule.regionChunks() * 16;
			int minRX = Math.floorDiv(x - radius, regionBlocks), maxRX = Math.floorDiv(x + radius, regionBlocks);
			int minRZ = Math.floorDiv(z - radius, regionBlocks), maxRZ = Math.floorDiv(z + radius, regionBlocks);
			for (int rx = minRX; rx <= maxRX; rx++) {
				for (int rz = minRZ; rz <= maxRZ; rz++) {
					Optional<Planned> site = roll(seed, e.getKey(), rule, rx, rz);
					if (site.isEmpty()) {
						continue;
					}
					Planned p = site.get();
					long dx = p.centerX() - x, dz = p.centerZ() - z;
					if (dx * dx + dz * dz <= (long) radius * radius && accepted(p, rule, zones, filter)) {
						out.add(p);
					}
				}
			}
		}
		return out;
	}

	/** Every accepted site whose centre lies in a block box (tests and the {@code /ashfall boss sites} listing). */
	public static List<Planned> inBox(long seed, Map<String, CrashSiteRule> rules, int minX, int minZ, int maxX, int maxZ,
			ZoneLookup zones, ChunkFilter filter) {
		List<Planned> out = new ArrayList<>();
		for (Map.Entry<String, CrashSiteRule> e : rules.entrySet()) {
			CrashSiteRule rule = e.getValue();
			int regionBlocks = rule.regionChunks() * 16;
			for (int rx = Math.floorDiv(minX, regionBlocks); rx <= Math.floorDiv(maxX, regionBlocks); rx++) {
				for (int rz = Math.floorDiv(minZ, regionBlocks); rz <= Math.floorDiv(maxZ, regionBlocks); rz++) {
					roll(seed, e.getKey(), rule, rx, rz)
							.filter(p -> p.centerX() >= minX && p.centerX() <= maxX && p.centerZ() >= minZ && p.centerZ() <= maxZ)
							.filter(p -> accepted(p, rule, zones, filter))
							.ifPresent(out::add);
				}
			}
		}
		return out;
	}

	/**
	 * Aftershock pass {@code generation} (1-based) of {@code rule}: the same zones, templates and guards, the salt moved
	 * by {@code generation * saltStep} and the chance scaled. The layout of every pass is as fixed per world as the
	 * original one, and each pass picks different chunks.
	 */
	public static CrashSiteRule aftershockRule(CrashSiteRule rule, int generation, float chanceScale, long saltStep) {
		return new CrashSiteRule(rule.zones(), rule.regionChunks(), Math.clamp(rule.chance() * chanceScale, 0f, 1f),
				rule.salt() + generation * saltStep, rule.templates(), rule.guards(), rule.avoidFixed());
	}

	/** The record key of an aftershock site: unlike the original pass's {@code crash:} keys, so both can coexist. */
	public static String aftershockKey(int generation, Planned planned) {
		return "aftershock" + generation + ":" + planned.rule() + ":" + planned.chunkX() + "," + planned.chunkZ();
	}
}
