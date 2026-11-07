package xyz.toesinthesand.ashfall.director.infection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import xyz.toesinthesand.ashfall.director.story.StoryFlags;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The infection timeline, loaded from {@code data/<ns>/ashfall/infection/stages.json}: when each of the
 * four stages begins, what each stage does to the body, how likely a zombie hit is to infect, and what the
 * two treatments do. Every number lives in the datapack so balance passes never need a rebuild.
 * <p>
 * All the arithmetic is here, free of entities and levels, so the timeline can be unit tested: progress is
 * "ticks of infection survived", which only advances while no treatment pause is running. A stage is a
 * band of that progress, so knocking a stage back is just moving progress to the start of the previous
 * band, and the death timer is simply the end of the last band.
 *
 * @param stages          the four stages in order; stage 1 must start at 0
 * @param deathAfterTicks how long stage 4 lasts before the infection kills, unless treated
 * @param sources         infection chances per zombie hit
 * @param treatments      what Bandages and Antibiotics do
 */
public record InfectionRules(List<Stage> stages, int deathAfterTicks, Sources sources, Treatments treatments) {
	public static final int TICKS_PER_SECOND = 20;
	public static final int MAX_STAGE = 4;
	/** The story flag set by the Sever ending (M9.5); with it, Antibiotics cure at any stage. */
	public static final String SEVER_FLAG = "ashfall:sever";

