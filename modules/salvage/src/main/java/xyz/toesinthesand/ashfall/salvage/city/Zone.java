package xyz.toesinthesand.ashfall.salvage.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * One area of the fixed Asheville layout, loaded from {@code data/<ns>/ashfall/zones/<id>.json}.
 * <p>
 * The same record feeds every system that needs to know "where am I": the Lost Cities hooks (whether a
 * chunk is city, which city style it uses, how often its buildings are ruined), and later the Director
 * (population scale), the damage pass, the Grid Map and district membership. Keeping one source avoids
 * the drift we had when districts were polygons in one file and building tags in another.
 *
 * @param id               the file name; also accepted as an informational {@code "id"} field in JSON
 * @param kind             district, start, filler, outskirts, ridge or wild (informational for now)
 * @param districtIndex    1..4 for the four scored districts, 0 otherwise
 * @param tier             loot/threat tier (1..5)
 * @param priority         higher wins where shapes overlap (districts over the suburbs disc, and so on)
 * @param city             false makes Lost Cities treat the zone as wilderness
 * @param cityStyle        the Lost Cities city style id used for every chunk in the zone
 * @param ruinChance       chance that a building here is ruined; replaces the profile-wide ruin chance
 * @param damageScale      intensity multiplier for our own damage pass
 * @param populationScale  multiplier for Director building populations
 * @param landmarks        Lost Cities building ids that must exist in the zone (authored in later stages)
 * @param keyRelayTargets  block (x, z) points near which the district's key Power Relays go
 * @param director         how the Director's ambient spawning and early populations differ here
 * @param sites            authored structures our own code places at fixed columns of the zone (the Wreck hull on
 *                         the ridge); unlike Lost Cities landmarks they exist outside the city generator
 */
