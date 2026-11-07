package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.EnumMap;
import java.util.Map;

/**
 * Loads the ending profiles, {@code data/<ns>/ashfall/ending/<ending id>.json} ({@code sever.json}, {@code bind.json};
 * the file name is the ending). A missing file makes that ending {@link EndingProfile#NEUTRAL}: the choice is still
 * recorded, it just changes nothing, which is safer than guessing numbers in code.
 */
public final class EndingProfiles extends SimpleJsonResourceReloadListener<EndingProfile> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "ending_profiles");

	private static volatile Map<StoryEnding, EndingProfile> profiles = Map.of();
	private static final Map<StoryEnding, EndingProfile> TEST = new EnumMap<>(StoryEnding.class);

	public EndingProfiles() {
		super(EndingProfile.CODEC, FileToIdConverter.json("ashfall/ending"));
	}

	@Override
	protected void apply(Map<Identifier, EndingProfile> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<StoryEnding, EndingProfile> next = new EnumMap<>(StoryEnding.class);
		// The ashfall namespace wins; another namespace's file only fills an ending ashfall does not define.
		loaded.entrySet().stream()
				.sorted((a, b) -> Boolean.compare(b.getKey().getNamespace().equals("ashfall"), a.getKey().getNamespace().equals("ashfall")))
				.forEach(e -> {
					StoryEnding ending = StoryEnding.byId(e.getKey().getPath());
					if (ending.chosen()) {
						next.putIfAbsent(ending, e.getValue());
					} else {
						AshfallArms.LOGGER.warn("Ending profile {} names no ending (expected sever or bind)", e.getKey());
					}
				});
		profiles = Map.copyOf(next);
		AshfallArms.LOGGER.info("Loaded {} ending profiles", next.size());
	}

	/** The profile of {@code ending}; NEUTRAL for NONE or a missing file. */
	public static EndingProfile get(StoryEnding ending) {
		synchronized (TEST) {
			EndingProfile test = TEST.get(ending);
			if (test != null) {
				return test;
			}
		}
		return ending.chosen() ? profiles.getOrDefault(ending, EndingProfile.NEUTRAL) : EndingProfile.NEUTRAL;
	}

	/** Whether a datapack file was loaded for {@code ending} (tests). */
	public static boolean loaded(StoryEnding ending) {
		return profiles.containsKey(ending);
	}

	/** Test hook: a profile that wins over the datapack; null goes back to it. */
	public static void setForTesting(StoryEnding ending, EndingProfile profile) {
		synchronized (TEST) {
			if (profile == null) {
				TEST.remove(ending);
			} else {
				TEST.put(ending, profile);
			}
		}
	}
}
