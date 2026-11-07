package xyz.toesinthesand.ashfall.arms.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Map;

/**
 * Transit network tuning, from {@code data/ashfall/ashfall/travel/travel.json}.
 * <p>
 * A trip costs {@code base_cost} plus {@code cost_per_100_blocks} for every 100 blocks (rounded up), at most
 * {@code max_cost}. Why energy and why capped: travel should pull on the same Cell Packs the blasters use, so a
 * trip is a choice against ammunition, but a cross-map trip must still fit in one Standard pack (60).
 * <p>
 * Trips leaving a Bunker Core cost {@code bunker_departure_multiplier} times as much: a Field Charger refills
 * packs for free inside the claim, so without it every trip from home would be free in practice.
 *
 * @param baseCost               energy for any trip
 * @param costPer100             extra energy per started 100 blocks
 * @param maxCost                the most a trip costs (also the cost between dimensions)
 * @param warmupTicks            standing still this long before leaving
 * @param substationRadius       how near a reclaimed Substation Core counts as being at it
 * @param threatRadius           a zombie targeting the player within this range blocks the trip
 * @param arrivalProtectionTicks respawn protection given on arrival
 * @param bunkerMultiplier       cost multiplier for trips that leave from a Bunker Core
 */
public record TravelRules(int baseCost, int costPer100, int maxCost, int warmupTicks, int substationRadius,
						  int threatRadius, int arrivalProtectionTicks, double bunkerMultiplier) {
	public static final TravelRules DEFAULT = new TravelRules(10, 5, 40, 60, 16, 24, 100, 2.0);

	public static final Codec<TravelRules> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 10_000).optionalFieldOf("base_cost", DEFAULT.baseCost).forGetter(TravelRules::baseCost),
			Codec.intRange(0, 10_000).optionalFieldOf("cost_per_100_blocks", DEFAULT.costPer100).forGetter(TravelRules::costPer100),
			Codec.intRange(0, 10_000).optionalFieldOf("max_cost", DEFAULT.maxCost).forGetter(TravelRules::maxCost),
			Codec.intRange(0, 1200).optionalFieldOf("warmup_ticks", DEFAULT.warmupTicks).forGetter(TravelRules::warmupTicks),
			Codec.intRange(1, 128).optionalFieldOf("substation_radius", DEFAULT.substationRadius).forGetter(TravelRules::substationRadius),
			Codec.intRange(0, 128).optionalFieldOf("threat_radius", DEFAULT.threatRadius).forGetter(TravelRules::threatRadius),
			Codec.intRange(0, 1200).optionalFieldOf("arrival_protection_ticks", DEFAULT.arrivalProtectionTicks)
					.forGetter(TravelRules::arrivalProtectionTicks),
			Codec.doubleRange(0, 10).optionalFieldOf("bunker_departure_multiplier", DEFAULT.bunkerMultiplier)
					.forGetter(TravelRules::bunkerMultiplier)
	).apply(i, TravelRules::new));

	/** Energy for a trip of {@code distance} blocks; a negative distance means another dimension (max cost). */
	public int cost(double distance) {
		if (distance < 0) {
			return maxCost;
		}
		long steps = (long) Math.ceil(distance / 100.0);
		return (int) Math.min(maxCost, baseCost + steps * costPer100);
	}

	/** Energy for a trip of {@code distance} blocks, leaving from a Bunker Core when {@code fromBunker}. */
	public int cost(double distance, boolean fromBunker) {
		int cost = cost(distance);
		return fromBunker ? (int) Math.ceil(cost * bunkerMultiplier) : cost;
	}

	/**
	 * How much to take from each pack to pay {@code cost}: emptiest packs first, so the player keeps their fullest
	 * pack for the blaster. Returns null when all packs together hold less than {@code cost}.
	 */
	public static long[] drawPlan(long[] stored, long cost) {
		long total = 0;
		for (long s : stored) {
			total += Math.max(0, s);
		}
		if (total < cost) {
			return null;
		}
		Integer[] order = new Integer[stored.length];
		for (int k = 0; k < order.length; k++) {
			order[k] = k;
		}
		java.util.Arrays.sort(order, (a, b) -> Long.compare(stored[a], stored[b]));
		long[] draw = new long[stored.length];
		long left = cost;
		for (int k : order) {
			if (left <= 0) {
				break;
			}
			long take = Math.min(left, Math.max(0, stored[k]));
			draw[k] = take;
			left -= take;
		}
		return draw;
	}

	private static volatile TravelRules current = DEFAULT;

	public static TravelRules get() {
		return current;
	}

	/** Reload listener for {@code data/<ns>/ashfall/travel/travel.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<TravelRules> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "travel");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "travel");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/travel"));
		}

		@Override
		protected void apply(Map<Identifier, TravelRules> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Travel rules loaded (base {}, max {})", current.baseCost, current.maxCost);
		}
	}
}
