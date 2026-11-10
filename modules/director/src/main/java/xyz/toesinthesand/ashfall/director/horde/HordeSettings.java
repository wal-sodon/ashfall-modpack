package xyz.toesinthesand.ashfall.director.horde;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.Map;

/**
 * Tuning for dynamic hordes and blood trails, from {@code data/ashfall/ashfall/horde/hordes.json}. Every field is
 * optional (this file shares its folder with {@code schedule.json}, and each loader reads both).
 *
 * @param updateTicks    ticks between horde updates (grouping, merging, drift)
 * @param groupRadius    zombies this close to each other may form a horde; a lone zombie this close to a horde's
 *                       leader joins it
 * @param minGroup       zombies needed to form a new horde
 * @param mergeRadius    two hordes whose leaders are this close merge into one
 * @param maxSize        no horde grows beyond this (a merge that would is skipped)
 * @param stimulusScale  a horde perceives SOUND, LIGHT and BLOOD out to the stimulus radius times this; a single
 *                       zombie's reach is capped at 1.3, so a horde draws in from further than any one zombie would
 * @param leash          a follower further than this from its leader walks back to it
 * @param arriveRadius   a leader this close to the horde's destination has arrived
 * @param blood          blood trails
 * @param breakIn        horde-night waves breaking in toward a Bunker Core they can't reach
 */
public record HordeSettings(int updateTicks, double groupRadius, int minGroup, double mergeRadius, int maxSize,
		double stimulusScale, double leash, double arriveRadius, Blood blood, BreakIn breakIn) {

	/**
	 * Break-in: a horde-night zombie heading for a Bunker Core, with no target, that has stood stuck for
	 * {@code stuckTicks} within {@code radius} blocks (horizontally) of the core breaks the block in its way toward it.
	 */
	public record BreakIn(boolean enabled, double radius, int stuckTicks) {
		public static final BreakIn DEFAULT = new BreakIn(true, 48.0, 60);
		public static final Codec<BreakIn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(BreakIn::enabled),
				Codec.doubleRange(4.0, 128.0).optionalFieldOf("radius", DEFAULT.radius).forGetter(BreakIn::radius),
				Codec.intRange(20, 1200).optionalFieldOf("stuck_ticks", DEFAULT.stuckTicks).forGetter(BreakIn::stuckTicks)
		).apply(i, BreakIn::new));
	}

	/**
	 * Blood trails: a player at or below {@code healthFraction} of their health leaves a BLOOD stimulus of
	 * {@code radius} every {@code intervalTicks} once they have moved {@code minStep} blocks from the last drop.
	 */
	public record Blood(boolean enabled, float healthFraction, double radius, int intervalTicks, double minStep) {
		public static final Blood DEFAULT = new Blood(true, 0.5f, 10.0, 40, 3.0);
		public static final Codec<Blood> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(Blood::enabled),
				Codec.floatRange(0f, 1f).optionalFieldOf("health_fraction", DEFAULT.healthFraction).forGetter(Blood::healthFraction),
				Codec.doubleRange(1.0, 24.0).optionalFieldOf("radius", DEFAULT.radius).forGetter(Blood::radius),
				Codec.intRange(10, 1200).optionalFieldOf("interval_ticks", DEFAULT.intervalTicks).forGetter(Blood::intervalTicks),
				Codec.doubleRange(0.0, 32.0).optionalFieldOf("min_step", DEFAULT.minStep).forGetter(Blood::minStep)
		).apply(i, Blood::new));
	}

	public static final HordeSettings DEFAULT = new HordeSettings(40, 8.0, 3, 16.0, 40, 2.0, 10.0, 4.0, Blood.DEFAULT,
			BreakIn.DEFAULT);

	public static final Codec<HordeSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(10, 1200).optionalFieldOf("update_ticks", DEFAULT.updateTicks).forGetter(HordeSettings::updateTicks),
			Codec.doubleRange(1.0, 64.0).optionalFieldOf("group_radius", DEFAULT.groupRadius).forGetter(HordeSettings::groupRadius),
			Codec.intRange(2, 100).optionalFieldOf("min_group", DEFAULT.minGroup).forGetter(HordeSettings::minGroup),
			Codec.doubleRange(1.0, 128.0).optionalFieldOf("merge_radius", DEFAULT.mergeRadius).forGetter(HordeSettings::mergeRadius),
			Codec.intRange(2, 150).optionalFieldOf("max_size", DEFAULT.maxSize).forGetter(HordeSettings::maxSize),
			Codec.doubleRange(1.0, 6.0).optionalFieldOf("stimulus_scale", DEFAULT.stimulusScale).forGetter(HordeSettings::stimulusScale),
			Codec.doubleRange(2.0, 64.0).optionalFieldOf("leash", DEFAULT.leash).forGetter(HordeSettings::leash),
			Codec.doubleRange(1.0, 32.0).optionalFieldOf("arrive_radius", DEFAULT.arriveRadius).forGetter(HordeSettings::arriveRadius),
			Blood.CODEC.optionalFieldOf("blood", Blood.DEFAULT).forGetter(HordeSettings::blood),
			BreakIn.CODEC.optionalFieldOf("break_in", BreakIn.DEFAULT).forGetter(HordeSettings::breakIn)
	).apply(i, HordeSettings::new));

	private static volatile HordeSettings current = DEFAULT;
	private static volatile HordeSettings testOverride;

	public static HordeSettings get() {
		HordeSettings test = testOverride;
		return test != null ? test : current;
	}

	/** Test hook: settings that win over the datapack; null goes back to it. */
	public static void setForTesting(HordeSettings settings) {
		testOverride = settings;
	}

	/** Reload listener for {@code data/<ns>/ashfall/horde/hordes.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<HordeSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "horde_settings");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "hordes");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/horde"));
		}

		@Override
		protected void apply(Map<Identifier, HordeSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallDirector.LOGGER.info("Horde settings loaded (group {} blocks, merge {} blocks, max {})",
					current.groupRadius, current.mergeRadius, current.maxSize);
		}
	}
}