	/**
	 * One stage's symptoms.
	 *
	 * @param stage                   1..4
	 * @param startsAtTicks           infection progress at which this stage begins
	 * @param hungerExhaustionPerTick extra hunger exhaustion every tick (vanilla spends 4 per food point)
	 * @param thirstExhaustionPerTick extra thirst exhaustion every tick (same scale as hunger)
	 * @param effects                 symptom effects, rolled once a second
	 */
	public record Stage(int stage, int startsAtTicks, float hungerExhaustionPerTick, float thirstExhaustionPerTick,
						List<Symptom> effects) {
		public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, MAX_STAGE).fieldOf("stage").forGetter(Stage::stage),
				Codec.intRange(0, 10_000_000).fieldOf("starts_at_seconds").forGetter(s -> s.startsAtTicks / TICKS_PER_SECOND),
				Codec.floatRange(0, 10).optionalFieldOf("hunger_exhaustion_per_tick", 0f).forGetter(Stage::hungerExhaustionPerTick),
				Codec.floatRange(0, 10).optionalFieldOf("thirst_exhaustion_per_tick", 0f).forGetter(Stage::thirstExhaustionPerTick),
				Symptom.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Stage::effects)
		).apply(i, (stage, seconds, hunger, thirst, effects) -> new Stage(stage, seconds * TICKS_PER_SECOND, hunger, thirst, effects)));
	}

	/**
	 * A symptom: once a second it is rolled with {@code chance} and, on a hit, applied for its duration.
	 * A steady symptom (slowness at stage 3) is chance 1 with a duration a little over a second, so it never
	 * flickers off; an occasional one (nausea) is a small chance with a longer duration.
	 *
	 * @param effect        mob effect id, resolved when applied so a typo only skips that symptom
	 * @param amplifier     0 = level I
	 * @param durationTicks how long one hit lasts
	 * @param chance        per-second chance in [0, 1]
	 */
	public record Symptom(Identifier effect, int amplifier, int durationTicks, float chance) {
		public static final Codec<Symptom> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("effect").forGetter(Symptom::effect),
				Codec.intRange(0, 9).optionalFieldOf("amplifier", 0).forGetter(Symptom::amplifier),
				Codec.floatRange(0.05f, 3600).optionalFieldOf("duration_seconds", 3f).forGetter(s -> s.durationTicks / (float) TICKS_PER_SECOND),
				Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(Symptom::chance)
		).apply(i, (effect, amp, seconds, chance) -> new Symptom(effect, amp, Math.round(seconds * TICKS_PER_SECOND), chance)));
	}

	/**
	 * Chance that one damaging zombie hit infects a player.
	 *
	 * @param zombieHitChance         base chance for a zombie with no known variant (other mods' zombies)
	 * @param variantChance           Director variant name ({@code patient}) -> chance, replacing the base
	 * @param zoneMultiplier          zone id -> multiplier on the chance where the player stands (Mission is
	 *                                the "highest infection pressure" district)
	 * @param armorReductionPerPoint  each armour point removes this share of the chance (armour still matters)
	 * @param maxChance               cap after every multiplier, so no hit is ever a certain infection
	 * @param hazmatReductionPerPiece each worn Hazmat piece removes this share of every infection roll
	 */
	public record Sources(float zombieHitChance, Map<String, Float> variantChance, Map<String, Float> zoneMultiplier,
						  float armorReductionPerPoint, float maxChance, float hazmatReductionPerPiece) {
		public static final Sources DEFAULT = new Sources(0.06f, Map.of("patient", 0.2f), Map.of("mission", 1.5f), 0.02f, 0.75f, 0.15f);
		public static final Codec<Sources> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0, 1).optionalFieldOf("zombie_hit_chance", DEFAULT.zombieHitChance).forGetter(Sources::zombieHitChance),
				Codec.unboundedMap(Codec.STRING, Codec.floatRange(0, 1)).optionalFieldOf("variant_chance", Map.of()).forGetter(Sources::variantChance),
				Codec.unboundedMap(Codec.STRING, Codec.floatRange(0, 100)).optionalFieldOf("zone_multiplier", Map.of()).forGetter(Sources::zoneMultiplier),
				Codec.floatRange(0, 1).optionalFieldOf("armor_reduction_per_point", DEFAULT.armorReductionPerPoint).forGetter(Sources::armorReductionPerPoint),
				Codec.floatRange(0, 1).optionalFieldOf("max_chance", DEFAULT.maxChance).forGetter(Sources::maxChance),
				Codec.floatRange(0, 0.25f).optionalFieldOf("hazmat_reduction_per_piece", DEFAULT.hazmatReductionPerPiece).forGetter(Sources::hazmatReductionPerPiece)
		).apply(i, Sources::new));

		/**
		 * The chance for one hit.
		 *
		 * @param variant Director variant name, or null for zombies the Director did not spawn
		 * @param zone    zone id where the player stands
		 * @param armor   the player's armour value
		 */
		public float chance(String variant, String zone, int armor) {
			float c = variant == null ? zombieHitChance : variantChance.getOrDefault(variant, zombieHitChance);
			c *= zoneMultiplier.getOrDefault(zone, 1f);
			c *= Math.max(0f, 1f - armor * armorReductionPerPoint);
			return Math.min(c, maxChance);
		}
	}

	/**
	 * @param bandageHeal          health a Bandage restores
	 * @param bandagePauseTicks    how long a Bandage holds infection progress still
	 * @param antibioticPauseTicks how long Antibiotics hold progress still after knocking it back
	 */
	public record Treatments(float bandageHeal, int bandagePauseTicks, int antibioticPauseTicks) {
		public static final Treatments DEFAULT = new Treatments(3f, 60 * TICKS_PER_SECOND, 180 * TICKS_PER_SECOND);
		public static final Codec<Treatments> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0, 40).optionalFieldOf("bandage_heal", DEFAULT.bandageHeal).forGetter(Treatments::bandageHeal),
				Codec.intRange(0, 360_000).optionalFieldOf("bandage_pause_seconds", DEFAULT.bandagePauseTicks / TICKS_PER_SECOND)
						.forGetter(t -> t.bandagePauseTicks / TICKS_PER_SECOND),
				Codec.intRange(0, 360_000).optionalFieldOf("antibiotics_pause_seconds", DEFAULT.antibioticPauseTicks / TICKS_PER_SECOND)
						.forGetter(t -> t.antibioticPauseTicks / TICKS_PER_SECOND)
		).apply(i, (heal, bandage, antibiotics) -> new Treatments(heal, bandage * TICKS_PER_SECOND, antibiotics * TICKS_PER_SECOND)));
	}

	private static final Codec<InfectionRules> RAW_CODEC = RecordCodecBuilder.create(i -> i.group(
			Stage.CODEC.listOf().fieldOf("stages").forGetter(InfectionRules::stages),
			Codec.intRange(1, 10_000_000).fieldOf("death_after_seconds").forGetter(r -> r.deathAfterTicks / TICKS_PER_SECOND),
			Sources.CODEC.optionalFieldOf("sources", Sources.DEFAULT).forGetter(InfectionRules::sources),
			Treatments.CODEC.optionalFieldOf("treatments", Treatments.DEFAULT).forGetter(InfectionRules::treatments)
	).apply(i, (stages, death, sources, treatments) -> new InfectionRules(stages, death * TICKS_PER_SECOND, sources, treatments)));

	/** Refuses files that would make the timeline ambiguous, so a bad edit fails loudly at reload. */
	public static final Codec<InfectionRules> CODEC = RAW_CODEC.validate(InfectionRules::validate);

	/**
	 * Built-in timeline, the same as the shipped stages.json: used before the first datapack load and in
	 * tests, so the system never runs without rules.
	 */
	public static final InfectionRules DEFAULT = new InfectionRules(List.of(
			new Stage(1, 0, 0f, 0f, List.of()),
			new Stage(2, 300 * TICKS_PER_SECOND, 0.005f, 0.008f, List.of()),
			new Stage(3, 720 * TICKS_PER_SECOND, 0.006f, 0.01f, List.of(
					new Symptom(Identifier.withDefaultNamespace("slowness"), 0, 30, 1f),
					new Symptom(Identifier.withDefaultNamespace("weakness"), 0, 30, 1f),
					new Symptom(Identifier.withDefaultNamespace("nausea"), 0, 160, 0.02f))),
			new Stage(4, 1200 * TICKS_PER_SECOND, 0.008f, 0.012f, List.of(
					new Symptom(Identifier.withDefaultNamespace("slowness"), 1, 30, 1f),
					new Symptom(Identifier.withDefaultNamespace("weakness"), 1, 30, 1f),
					new Symptom(Identifier.withDefaultNamespace("mining_fatigue"), 0, 30, 1f),
					new Symptom(Identifier.withDefaultNamespace("nausea"), 0, 200, 0.05f)))),
			480 * TICKS_PER_SECOND, Sources.DEFAULT, Treatments.DEFAULT);

	public InfectionRules {
		stages = stages.stream().sorted(Comparator.comparingInt(Stage::stage)).toList();
	}

	private static DataResult<InfectionRules> validate(InfectionRules rules) {
		List<Stage> s = rules.stages;
		if (s.size() != MAX_STAGE) {
			return DataResult.error(() -> "infection needs exactly " + MAX_STAGE + " stages, got " + s.size());
		}
		for (int n = 0; n < MAX_STAGE; n++) {
			if (s.get(n).stage != n + 1) {
				return DataResult.error(() -> "infection stages must be numbered 1.." + MAX_STAGE);
			}
			if (n == 0 ? s.get(0).startsAtTicks != 0 : s.get(n).startsAtTicks <= s.get(n - 1).startsAtTicks) {
				return DataResult.error(() -> "stage 1 must start at 0 and every later stage strictly after the one before");
			}
		}
		return DataResult.success(rules);
	}

	/** Stage at a given progress: 0 when not infected, otherwise the last stage that has begun. */
	public int stageAt(InfectionData data) {
		if (!data.infected()) {
			return 0;
		}
		int stage = 1;
		for (Stage s : stages) {
			if (data.progress() >= s.startsAtTicks) {
				stage = s.stage;
			}
		}
		return stage;
	}

	public Stage stage(int stage) {
		return stages.get(Math.clamp(stage, 1, MAX_STAGE) - 1);
	}

	/** Progress at which the infection kills: the start of stage 4 plus its timeout. */
	public long deathAt() {
		return (long) stage(MAX_STAGE).startsAtTicks + deathAfterTicks;
	}

	public boolean isFatal(InfectionData data) {
		return data.infected() && data.progress() >= deathAt();
	}

	/** A fresh infection: stage 1, no pause. */
	public InfectionData infect() {
		return new InfectionData(true, 0L, 0);
	}

	/** Infected and moved to the very start of {@code stage} (the testing command). */
	public InfectionData atStage(int stage) {
		return new InfectionData(true, stage(stage).startsAtTicks, 0);
	}

	/**
	 * {@code ticks} of time passing. A running pause is used up first; only what is left of the time moves
	 * the infection on. Healthy players never change.
	 */
	public InfectionData advance(InfectionData data, long ticks) {
		if (!data.infected() || ticks <= 0) {
			return data;
		}
		long paused = Math.min(ticks, data.pauseTicks());
		return new InfectionData(true, data.progress() + (ticks - paused), (int) (data.pauseTicks() - paused));
	}

	/** Bandage: progress holds still for a while. A longer pause already running is kept, never shortened. */
	public InfectionData bandage(InfectionData data) {
		if (!data.infected()) {
			return data;
		}
		return new InfectionData(true, data.progress(), Math.max(data.pauseTicks(), treatments.bandagePauseTicks));
	}

	/**
	 * Antibiotics: stage 1 is cured outright, later stages drop to the start of the stage before, and
	 * progress then pauses. After the Sever ending they cure at any stage (user decision 2026-10-25: there is
	 * still no cure item, the ending is what makes the medicine enough).
	 *
	 * @param severEnding whether the {@link #SEVER_FLAG} story flag is set
	 */
	public InfectionData antibiotics(InfectionData data, boolean severEnding) {
		int stage = stageAt(data);
		if (stage == 0) {
			return data;
		}
		if (stage == 1 || severEnding) {
			return InfectionData.HEALTHY;
		}
		return new InfectionData(true, stage(stage - 1).startsAtTicks, Math.max(data.pauseTicks(), treatments.antibioticPauseTicks));
	}

	/**
	 * The Sever-ending hook: true once the party chose Sever. Backed by the world's {@link StoryFlags} saved
	 * data, so the ending controller (M9.5) only has to set the flag, and {@code /ashfall story sever} can
	 * set it for testing. Off in every new world.
	 */
	public static boolean severEnding(MinecraftServer server) {
		return server != null && StoryFlags.get(server).has(SEVER_FLAG);
	}
}
