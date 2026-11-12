package xyz.toesinthesand.ashfall.director.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;

import java.util.List;
import java.util.Map;

/**
 * Balance for Survivor Skills, from {@code data/ashfall/ashfall/skills/skills.json}.
 *
 * @param coreCosts         XP points for rank 1, 2, 3... of a class's core skill (the length is the core max rank)
 * @param generalCosts      the same for every other skill (shorter: the general max rank)
 * @param classes           class id to its core skill ids
 * @param skills            skill id to what one rank is worth
 * @param classChangeDays   in-game days between class changes
 * @param classChangeRefund share of the spent XP given back when the class changes
 * @param specialXp         XP orbs dropped by each special infected kind (others keep the vanilla zombie's 5)
 */
public record SkillRules(List<Integer> coreCosts, List<Integer> generalCosts, Map<String, List<String>> classes,
						 Map<String, Double> skills, int classChangeDays, double classChangeRefund, Map<String, Integer> specialXp) {
	public static final Codec<SkillRules> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.listOf(1, 10).fieldOf("core_costs").forGetter(SkillRules::coreCosts),
			Codec.INT.listOf(0, 10).fieldOf("general_costs").forGetter(SkillRules::generalCosts),
			Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).fieldOf("classes").forGetter(SkillRules::classes),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("per_rank").forGetter(SkillRules::skills),
			Codec.INT.optionalFieldOf("class_change_days", 7).forGetter(SkillRules::classChangeDays),
			Codec.DOUBLE.optionalFieldOf("class_change_refund", 0.5).forGetter(SkillRules::classChangeRefund),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("special_infected_xp", Map.of()).forGetter(SkillRules::specialXp)
	).apply(i, SkillRules::new));

	/** The shipped values, used when the file is missing or invalid. */
	public static final SkillRules DEFAULT = new SkillRules(List.of(40, 100, 180, 280, 400), List.of(60, 180),
			Map.of("medic", List.of("field_medic", "immune_system", "iron_gut"),
					"brawler", List.of("brawler", "fleet_foot", "hardy"),
					"sniper", List.of("steady_aim", "headhunter", "light_step"),
					"tank", List.of("hardy", "bulwark", "brawler"),
					"scout", List.of("fleet_foot", "light_step", "pack_rat"),
					"engineer", List.of("tinker", "pack_rat", "steady_aim")),
			Map.ofEntries(Map.entry("hardy", 2.0), Map.entry("fleet_foot", 0.03), Map.entry("brawler", 0.06),
					Map.entry("steady_aim", 0.06), Map.entry("headhunter", 0.10), Map.entry("bulwark", 1.0),
					Map.entry("field_medic", 0.20), Map.entry("iron_gut", 0.12), Map.entry("immune_system", 0.10),
					Map.entry("light_step", 0.10), Map.entry("pack_rat", 0.10), Map.entry("tinker", 0.20)),
			7, 0.5,
			Map.of("crawler", 8, "spitter", 10, "bloated", 10, "stalker", 15, "screamer", 15, "brute", 25));

	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "skill_rules");
	public static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "skills");

	private static volatile SkillRules rules = DEFAULT;

	public static SkillRules get() {
		return rules;
	}

	/** Test hook: replaces the loaded rules (a reload puts the datapack's back). */
	public static void setForTesting(SkillRules replacement) {
		rules = replacement == null ? DEFAULT : replacement;
	}

	/** Whether {@code skill} is one of {@code chosen}'s core skills. */
	public boolean isCore(SurvivorClass chosen, Skill skill) {
		return chosen != null && classes.getOrDefault(chosen.id(), List.of()).contains(skill.id());
	}

	/** A class's core skills, in the file's order (unknown ids skipped). */
	public List<Skill> coreSkills(SurvivorClass chosen) {
		return classes.getOrDefault(chosen.id(), List.of()).stream().map(Skill::byId).filter(s -> s != null).toList();
	}

	private List<Integer> costsFor(SurvivorClass chosen, Skill skill) {
		if (chosen == null) {
			return List.of();
		}
		return isCore(chosen, skill) ? coreCosts : generalCosts;
	}

	/** How far {@code chosen} can train {@code skill} (0 without a class). */
	public int maxRank(SurvivorClass chosen, Skill skill) {
		return costsFor(chosen, skill).size();
	}

	/** Points for {@code chosen} to buy {@code rank} (1-based) of {@code skill}, or -1 past its max. */
	public int cost(SurvivorClass chosen, Skill skill, int rank) {
		List<Integer> list = costsFor(chosen, skill);
		return rank >= 1 && rank <= list.size() ? Math.max(0, list.get(rank - 1)) : -1;
	}

	public double perRank(Skill skill) {
		return skills.getOrDefault(skill.id(), 0.0);
	}

	/** Game ticks between class changes. */
	public long classChangeTicks() {
		return Math.max(0, classChangeDays) * 24000L;
	}

	/** XP a special infected of this kind drops, or -1 to keep the vanilla amount. */
	public int specialXp(SpecialKind kind) {
		return specialXp.getOrDefault(kind.id(), -1);
	}

	public static final class Loader extends SimpleJsonResourceReloadListener<SkillRules> {
		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/skills"));
		}

		@Override
		protected void apply(Map<Identifier, SkillRules> loaded, ResourceManager manager, ProfilerFiller profiler) {
			SkillRules found = loaded.get(FILE);
			if (found == null) {
				AshfallDirector.LOGGER.warn("No valid ashfall:skills skill file; using the built-in skill balance");
				found = DEFAULT;
			}
			rules = found;
		}
	}
}
