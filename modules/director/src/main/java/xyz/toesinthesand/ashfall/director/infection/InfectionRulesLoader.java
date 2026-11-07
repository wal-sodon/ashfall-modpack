package xyz.toesinthesand.ashfall.director.infection;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.Map;

/**
 * Loads {@code data/ashfall/ashfall/infection/stages.json}. There is one timeline per world, so only that
 * id is read; a datapack replaces it by shipping the same path. A missing or invalid file falls back to
 * {@link InfectionRules#DEFAULT} (with the codec error in the log) rather than turning infection off.
 */
public final class InfectionRulesLoader extends SimpleJsonResourceReloadListener<InfectionRules> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "infection_rules");
	public static final Identifier STAGES = Identifier.fromNamespaceAndPath("ashfall", "stages");

	private static volatile InfectionRules rules = InfectionRules.DEFAULT;

	public InfectionRulesLoader() {
		super(InfectionRules.CODEC, FileToIdConverter.json("ashfall/infection"));
	}

	@Override
	protected void apply(Map<Identifier, InfectionRules> loaded, ResourceManager manager, ProfilerFiller profiler) {
		InfectionRules found = loaded.get(STAGES);
		if (found == null) {
			AshfallDirector.LOGGER.warn("No valid ashfall:stages infection file; using the built-in timeline");
			found = InfectionRules.DEFAULT;
		}
		rules = found;
		AshfallDirector.LOGGER.info("Infection: stage 4 at {} s, fatal at {} s", found.stage(4).startsAtTicks() / 20, found.deathAt() / 20);
	}

	public static InfectionRules rules() {
		return rules;
	}

	/** Test hook: replaces the loaded rules (a reload puts the datapack's back). */
	public static void setForTesting(InfectionRules replacement) {
		rules = replacement;
	}
}
