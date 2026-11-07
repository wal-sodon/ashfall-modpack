package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import xyz.toesinthesand.ashfall.arms.grid.DistrictState;

import java.util.Locale;
import java.util.Optional;

/**
 * Criterion triggers for the "Relight" advancement tab that vanilla triggers cannot express: grid
 * progress, Relay Log counts and story boss kills. Each is a plain {@link SimpleCriterionTrigger}, so the
 * advancement JSON stays ordinary and Better Advancements / Advancement Plaques show them like any other.
 */
public final class RelightTriggers {
	/** {@code ashfall:relay_logs}: fired whenever a player's collection grows. */
	public static final RelayLogTrigger RELAY_LOGS = register("relay_logs", new RelayLogTrigger());
	/** {@code ashfall:district}: fired on every district change, for every online player, and again on join. */
	public static final DistrictTrigger DISTRICT = register("district", new DistrictTrigger());
	/** {@code ashfall:grid_percent}: fired with the district trigger. */
	public static final GridPercentTrigger GRID_PERCENT = register("grid_percent", new GridPercentTrigger());
	/** {@code ashfall:boss_defeated}: fired for the killer and everyone near a story boss when it dies. */
	public static final BossTrigger BOSS_DEFEATED = register("boss_defeated", new BossTrigger());
	/** {@code ashfall:story_ending}: fired for every participant when the party's ending is recorded. */
	public static final EndingTrigger ENDING = register("story_ending", new EndingTrigger());

	/** District states by their lower-case JSON name ({@code "reclaimed"}). */
	public static final Codec<DistrictState> STATE_CODEC = Codec.STRING.comapFlatMap(name -> {
		try {
			return DataResult.success(DistrictState.valueOf(name.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Unknown district state: " + name);
		}
	}, state -> state.name().toLowerCase(Locale.ROOT));

	private RelightTriggers() {
	}

	private static <T extends SimpleCriterionTrigger<?>> T register(String name, T trigger) {
		return Registry.register(BuiltInRegistries.TRIGGER_TYPES, Identifier.fromNamespaceAndPath("ashfall", name), trigger);
	}

	/** Forces class loading, and so registration, during mod init (before advancements load). */
	public static void init() {
	}

	// --------------------------------------------------------------------------------------------- relay logs

	public static final class RelayLogTrigger extends SimpleCriterionTrigger<RelayLogTrigger.Instance> {
		@Override
		public Codec<Instance> codec() {
			return Instance.CODEC;
		}

		public void trigger(ServerPlayer player, int collected, int total) {
			trigger(player, i -> i.matches(collected, total));
		}

		/**
		 * @param count collected logs must fall in this range
		 * @param all   when true, the collection must also be complete (every log in the current pool), so a
		 *              datapack that adds logs moves the "all logs" goalpost with it
		 */
		public record Instance(Optional<Holder<LootItemCondition>> player, MinMaxBounds.Ints count, boolean all)
				implements SimpleCriterionTrigger.SimpleInstance {
			public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
					LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
					MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(Instance::count),
					Codec.BOOL.optionalFieldOf("all", false).forGetter(Instance::all)
			).apply(i, Instance::new));

			public boolean matches(int collected, int total) {
				return count.matches(collected) && (!all || (total > 0 && collected >= total));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ districts

	public static final class DistrictTrigger extends SimpleCriterionTrigger<DistrictTrigger.Instance> {
		@Override
		public Codec<Instance> codec() {
			return Instance.CODEC;
		}

		public void trigger(ServerPlayer player, String district, DistrictState state) {
			trigger(player, i -> i.matches(district, state));
		}

		/**
		 * @param district the district id ({@code river_arts}); absent matches any district
		 * @param state    the district must have reached at least this state ({@link DistrictProgress#reached})
		 */
		public record Instance(Optional<Holder<LootItemCondition>> player, Optional<String> district, DistrictState state)
				implements SimpleCriterionTrigger.SimpleInstance {
			public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
					LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
					Codec.STRING.optionalFieldOf("district").forGetter(Instance::district),
					STATE_CODEC.fieldOf("state").forGetter(Instance::state)
			).apply(i, Instance::new));

			public boolean matches(String id, DistrictState current) {
				return district.map(id::equals).orElse(true) && DistrictProgress.reached(current, state);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- grid %

	public static final class GridPercentTrigger extends SimpleCriterionTrigger<GridPercentTrigger.Instance> {
		@Override
		public Codec<Instance> codec() {
			return Instance.CODEC;
		}

		public void trigger(ServerPlayer player, int percent) {
			trigger(player, i -> i.percent().matches(percent));
		}

		public record Instance(Optional<Holder<LootItemCondition>> player, MinMaxBounds.Ints percent)
				implements SimpleCriterionTrigger.SimpleInstance {
			public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
					LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
					MinMaxBounds.Ints.CODEC.optionalFieldOf("percent", MinMaxBounds.Ints.ANY).forGetter(Instance::percent)
			).apply(i, Instance::new));
		}
	}

	// --------------------------------------------------------------------------------------------------- bosses

	public static final class BossTrigger extends SimpleCriterionTrigger<BossTrigger.Instance> {
		@Override
		public Codec<Instance> codec() {
			return Instance.CODEC;
		}

		public void trigger(ServerPlayer player, Identifier lair) {
			trigger(player, i -> i.boss().map(lair::equals).orElse(true));
		}

		/** @param boss the boss lair id ({@code ashfall:patient_zero}); absent matches any story boss */
		public record Instance(Optional<Holder<LootItemCondition>> player, Optional<Identifier> boss)
				implements SimpleCriterionTrigger.SimpleInstance {
			public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
					LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
					Identifier.CODEC.optionalFieldOf("boss").forGetter(Instance::boss)
			).apply(i, Instance::new));
		}
	}

	// --------------------------------------------------------------------------------------------------- ending

	public static final class EndingTrigger extends SimpleCriterionTrigger<EndingTrigger.Instance> {
		@Override
		public Codec<Instance> codec() {
			return Instance.CODEC;
		}

		public void trigger(ServerPlayer player, StoryEnding ending) {
			trigger(player, i -> i.ending().map(e -> e.equals(ending.id())).orElse(true));
		}

		/** @param ending {@code "sever"} or {@code "bind"}; absent matches either ending */
		public record Instance(Optional<Holder<LootItemCondition>> player, Optional<String> ending)
				implements SimpleCriterionTrigger.SimpleInstance {
			public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
					LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
					Codec.STRING.optionalFieldOf("ending").forGetter(Instance::ending)
			).apply(i, Instance::new));
		}
	}
}