public record Zone(String id, String kind, int districtIndex, int tier, int priority, ZoneShape shape, boolean city,
		Optional<String> cityStyle, float ruinChance, float damageScale, float populationScale, List<String> landmarks,
		List<List<Integer>> keyRelayTargets, DirectorScales director, List<FixedSite> sites) {

	/**
	 * A structure placed at one fixed block column of the zone. The position lives in the zone file (not in
	 * code) for the same reason the district discs do: the Grid Map, the story and the boss lair data all
	 * point at it, so it must be authored in one place.
	 *
	 * @param id       site id other data refers to (a boss lair's {@code "site"}), e.g. {@code ashfall:wreck_hull}
	 * @param template {@code data/<ns>/ashfall/site_template/<path>.json} to place
	 * @param x        block x of the template's centre column
	 * @param z        block z of the template's centre column
	 */
	public record FixedSite(String id, String template, int x, int z) {
		public static final Codec<FixedSite> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(FixedSite::id),
				Codec.STRING.fieldOf("template").forGetter(FixedSite::template),
				Codec.INT.fieldOf("x").forGetter(FixedSite::x),
				Codec.INT.fieldOf("z").forGetter(FixedSite::z)
		).apply(i, FixedSite::new));
	}

	/** A zone with no fixed sites (every zone that predates the Wreck hull). */
	public Zone(String id, String kind, int districtIndex, int tier, int priority, ZoneShape shape, boolean city,
			Optional<String> cityStyle, float ruinChance, float damageScale, float populationScale, List<String> landmarks,
			List<List<Integer>> keyRelayTargets, DirectorScales director) {
		this(id, kind, districtIndex, tier, priority, shape, city, cityStyle, ruinChance, damageScale, populationScale,
				landmarks, keyRelayTargets, director, List.of());
	}

	/**
	 * Per-zone Director tuning (plan section 1.2, "Director" column), nested under {@code "director"} so the
	 * zone file stays the single source the plan asks for without growing the top level further.
	 *
	 * @param darknessScale multiplier on the ambient spawn weight of dark spots (light 7 or less): the
	 *                      wasteland's "darkness spawning x1.3". Lit spots keep their weight, so a zone
	 *                      with no buildings is dangerous at night and in shadow rather than everywhere.
	 * @param gentleDays    the first this-many world days use {@code gentleScale} on top of everything else;
	 *                      Montford's "x0.6 for the first 3 days" so new players can find their feet
	 * @param gentleScale   multiplier on building populations and ambient weight during the gentle days
	 */
	public record DirectorScales(float darknessScale, int gentleDays, float gentleScale) {
		public static final DirectorScales DEFAULT = new DirectorScales(1f, 0, 1f);
		public static final Codec<DirectorScales> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0, 10).optionalFieldOf("darkness_scale", 1f).forGetter(DirectorScales::darknessScale),
				Codec.intRange(0, 100_000).optionalFieldOf("gentle_days", 0).forGetter(DirectorScales::gentleDays),
				Codec.floatRange(0, 10).optionalFieldOf("gentle_scale", 1f).forGetter(DirectorScales::gentleScale)
		).apply(i, DirectorScales::new));

		/** The gentle multiplier on world day {@code day} (1-based): {@code gentleScale} while it lasts, else 1. */
		public float gentle(int day) {
			return day <= gentleDays ? gentleScale : 1f;
		}
	}

	/** A zone with default Director tuning (tests and callers that predate {@link DirectorScales}). */
	public Zone(String id, String kind, int districtIndex, int tier, int priority, ZoneShape shape, boolean city,
			Optional<String> cityStyle, float ruinChance, float damageScale, float populationScale, List<String> landmarks,
			List<List<Integer>> keyRelayTargets) {
		this(id, kind, districtIndex, tier, priority, shape, city, cityStyle, ruinChance, damageScale, populationScale,
				landmarks, keyRelayTargets, DirectorScales.DEFAULT);
	}

	public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("id", "").forGetter(Zone::id),
			Codec.STRING.optionalFieldOf("kind", "filler").forGetter(Zone::kind),
			Codec.INT.optionalFieldOf("district_index", 0).forGetter(Zone::districtIndex),
			Codec.INT.optionalFieldOf("tier", 1).forGetter(Zone::tier),
			Codec.INT.optionalFieldOf("priority", 0).forGetter(Zone::priority),
			ZoneShape.CODEC.fieldOf("shape").forGetter(Zone::shape),
			Codec.BOOL.optionalFieldOf("city", true).forGetter(Zone::city),
			Codec.STRING.optionalFieldOf("citystyle").forGetter(Zone::cityStyle),
			Codec.FLOAT.optionalFieldOf("ruin_chance", 0.1f).forGetter(Zone::ruinChance),
			Codec.FLOAT.optionalFieldOf("damage_scale", 1.0f).forGetter(Zone::damageScale),
			Codec.FLOAT.optionalFieldOf("population_scale", 1.0f).forGetter(Zone::populationScale),
			Codec.STRING.listOf().optionalFieldOf("landmarks", List.of()).forGetter(Zone::landmarks),
			Codec.INT.listOf().listOf().optionalFieldOf("key_relay_targets", List.of()).forGetter(Zone::keyRelayTargets),
			DirectorScales.CODEC.optionalFieldOf("director", DirectorScales.DEFAULT).forGetter(Zone::director),
			FixedSite.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(Zone::sites)
	).apply(i, Zone::new));

	/**
	 * The zone used when no data is loaded (client-side world-creation previews, unit tests without data):
	 * it matches everywhere and changes nothing, so Lost Cities falls back to its own behaviour.
	 */
	public static final Zone UNKNOWN = new Zone("unknown", "wild", 0, 1, Integer.MIN_VALUE, new ZoneShape.All(), true,
			Optional.empty(), -1f, 1f, 1f, List.of(), List.of());

	/** A copy with the id taken from the data file name, which is authoritative. */
	public Zone withId(String newId) {
		return new Zone(newId, kind, districtIndex, tier, priority, shape, city, cityStyle, ruinChance, damageScale,
				populationScale, landmarks, keyRelayTargets, director, sites);
	}

	public boolean isDistrict() {
		return districtIndex > 0;
	}

	public boolean contains(int x, int z) {
		return shape.contains(x, z);
	}
}
