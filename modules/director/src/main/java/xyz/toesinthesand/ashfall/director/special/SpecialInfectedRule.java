package xyz.toesinthesand.ashfall.director.special;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * One special infected from {@code data/<ns>/ashfall/special_infected/<kind>.json}: when and where the Director
 * may spawn it, how many may be alive, its stats and its behaviour numbers. The file name is the kind
 * ({@link SpecialKind#id()}); a file for an unknown kind is ignored with a warning.
 * <p>
 * Weights are per mille rather than relative like zombie variants: {@code weight: 20} means 20 of every 1000
 * Director spawns become this special (before zone multipliers). That keeps specials an exception layered over the
 * variant table instead of a share of it, so adding a new walker variant never dilutes or inflates them, and an
 * admin can read the real rate straight off the file.
 *
 * @param weight              spawns per 1000 Director spawns once {@code min_day} is reached
 * @param minDay              first world day (1-based) the special can appear
 * @param zoneMultiplier      zone id -> multiplier on the weight (Mission breeds bloated and spitters, the
 *                            wasteland brutes)
 * @param maxAlivePerPlayer   cap on this special alive within {@link SpecialInfected#CAP_RADIUS} of a spawn, per
 *                            player in that radius (the "at most 2 screamers per player" rule)
 * @param siegeMultiplier     multiplier on the weight for siege waves and horde nights, which should feel worse
 *                            than a quiet street
 * @param attributes          attribute id -> base value, applied on every spawn (eggs and commands too); the
 *                            entity's code defaults are the same numbers, so a missing file changes nothing
 * @param tuning              behaviour numbers by name (cloud radius, scream radius, spit cooldown, ...); each
 *                            entity documents the keys it reads and their defaults
 */
public record SpecialInfectedRule(int weight, int minDay, Map<String, Float> zoneMultiplier, int maxAlivePerPlayer,
								  float siegeMultiplier, Map<Identifier, Double> attributes, Map<String, Float> tuning) {
	/** The weight scale: weights are spawns per this many Director spawns. */
	public static final double PER = 1000.0;

	public static final Codec<SpecialInfectedRule> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 1000).fieldOf("weight").forGetter(SpecialInfectedRule::weight),
			Codec.intRange(0, 100_000).optionalFieldOf("min_day", 1).forGetter(SpecialInfectedRule::minDay),
			Codec.unboundedMap(Codec.STRING, Codec.floatRange(0, 100)).optionalFieldOf("zone_multiplier", Map.of())
					.forGetter(SpecialInfectedRule::zoneMultiplier),
			Codec.intRange(0, 1000).optionalFieldOf("max_alive_per_player", 4).forGetter(SpecialInfectedRule::maxAlivePerPlayer),
			Codec.floatRange(0, 100).optionalFieldOf("siege_multiplier", 1f).forGetter(SpecialInfectedRule::siegeMultiplier),
			Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("attributes", Map.of()).forGetter(SpecialInfectedRule::attributes),
			Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("tuning", Map.of()).forGetter(SpecialInfectedRule::tuning)
	).apply(i, SpecialInfectedRule::new));

	/** A rule that never spawns and tunes nothing: what an entity uses when its file is missing. */
	public static final SpecialInfectedRule NONE = new SpecialInfectedRule(0, 1, Map.of(), 0, 1f, Map.of(), Map.of());

	/** A behaviour number, or {@code fallback} when the file does not set it. */
	public float tuning(String key, float fallback) {
		return tuning.getOrDefault(key, fallback);
	}

	/**
	 * Effective weight for a spawn on {@code day} in {@code zone}, times {@code multiplier} (1 for ambient spawns,
	 * {@link #siegeMultiplier} for sieges and hordes). 0 before {@code min_day}.
	 */
	public double weightFor(int day, String zone, float multiplier) {
		if (day < minDay) {
			return 0;
		}
		return weight * zoneMultiplier.getOrDefault(zone, 1f) * multiplier;
	}

	/** Whether {@code alive} specials of this kind near a spawn, with {@code players} players around, leave room. */
	public boolean underCap(int alive, int players) {
		return alive < maxAlivePerPlayer * Math.max(1, players);
	}
}
