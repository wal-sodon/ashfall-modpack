package xyz.toesinthesand.ashfall.arms.grid;

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
 * Relay wear on reclaimed districts ({@link GridDecay}), from {@code data/ashfall/ashfall/grid/decay.json}. The
 * shipped numbers mean one maintenance run every few days with three districts; halve {@code wear_per_day} for a
 * slower city, double it for daily upkeep.
 *
 * @param enabled         false turns decay off entirely (districts stay lit forever, as before rc13)
 * @param wearPerDay      condition every linked relay loses per world day
 * @param graceDays       no wear for this many days after a district is first reclaimed
 * @param flickerDays     days a district flickers with too few working relays before it blacks out
 * @param hordeRelaysMin  relays a horde night damages in the district it hits, at least ...
 * @param hordeRelaysMax  ... and at most
 * @param hordeDamage     condition a horde night takes off each relay it damages
 * @param warnBelow       the Field Radio lists relays at or below this condition
 */
public record DecaySettings(boolean enabled, int wearPerDay, int graceDays, int flickerDays, int hordeRelaysMin,
		int hordeRelaysMax, int hordeDamage, int warnBelow) {
	public static final DecaySettings DEFAULT = new DecaySettings(true, 8, 3, 2, 1, 2, 30, 30);

	public static final Codec<DecaySettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(DecaySettings::enabled),
			Codec.intRange(0, 100).optionalFieldOf("wear_per_day", DEFAULT.wearPerDay).forGetter(DecaySettings::wearPerDay),
			Codec.intRange(0, 365).optionalFieldOf("grace_days", DEFAULT.graceDays).forGetter(DecaySettings::graceDays),
			Codec.intRange(0, 365).optionalFieldOf("flicker_days", DEFAULT.flickerDays).forGetter(DecaySettings::flickerDays),
			Codec.intRange(0, 64).optionalFieldOf("horde_relays_min", DEFAULT.hordeRelaysMin).forGetter(DecaySettings::hordeRelaysMin),
			Codec.intRange(0, 64).optionalFieldOf("horde_relays_max", DEFAULT.hordeRelaysMax).forGetter(DecaySettings::hordeRelaysMax),
			Codec.intRange(0, 100).optionalFieldOf("horde_damage", DEFAULT.hordeDamage).forGetter(DecaySettings::hordeDamage),
			Codec.intRange(0, 100).optionalFieldOf("warn_below", DEFAULT.warnBelow).forGetter(DecaySettings::warnBelow)
	).apply(i, DecaySettings::new));

	private static volatile DecaySettings current = DEFAULT;

	public static DecaySettings get() {
		return current;
	}

	/** For gametests: replace the loaded settings until the next reload. */
	public static void setForTesting(DecaySettings settings) {
		current = settings == null ? DEFAULT : settings;
	}

	/** Reload listener for {@code data/<ns>/ashfall/grid/decay.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<DecaySettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "grid_decay");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "decay");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/grid"));
		}

		@Override
		protected void apply(Map<Identifier, DecaySettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Grid decay loaded ({}, {} per day)", current.enabled ? "on" : "off", current.wearPerDay);
		}
	}
}
