package xyz.toesinthesand.ashfall.arms.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.List;
import java.util.Map;

/**
 * Tuning for the briefing and the objective tracker, from {@code data/ashfall/ashfall/mission/settings.json}.
 * <p>
 * The counts the objective shows (crystals for the frame, banks for the Uplink, Heart pylons) mirror the recipes and
 * the finale data; they live here so a pack that changes a recipe changes the checklist with one edit, instead of the
 * tracker reading recipes back out of the recipe manager.
 *
 * @param briefingDelayTicks how long after joining the briefing waits, so the title is not lost behind the
 *                           loading screen
 * @param syncIntervalTicks  how often each player's objective is recomputed and, if it changed, sent
 * @param crystalsNeeded     Resonance Crystals in the Uplink Frame recipe
 * @param banksNeeded        Capacitor Banks in the Grid Uplink recipe
 * @param pylons             Heart pylons in the finale
 * @param storyBosses        the boss lairs whose crystal the Uplink needs, in briefing order
 * @param titleFadeIn        title fade-in ticks
 * @param titleStay          title stay ticks
 * @param titleFadeOut       title fade-out ticks
 */
public record MissionSettings(int briefingDelayTicks, int syncIntervalTicks, int crystalsNeeded, int banksNeeded, int pylons,
		List<Identifier> storyBosses, int titleFadeIn, int titleStay, int titleFadeOut) {
	public static final MissionSettings DEFAULT = new MissionSettings(80, 20, 3, 2, 4, List.of(
			Identifier.fromNamespaceAndPath("ashfall", "abomination"), Identifier.fromNamespaceAndPath("ashfall", "patient_zero"),
			Identifier.fromNamespaceAndPath("ashfall", "juggernaut")), 20, 90, 30);

	public static final Codec<MissionSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 1200).optionalFieldOf("briefing_delay_ticks", DEFAULT.briefingDelayTicks).forGetter(MissionSettings::briefingDelayTicks),
			Codec.intRange(1, 1200).optionalFieldOf("sync_interval_ticks", DEFAULT.syncIntervalTicks).forGetter(MissionSettings::syncIntervalTicks),
			Codec.intRange(0, 64).optionalFieldOf("crystals_needed", DEFAULT.crystalsNeeded).forGetter(MissionSettings::crystalsNeeded),
			Codec.intRange(0, 64).optionalFieldOf("banks_needed", DEFAULT.banksNeeded).forGetter(MissionSettings::banksNeeded),
			Codec.intRange(0, 64).optionalFieldOf("pylons", DEFAULT.pylons).forGetter(MissionSettings::pylons),
			Identifier.CODEC.listOf().optionalFieldOf("story_bosses", DEFAULT.storyBosses).forGetter(MissionSettings::storyBosses),
			Codec.intRange(0, 200).optionalFieldOf("title_fade_in_ticks", DEFAULT.titleFadeIn).forGetter(MissionSettings::titleFadeIn),
			Codec.intRange(0, 400).optionalFieldOf("title_stay_ticks", DEFAULT.titleStay).forGetter(MissionSettings::titleStay),
			Codec.intRange(0, 200).optionalFieldOf("title_fade_out_ticks", DEFAULT.titleFadeOut).forGetter(MissionSettings::titleFadeOut)
	).apply(i, MissionSettings::new));

	public MissionSettings {
		storyBosses = List.copyOf(storyBosses);
	}

	private static volatile MissionSettings current = DEFAULT;

	public static MissionSettings get() {
		return current;
	}

	/** Reload listener for {@code data/<ns>/ashfall/mission/settings.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<MissionSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "mission_settings");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "settings");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/mission"));
		}

		@Override
		protected void apply(Map<Identifier, MissionSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Mission settings loaded ({} story bosses)", current.storyBosses.size());
		}
	}
}
