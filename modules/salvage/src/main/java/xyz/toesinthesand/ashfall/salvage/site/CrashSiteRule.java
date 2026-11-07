package xyz.toesinthesand.ashfall.salvage.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Where Wreck crash sites scatter, from {@code data/<ns>/ashfall/crash_sites/<id>.json} (one rule per zone
 * group: the ridge is dense around the hull, the wasteland ring sparse).
 * <p>
 * The world is cut into square regions of {@code regionChunks} chunks. Each region rolls once, from the world
 * seed, the rule's {@code salt} and the region coordinates, whether it holds a site, which chunk inside it
 * and which template. That makes the layout a pure function of the seed (the same world always has the same
 * sites, before anyone visits them), and keeps two sites from ever being closer than about one region's
 * margin, without any search.
 *
 * @param zones          zone ids a site may land in (judged at the chosen chunk's centre)
 * @param regionChunks   region edge in chunks
 * @param chance         chance that a region holds a site
 * @param salt           mixed into the seed so two rules never pick the same chunks
 * @param templates      weighted site templates
 * @param guards         zombies that rise around the site when a player first comes near
 * @param avoidFixed     no site within this many blocks of a fixed site (the hull keeps its clearing)
 */
public record CrashSiteRule(List<String> zones, int regionChunks, float chance, long salt, List<WeightedTemplate> templates,
		Guards guards, int avoidFixed) {

	public record WeightedTemplate(String template, int weight) {
		public static final Codec<WeightedTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("template").forGetter(WeightedTemplate::template),
				Codec.intRange(1, 10_000).optionalFieldOf("weight", 1).forGetter(WeightedTemplate::weight)
		).apply(i, WeightedTemplate::new));
	}

	/** A light guard: {@code min..max} zombies within {@code radius} blocks of the site. */
	public record Guards(int min, int max, int radius) {
		public static final Guards NONE = new Guards(0, 0, 0);
		public static final Codec<Guards> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 32).optionalFieldOf("min", 0).forGetter(Guards::min),
				Codec.intRange(0, 32).optionalFieldOf("max", 0).forGetter(Guards::max),
				Codec.intRange(0, 64).optionalFieldOf("radius", 6).forGetter(Guards::radius)
		).apply(i, Guards::new));
	}

	public static final Codec<CrashSiteRule> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().fieldOf("zones").forGetter(CrashSiteRule::zones),
			Codec.intRange(3, 256).fieldOf("region_chunks").forGetter(CrashSiteRule::regionChunks),
			Codec.floatRange(0, 1).fieldOf("chance").forGetter(CrashSiteRule::chance),
			Codec.LONG.optionalFieldOf("salt", 0L).forGetter(CrashSiteRule::salt),
			WeightedTemplate.CODEC.listOf().fieldOf("templates").forGetter(CrashSiteRule::templates),
			Guards.CODEC.optionalFieldOf("guards", Guards.NONE).forGetter(CrashSiteRule::guards),
			Codec.intRange(0, 1024).optionalFieldOf("avoid_fixed", 48).forGetter(CrashSiteRule::avoidFixed)
	).apply(i, CrashSiteRule::new));
}
