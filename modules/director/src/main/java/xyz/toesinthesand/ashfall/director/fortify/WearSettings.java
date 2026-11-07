package xyz.toesinthesand.ashfall.director.fortify;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Wear tuning from {@code data/ashfall/ashfall/fortify/wear.json}: how many wear points each fortified block holds,
 * what it becomes when they run out and what repairs it. Block and item names are ids or {@code #tags}; an item id
 * that is not registered (arms' Nails without arms) is dropped from the repair list.
 *
 * @param hitIntervalTicks how often one zombie adds a wear point while it keeps hitting (20 = one point a second)
 * @param reach            how close (blocks, to the block's centre) a zombie must be for its hit to count
 * @param repairPoints     wear one repair takes off (one set of repair materials, from a player or a Mechanic)
 * @param repairCooldownTicks after a repair the material in the player's hand cools down this long (10.2.2: one repair
 *                         a second, so holding use cannot out-repair several waves at once)
 * @param warnBelow        condition (1 - wear / points) under which a Bunker Core warns its owner after a horde night
 * @param immune           fortified blocks that never wear (the Bunker Core itself, reinforced deepslate)
 * @param fallback         the rule for a fortified block no rule matches (another mod's block added to the tag)
 * @param rules            first match wins
 */
public record WearSettings(int hitIntervalTicks, double reach, int repairPoints, int repairCooldownTicks, double warnBelow,
						   List<String> immune, Rule fallback, List<Rule> rules) {
	/** What a fully worn block becomes: {@code block} in its place, else broken; {@code drop} spawned either way. */
	public record Downgrade(Optional<Identifier> block, Optional<Identifier> drop, int count) {
		public static final Downgrade NONE = new Downgrade(Optional.empty(), Optional.empty(), 0);
		public static final Codec<Downgrade> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.optionalFieldOf("block").forGetter(Downgrade::block),
				Identifier.CODEC.optionalFieldOf("drop").forGetter(Downgrade::drop),
				Codec.intRange(0, 64).optionalFieldOf("count", 1).forGetter(Downgrade::count)
		).apply(i, Downgrade::new));
	}

	/** One wear rule; see the class comment. */
	public record Rule(List<String> match, int points, Downgrade downgrade, List<String> repair) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("match", List.of()).forGetter(Rule::match),
				Codec.intRange(1, 100_000).fieldOf("points").forGetter(Rule::points),
				Downgrade.CODEC.optionalFieldOf("downgrade", Downgrade.NONE).forGetter(Rule::downgrade),
				Codec.STRING.listOf().optionalFieldOf("repair", List.of()).forGetter(Rule::repair)
		).apply(i, Rule::new));

		boolean matches(BlockState state) {
			for (String name : match) {
				if (WearSettings.matches(state, name)) {
					return true;
				}
			}
			return false;
		}

		/** One test per repair entry; a repair takes one of each (a name listed twice takes two). */
		public List<Predicate<ItemStack>> materials() {
			List<Predicate<ItemStack>> out = new ArrayList<>();
			for (String name : repair) {
				Predicate<ItemStack> test = item(name);
				if (test != null) {
					out.add(test);
				}
			}
			return out;
		}

		/**
		 * The repair materials by name, each with how many one set takes (a name listed twice takes two), in list
		 * order; unregistered names are dropped as in {@link #materials()}.
		 */
		public Map<Predicate<ItemStack>, Integer> materialCounts() {
			Map<String, Integer> byName = new LinkedHashMap<>();
			for (String name : repair) {
				byName.merge(name, 1, Integer::sum);
			}
			Map<Predicate<ItemStack>, Integer> out = new LinkedHashMap<>();
			byName.forEach((name, count) -> {
				Predicate<ItemStack> test = item(name);
				if (test != null) {
					out.put(test, count);
				}
			});
			return out;
		}
	}

	public static final WearSettings DEFAULT = new WearSettings(20, 3.5, 45, 20, 0.5,
			List.of("ashfall:bunker_core", "minecraft:reinforced_deepslate"),
			new Rule(List.of(), 120, new Downgrade(Optional.of(Identifier.withDefaultNamespace("cobblestone")), Optional.empty(), 1),
					List.of("ashfall:scrap_metal")),
			List.of());

	public static final Codec<WearSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 1200).optionalFieldOf("hit_interval_ticks", DEFAULT.hitIntervalTicks).forGetter(WearSettings::hitIntervalTicks),
			Codec.doubleRange(1, 8).optionalFieldOf("reach", DEFAULT.reach).forGetter(WearSettings::reach),
			Codec.intRange(1, 100_000).optionalFieldOf("repair_points", DEFAULT.repairPoints).forGetter(WearSettings::repairPoints),
			Codec.intRange(0, 1200).optionalFieldOf("repair_cooldown_ticks", DEFAULT.repairCooldownTicks).forGetter(WearSettings::repairCooldownTicks),
			Codec.doubleRange(0, 1).optionalFieldOf("warn_below", DEFAULT.warnBelow).forGetter(WearSettings::warnBelow),
			Codec.STRING.listOf().optionalFieldOf("immune", DEFAULT.immune).forGetter(WearSettings::immune),
			Rule.CODEC.optionalFieldOf("default", DEFAULT.fallback).forGetter(WearSettings::fallback),
			Rule.CODEC.listOf().optionalFieldOf("blocks", DEFAULT.rules).forGetter(WearSettings::rules)
	).apply(i, WearSettings::new));

	private static volatile WearSettings current = DEFAULT;

	public static WearSettings get() {
		return current;
	}

	/** Test hook: replaces the settings until the next datapack reload (pass null for the defaults). */
	public static void setForTesting(@Nullable WearSettings settings) {
		current = settings == null ? DEFAULT : settings;
	}

	/** The rule for a fortified {@code state}, or null when it never wears (not fortified, or immune). */
	public @Nullable Rule ruleFor(BlockState state) {
		if (!state.is(Fortified.FORTIFIED)) {
			return null;
		}
		for (String name : immune) {
			if (matches(state, name)) {
				return null;
			}
		}
		for (Rule rule : rules) {
			if (rule.matches(state)) {
				return rule;
			}
		}
		return fallback;
	}

	static boolean matches(BlockState state, String name) {
		if (name.startsWith("#")) {
			Identifier id = Identifier.tryParse(name.substring(1));
			return id != null && state.is(TagKey.create(Registries.BLOCK, id));
		}
		Identifier id = Identifier.tryParse(name);
		return id != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
	}

	private static @Nullable Predicate<ItemStack> item(String name) {
		if (name.startsWith("#")) {
			Identifier id = Identifier.tryParse(name.substring(1));
			if (id == null) {
				return null;
			}
			TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
			return s -> s.is(tag);
		}
		Identifier id = Identifier.tryParse(name);
		Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
		return item.<Predicate<ItemStack>>map(it -> s -> s.is(it)).orElse(null);
	}

	/** Reload listener for {@code data/<ns>/ashfall/fortify/wear.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<WearSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "fortify_wear");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "wear");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/fortify"));
		}

		@Override
		protected void apply(Map<Identifier, WearSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallDirector.LOGGER.info("Fortified wear: {} rules", current.rules().size());
		}
	}
}
