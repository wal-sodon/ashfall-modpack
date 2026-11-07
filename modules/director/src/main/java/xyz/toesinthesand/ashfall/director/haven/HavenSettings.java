package xyz.toesinthesand.ashfall.director.haven;

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
 * Haven tuning from {@code data/ashfall/ashfall/haven/settings.json}, so balance changes are a datapack edit.
 * Every field is optional and falls back to the default below; a missing file means all defaults.
 *
 * @param bunkerRadius             chunks on each side of a Bunker Core's chunk that it claims (2 = 5x5 chunks)
 * @param respawnProtectionSeconds resistance and no zombie targeting after a respawn
 * @param respawnChoiceSeconds     how long the respawn-choice buttons stay valid
 * @param sirenWarningSeconds      how long before nightfall the horde countdown starts; the default 150 s
 *                                 starts it in the late afternoon (time of day 9000), enough to walk a few
 *                                 blocks of city to a haven
 * @param sirenRepeatSeconds       siren repeat interval during the countdown (0 = only once)
 * @param sirenSound               sound event id; the vanilla raid horn reads as a siren and needs no asset
 * @param sirenVolume              volume of the siren, played at each player's own position
 */
public record HavenSettings(int bunkerRadius, int respawnProtectionSeconds, int respawnChoiceSeconds,
							int sirenWarningSeconds, int sirenRepeatSeconds, Identifier sirenSound, float sirenVolume) {
	public static final HavenSettings DEFAULT = new HavenSettings(2, 10, 20, 150, 30,
			Identifier.withDefaultNamespace("event.raid.horn"), 0.8f);

	public static final Codec<HavenSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 8).optionalFieldOf("bunker_radius", DEFAULT.bunkerRadius).forGetter(HavenSettings::bunkerRadius),
			Codec.intRange(0, 120).optionalFieldOf("respawn_protection_seconds", DEFAULT.respawnProtectionSeconds)
					.forGetter(HavenSettings::respawnProtectionSeconds),
			Codec.intRange(0, 600).optionalFieldOf("respawn_choice_seconds", DEFAULT.respawnChoiceSeconds)
					.forGetter(HavenSettings::respawnChoiceSeconds),
			Codec.intRange(0, 600).optionalFieldOf("siren_warning_seconds", DEFAULT.sirenWarningSeconds)
					.forGetter(HavenSettings::sirenWarningSeconds),
			Codec.intRange(0, 600).optionalFieldOf("siren_repeat_seconds", DEFAULT.sirenRepeatSeconds)
					.forGetter(HavenSettings::sirenRepeatSeconds),
			Identifier.CODEC.optionalFieldOf("siren_sound", DEFAULT.sirenSound).forGetter(HavenSettings::sirenSound),
			Codec.floatRange(0, 4).optionalFieldOf("siren_volume", DEFAULT.sirenVolume).forGetter(HavenSettings::sirenVolume)
	).apply(i, HavenSettings::new));

	public int respawnProtectionTicks() {
		return respawnProtectionSeconds * 20;
	}

	public int respawnChoiceTicks() {
		return respawnChoiceSeconds * 20;
	}

	public long sirenWarningTicks() {
		return sirenWarningSeconds * 20L;
	}

	public long sirenRepeatTicks() {
		return sirenRepeatSeconds * 20L;
	}

	private static volatile HavenSettings current = DEFAULT;

	public static HavenSettings get() {
		return current;
	}

	/** Test hook: replaces the settings until the next datapack reload (pass null for the defaults). */
	public static void setForTesting(HavenSettings settings) {
		current = settings == null ? DEFAULT : settings;
	}

	/** Reload listener for {@code data/<ns>/ashfall/haven/settings.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<HavenSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "haven_settings");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "settings");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/haven"));
		}

		@Override
		protected void apply(Map<Identifier, HavenSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallDirector.LOGGER.info("Haven settings: {}", current);
		}
	}
}
