package xyz.toesinthesand.ashfall.salvage.profile;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Unit;

import java.util.Map;

/**
 * Loads {@code data/<ns>/ashfall/building_profile/*.json} and classifies buildings and units.
 * <p>
 * Tag rules win first and apply to the whole building (a police station stays a police station on
 * every floor). For Lost Cities buildings those rules read the building id, multibuilding id and zone
 * ({@code lc_*} tags from {@code LostCitiesBuildingSource}), so the same tower is an office downtown and
 * a block of flats in Montford. Otherwise each unit is classified by its use (the Arnis furnishing, or
 * "Floor"/"Cellar" for Lost Cities); anything unmatched falls back to {@link #GENERIC}.
 */
public final class BuildingProfiles extends SimpleJsonResourceReloadListener<BuildingProfile> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "building_profiles");
	public static final Identifier GENERIC = Identifier.fromNamespaceAndPath("ashfall", "generic");

	private static volatile Map<Identifier, BuildingProfile> profiles = Map.of();

	public BuildingProfiles() {
		super(BuildingProfile.CODEC, FileToIdConverter.json("ashfall/building_profile"));
	}

	@Override
	protected void apply(Map<Identifier, BuildingProfile> loaded, ResourceManager manager, ProfilerFiller profiler) {
		profiles = Map.copyOf(loaded);
		AshfallSalvage.LOGGER.info("Loaded {} building profiles", loaded.size());
	}

	public static Map<Identifier, BuildingProfile> all() {
		return profiles;
	}

	public static BuildingProfile get(Identifier id) {
		return profiles.get(id);
	}

	/** The profile id chosen by tag rules for the whole building, or null if no tag rule matches. */
	public static Identifier classifyByTags(Building building) {
		Identifier best = null;
		int bestPriority = Integer.MIN_VALUE;
		for (Map.Entry<Identifier, BuildingProfile> e : profiles.entrySet()) {
			BuildingProfile p = e.getValue();
			if (p.priority() > bestPriority && p.matchesTags(building.tags())) {
				best = e.getKey();
				bestPriority = p.priority();
			}
		}
		return best;
	}

	/** The profile id for one unit: the building's tag profile if any, else by Arnis use, else generic. */
	public static Identifier classify(Building building, Unit unit) {
		Identifier byTags = classifyByTags(building);
		if (byTags != null) {
			return byTags;
		}
		Identifier best = GENERIC;
		int bestScore = 0, bestPriority = Integer.MIN_VALUE;
		for (Map.Entry<Identifier, BuildingProfile> e : profiles.entrySet()) {
			int score = e.getValue().matchesArnisUse(unit.arnisUse());
			int priority = e.getValue().priority();
			if (score > bestScore || (score == bestScore && score > 0 && priority > bestPriority)) {
				best = e.getKey();
				bestScore = score;
				bestPriority = priority;
			}
		}
		return best;
	}

	/** Test hook: replaces the loaded profiles. */
	public static void setForTesting(Map<Identifier, BuildingProfile> replacement) {
		profiles = Map.copyOf(replacement);
	}
}
