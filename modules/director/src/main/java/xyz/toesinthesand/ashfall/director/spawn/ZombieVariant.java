package xyz.toesinthesand.ashfall.director.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One zombie variant from {@code data/<ns>/ashfall/zombie_variant/<id>.json}. For the MVP a variant is
 * a vanilla zombie-family entity with attributes and gear applied on spawn; the variant id is what the
 * {@code ashfall_variant=<id>} tag and the {@link DirectorZombie} attachment record.
 *
 * @param entity       zombie-family entity to spawn (default {@code minecraft:zombie}); anything that is
 *                     not a {@code Zombie} subclass is refused at spawn time, per the zombies-only rule
 * @param weight       relative pick weight among eligible variants
 * @param minDay       first world day (1-based) the variant can appear
 * @param environment  where the variant may spawn: land (also hot land), hot land only, or water only
 * @param babyChance   chance of a baby; 0 by default so variant stats stay what the file says
 * @param attributes   attribute id -> base value; modifiers vanilla added on spawn are stripped first
 * @param equipment    random gear rolled on spawn (vanilla's random armour is always removed)
 * @param buildingBias building profile id -> weight multiplier for building populations
 *                     (police stations make armoured zombies more likely)
 * @param conditions   where the variant may spawn at all (zone, inside a building, on a street, building id)
 * @param zoneBias     zone id -> weight multiplier; how Mission's "highest infection pressure" makes patients
 *                     common there without a separate table per zone
 */
public record ZombieVariant(Identifier entity, int weight, int minDay, Environment environment, float babyChance,
							Map<Identifier, Double> attributes, Equipment equipment, Map<Identifier, Float> buildingBias,
							Conditions conditions, Map<String, Float> zoneBias) {
	public static final Identifier ZOMBIE = Identifier.withDefaultNamespace("zombie");

	/**
	 * Spawn-table conditions over a {@link SpawnContext}. Every condition that is set must hold; an empty
	 * {@link #ANY} matches everywhere, so old variant files keep their behaviour.
	 *
	 * @param zones      zone ids the variant may spawn in; empty = any zone
	 * @param inBuilding when present, the spot must (true) or must not (false) be inside a building
	 * @param onStreet   when present, the spot must (true) or must not (false) be on a city street chunk
	 * @param buildings  Lost Cities building ids the spot must be in; empty = any (or none)
	 */
	public record Conditions(List<String> zones, Optional<Boolean> inBuilding, Optional<Boolean> onStreet, List<String> buildings) {
		public static final Conditions ANY = new Conditions(List.of(), Optional.empty(), Optional.empty(), List.of());
		public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("zones", List.of()).forGetter(Conditions::zones),
				Codec.BOOL.optionalFieldOf("in_building").forGetter(Conditions::inBuilding),
				Codec.BOOL.optionalFieldOf("on_street").forGetter(Conditions::onStreet),
				Codec.STRING.listOf().optionalFieldOf("buildings", List.of()).forGetter(Conditions::buildings)
		).apply(i, Conditions::new));

		public boolean matches(SpawnContext ctx) {
			if (!zones.isEmpty() && !zones.contains(ctx.zone())) {
				return false;
			}
			if (inBuilding.isPresent() && inBuilding.get() != ctx.inBuilding()) {
				return false;
			}
			if (onStreet.isPresent() && onStreet.get() != ctx.onStreet()) {
				return false;
			}
			return buildings.isEmpty() || (ctx.building() != null && buildings.contains(ctx.building()));
		}
	}

	/** A variant with no conditions and no zone bias (tests and code that predates spawn contexts). */
	public ZombieVariant(Identifier entity, int weight, int minDay, Environment environment, float babyChance,
						 Map<Identifier, Double> attributes, Equipment equipment, Map<Identifier, Float> buildingBias) {
		this(entity, weight, minDay, environment, babyChance, attributes, equipment, buildingBias, Conditions.ANY, Map.of());
	}

	public enum Environment implements StringRepresentable {
		LAND, HOT, WATER;

		public static final Codec<Environment> CODEC = StringRepresentable.fromEnum(Environment::values);

		/** Whether a variant declared for this environment may spawn at a spot of {@code spot}'s kind. */
		public boolean allows(Environment spot) {
			return switch (this) {
				case LAND -> spot == LAND || spot == HOT;
				case HOT -> spot == HOT;
				case WATER -> spot == WATER;
			};
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * One themed outfit (police, worker): per slot name ({@code head}, {@code chest}, {@code legs},
	 * {@code feet}, {@code mainhand}, {@code offhand}) the items it may roll.
	 */
	public record GearSet(int weight, Map<String, List<Identifier>> slots) {
		public static final Codec<GearSet> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(GearSet::weight),
				Codec.unboundedMap(Codec.STRING, Identifier.CODEC.listOf()).fieldOf("slots").forGetter(GearSet::slots)
		).apply(i, GearSet::new));
	}

	/** Picks one set by weight, then {@code min..max} distinct slots of it, one random item each. */
	public record Equipment(int min, int max, float dropChance, List<GearSet> sets) {
		public static final Equipment NONE = new Equipment(0, 0, 0.085f, List.of());
		public static final Codec<Equipment> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 6).optionalFieldOf("min", 0).forGetter(Equipment::min),
				Codec.intRange(0, 6).optionalFieldOf("max", 0).forGetter(Equipment::max),
				Codec.floatRange(0, 1).optionalFieldOf("drop_chance", 0.085f).forGetter(Equipment::dropChance),
				GearSet.CODEC.listOf().optionalFieldOf("sets", List.of()).forGetter(Equipment::sets)
		).apply(i, Equipment::new));
	}

	public static final Codec<ZombieVariant> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("entity", ZOMBIE).forGetter(ZombieVariant::entity),
			Codec.intRange(0, 10_000).fieldOf("weight").forGetter(ZombieVariant::weight),
			Codec.intRange(0, 100_000).optionalFieldOf("min_day", 1).forGetter(ZombieVariant::minDay),
			Environment.CODEC.optionalFieldOf("environment", Environment.LAND).forGetter(ZombieVariant::environment),
			Codec.floatRange(0, 1).optionalFieldOf("baby_chance", 0f).forGetter(ZombieVariant::babyChance),
			Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("attributes", Map.of()).forGetter(ZombieVariant::attributes),
			Equipment.CODEC.optionalFieldOf("equipment", Equipment.NONE).forGetter(ZombieVariant::equipment),
			Codec.unboundedMap(Identifier.CODEC, Codec.floatRange(0, 1000)).optionalFieldOf("building_bias", Map.of()).forGetter(ZombieVariant::buildingBias),
			Conditions.CODEC.optionalFieldOf("conditions", Conditions.ANY).forGetter(ZombieVariant::conditions),
			Codec.unboundedMap(Codec.STRING, Codec.floatRange(0, 1000)).optionalFieldOf("zone_bias", Map.of()).forGetter(ZombieVariant::zoneBias)
	).apply(i, ZombieVariant::new));

	/** Plain vanilla zombie, used when no datapack variant is eligible so spawning never silently stops. */
	public static final ZombieVariant FALLBACK = new ZombieVariant(ZOMBIE, 1, 1, Environment.LAND, 0f, Map.of(), Equipment.NONE, Map.of());
	public static final String FALLBACK_ID = "walker";

	/** Pick weight in a given building profile (null outside buildings). */
	public double weightIn(Identifier profile) {
		double w = weight;
		if (profile != null) {
			w *= buildingBias.getOrDefault(profile, 1.0f);
		}
		return w;
	}

	/** Pick weight in a building profile (null outside buildings) and zone. */
	public double weightIn(Identifier profile, SpawnContext ctx) {
		return weightIn(profile) * zoneBias.getOrDefault(ctx.zone(), 1.0f);
	}

	public boolean eligible(int day, Environment spot) {
		return day >= minDay && environment.allows(spot);
	}

	/** Day and environment as before, plus the spawn-table conditions against the spot's context. */
	public boolean eligible(int day, Environment spot, SpawnContext ctx) {
		return eligible(day, spot) && conditions.matches(ctx);
	}
}
