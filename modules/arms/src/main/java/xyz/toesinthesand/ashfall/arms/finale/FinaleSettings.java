package xyz.toesinthesand.ashfall.arms.finale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.List;
import java.util.Map;

/**
 * The Black Start's numbers (endgame-design section 4), from {@code data/ashfall/ashfall/finale/settings.json}, so
 * the finale is balanced with a datapack edit and {@code /reload}. Every field is optional and falls back to the
 * default here; the shipped file repeats the defaults so admins can see them. Times are in seconds in the file and
 * converted to ticks by the accessors the logic uses.
 *
 * @param surgeSeconds       powered seconds the Surge needs; it only counts while the energy buffer holds charge
 * @param buffer             the Uplink's energy buffer (Energy Cells buy time)
 * @param stimulus           how loud and bright the running Uplink is for the Director
 * @param waves              the horde waves at the ridge edge, and the mob cap
 * @param breakers           the Feedback phase's two Relay Breakers
 * @param vents              special infected pouring out of the hull vents during Feedback
 * @param uplink             the Uplink's health and what damages and repairs it
 * @param presenceRadius     a player this close to the socket takes part (and keeps the run alive)
 * @param abandonSeconds     seconds with no living player within {@link #presenceRadius} before the run pauses
 * @param failSetbackSeconds Surge seconds lost when the run fails (failing costs time, not the run)
 * @param heart              the Heart phase: pylon positions and the Choir
 * @param bands              what each phase end lights below the ridge, and the Grid % it adds
 * @param scale              per online party size: wave multiplier and breaker grace multiplier
 */
public record FinaleSettings(int surgeSeconds, Buffer buffer, Stimulus stimulus, Waves waves, Breakers breakers, Vents vents,
		Uplink uplink, int presenceRadius, int abandonSeconds, int failSetbackSeconds, Heart heart, List<Band> bands,
		Scale scale) {

	/** A block offset in JSON as {@code [x, y, z]}. */
	public static final Codec<Vec3i> OFFSET = Codec.INT.listOf(3, 3).xmap(l -> new Vec3i(l.get(0), l.get(1), l.get(2)),
			v -> List.of(v.getX(), v.getY(), v.getZ()));

	/**
	 * @param startSeconds seconds of charge the Uplink brings with it when seated (its recipe holds 16 cells)
	 * @param cellSeconds  seconds one Energy Cell fed into the socket buys ("each Energy Cell buys 60 seconds")
	 * @param maxSeconds   the buffer's ceiling, so a party cannot bank the whole Surge up front and walk away
	 * @param cellMinEnergy charge a cell needs to count; full by default, like the Substation Cores
	 */
	public record Buffer(int startSeconds, int cellSeconds, int maxSeconds, long cellMinEnergy) {
		public static final Buffer DEFAULT = new Buffer(60, 45, 600, 240L);
		public static final Codec<Buffer> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 36_000).optionalFieldOf("start_seconds", DEFAULT.startSeconds).forGetter(Buffer::startSeconds),
				Codec.intRange(1, 3600).optionalFieldOf("cell_seconds", DEFAULT.cellSeconds).forGetter(Buffer::cellSeconds),
				Codec.intRange(1, 36_000).optionalFieldOf("max_seconds", DEFAULT.maxSeconds).forGetter(Buffer::maxSeconds),
				Codec.LONG.optionalFieldOf("cell_min_energy", DEFAULT.cellMinEnergy).forGetter(Buffer::cellMinEnergy)
		).apply(i, Buffer::new));
	}

	/**
	 * The Uplink is the loudest noise and the brightest light in the game: both radii are larger than anything else
	 * the Director hears or sees (the Substation hum is 144, a screamer 48).
	 */
	public record Stimulus(int intervalSeconds, int soundRadius, int lightRadius) {
		public static final Stimulus DEFAULT = new Stimulus(4, 192, 128);
		public static final Codec<Stimulus> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 600).optionalFieldOf("interval_seconds", DEFAULT.intervalSeconds).forGetter(Stimulus::intervalSeconds),
				Codec.intRange(1, 1024).optionalFieldOf("sound_radius", DEFAULT.soundRadius).forGetter(Stimulus::soundRadius),
				Codec.intRange(1, 1024).optionalFieldOf("light_radius", DEFAULT.lightRadius).forGetter(Stimulus::lightRadius)
		).apply(i, Stimulus::new));
	}

	/**
	 * Horde waves at the ridge edge (the horde scheduler's ring, 40-64 blocks out, out of sight): they never path in
	 * from across the map. One interval per phase; 0 turns a phase's waves off.
	 *
	 * @param size              zombies per wave before party scaling
	 * @param surgeInterval     seconds between waves in the Surge
	 * @param feedbackInterval  ... in Feedback
	 * @param heartInterval     ... at the Heart
	 * @param maxMobs           the finale's mob cap: no wave or vent spawn while this many finale mobs are alive
	 * @param capRadius         finale mobs this close to the socket count towards {@link #maxMobs}
	 */
	public record Waves(int size, int surgeInterval, int feedbackInterval, int heartInterval, int maxMobs, int capRadius) {
		public static final Waves DEFAULT = new Waves(22, 30, 45, 60, 120, 128);
		public static final Codec<Waves> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 200).optionalFieldOf("size", DEFAULT.size).forGetter(Waves::size),
				Codec.intRange(0, 3600).optionalFieldOf("surge_interval_seconds", DEFAULT.surgeInterval).forGetter(Waves::surgeInterval),
				Codec.intRange(0, 3600).optionalFieldOf("feedback_interval_seconds", DEFAULT.feedbackInterval).forGetter(Waves::feedbackInterval),
				Codec.intRange(0, 3600).optionalFieldOf("heart_interval_seconds", DEFAULT.heartInterval).forGetter(Waves::heartInterval),
				Codec.intRange(0, 1000).optionalFieldOf("max_mobs", DEFAULT.maxMobs).forGetter(Waves::maxMobs),
				Codec.intRange(8, 512).optionalFieldOf("cap_radius", DEFAULT.capRadius).forGetter(Waves::capRadius)
		).apply(i, Waves::new));
	}

	/**
	 * @param holdSeconds  seconds a player must stand at a tripped breaker to close it
	 * @param radius       a player this close to the breaker holds it ("within 3 blocks")
	 * @param offsets      breaker positions from the socket; the height is found on the ground there
	 * @param graceSeconds seconds an unattended breaker keeps its progress before it starts to slip back; multiplied
	 *                     by the party-size grace scale, so a solo player gets time to run between the two
	 * @param decay        hold progress lost per second once the grace is over (1 = as fast as it was gained)
	 */
	public record Breakers(int holdSeconds, double radius, List<Vec3i> offsets, int graceSeconds, double decay) {
		public static final Breakers DEFAULT = new Breakers(75, 3.0, List.of(new Vec3i(-20, 0, 0), new Vec3i(20, 0, 0)), 10, 1.0);
		public static final Codec<Breakers> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 3600).optionalFieldOf("hold_seconds", DEFAULT.holdSeconds).forGetter(Breakers::holdSeconds),
				Codec.doubleRange(0.5, 16).optionalFieldOf("radius", DEFAULT.radius).forGetter(Breakers::radius),
				OFFSET.listOf().optionalFieldOf("offsets", DEFAULT.offsets).forGetter(Breakers::offsets),
				Codec.intRange(0, 3600).optionalFieldOf("grace_seconds", DEFAULT.graceSeconds).forGetter(Breakers::graceSeconds),
				Codec.doubleRange(0, 10).optionalFieldOf("decay", DEFAULT.decay).forGetter(Breakers::decay)
		).apply(i, Breakers::new));
	}

	/**
	 * @param intervalSeconds seconds between vent bursts (Feedback only); 0 turns vents off
	 * @param count           specials per burst before party scaling
	 * @param offsets         vent spawn spots from the socket, inside the hull
	 * @param kinds           special infected ids a burst picks from
	 */
	public record Vents(int intervalSeconds, int count, List<Vec3i> offsets, List<String> kinds) {
		public static final Vents DEFAULT = new Vents(20, 2, List.of(new Vec3i(-9, 0, 0), new Vec3i(8, 0, 0)),
				List.of("crawler", "spitter", "screamer", "bloated", "brute"));
		public static final Codec<Vents> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 3600).optionalFieldOf("interval_seconds", DEFAULT.intervalSeconds).forGetter(Vents::intervalSeconds),
				Codec.intRange(0, 32).optionalFieldOf("count", DEFAULT.count).forGetter(Vents::count),
				OFFSET.listOf().optionalFieldOf("offsets", DEFAULT.offsets).forGetter(Vents::offsets),
				Codec.STRING.listOf().optionalFieldOf("kinds", DEFAULT.kinds).forGetter(Vents::kinds)
		).apply(i, Vents::new));
	}

	/**
	 * @param hp                    the Uplink's health; at 0 the run pauses
	 * @param couplerPatch          health one Grid Coupler patches back
	 * @param resumeFraction        share of {@link #hp} a downed Uplink needs again before the run resumes
	 * @param attackRadius          a finale mob this close to the socket, horizontally (and within 2 blocks of its
	 *                              height), is battering the Uplink. Six, not three: the hull's north and south walls
	 *                              stand 4 blocks from the socket, and a wave that arrives from the north (away from the
	 *                              hatch) ends its path against the north wall at 4.3-5.5 blocks. In the 1.0.0-rc1 run
	 *                              such waves stood there for minutes and never hurt the Uplink, so about half the Surge
	 *                              waves did nothing. The siege core counts its wall the same way ({@code core.attack_radius})
	 * @param damagePerZombieSecond health one battering mob takes off per second
	 * @param arcIntervalSeconds    seconds between feedback arcs in the Feedback phase (0: none)
	 * @param arcDamage             health one feedback arc takes off
	 */
	public record Uplink(int hp, int couplerPatch, double resumeFraction, double attackRadius, int damagePerZombieSecond,
			int arcIntervalSeconds, int arcDamage) {
		public static final Uplink DEFAULT = new Uplink(1200, 200, 0.5, 6.0, 1, 30, 60);
		public static final Codec<Uplink> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 100_000).optionalFieldOf("hp", DEFAULT.hp).forGetter(Uplink::hp),
				Codec.intRange(1, 100_000).optionalFieldOf("coupler_patch", DEFAULT.couplerPatch).forGetter(Uplink::couplerPatch),
				Codec.doubleRange(0, 1).optionalFieldOf("resume_fraction", DEFAULT.resumeFraction).forGetter(Uplink::resumeFraction),
				Codec.doubleRange(0.5, 16).optionalFieldOf("attack_radius", DEFAULT.attackRadius).forGetter(Uplink::attackRadius),
				Codec.intRange(0, 10_000).optionalFieldOf("damage_per_zombie_second", DEFAULT.damagePerZombieSecond)
						.forGetter(Uplink::damagePerZombieSecond),
				Codec.intRange(0, 3600).optionalFieldOf("arc_interval_seconds", DEFAULT.arcIntervalSeconds).forGetter(Uplink::arcIntervalSeconds),
				Codec.intRange(0, 100_000).optionalFieldOf("arc_damage", DEFAULT.arcDamage).forGetter(Uplink::arcDamage)
		).apply(i, Uplink::new));
	}

	/**
	 * @param pylons      Heart pylon positions from the socket (four in the hull, two to each side)
	 * @param choir       whether the Choir rises at the Heart (off only for tests and admin rehearsals)
	 * @param choirOffset where the Choir rises, from the socket
	 * @param leashRadius the Choir walks back towards the Heart when it is farther than this with nobody to fight
	 * @param barRadius   players this close to the Choir see its boss bar
	 * @param attributes  the Choir's attributes, applied over the entity's registered defaults on every spawn (the same
	 *                    numbers as {@link ChoirEntity#createChoirAttributes}: an Abomination-class body, 1.8x scale)
	 */
	public record Heart(List<Vec3i> pylons, boolean choir, Vec3i choirOffset, int leashRadius, int barRadius,
			Map<Identifier, Double> attributes) {
		public static final Heart DEFAULT = new Heart(List.of(new Vec3i(-8, 0, -2), new Vec3i(-8, 0, 2), new Vec3i(6, 0, -2),
				new Vec3i(6, 0, 2)), true, new Vec3i(-4, 0, 0), 14, 64, Map.of(
				Identifier.withDefaultNamespace("max_health"), 420.0, Identifier.withDefaultNamespace("attack_damage"), 12.0,
				Identifier.withDefaultNamespace("armor"), 10.0, Identifier.withDefaultNamespace("armor_toughness"), 4.0,
				Identifier.withDefaultNamespace("movement_speed"), 0.24, Identifier.withDefaultNamespace("knockback_resistance"), 1.0,
				Identifier.withDefaultNamespace("attack_knockback"), 1.5, Identifier.withDefaultNamespace("follow_range"), 48.0,
				Identifier.withDefaultNamespace("scale"), 1.8));
		public static final Codec<Heart> CODEC = RecordCodecBuilder.create(i -> i.group(
				OFFSET.listOf().optionalFieldOf("pylons", DEFAULT.pylons).forGetter(Heart::pylons),
				Codec.BOOL.optionalFieldOf("choir", DEFAULT.choir).forGetter(Heart::choir),
				OFFSET.optionalFieldOf("choir_offset", DEFAULT.choirOffset).forGetter(Heart::choirOffset),
				Codec.intRange(1, 256).optionalFieldOf("leash_radius", DEFAULT.leashRadius).forGetter(Heart::leashRadius),
				Codec.intRange(1, 512).optionalFieldOf("bar_radius", DEFAULT.barRadius).forGetter(Heart::barRadius),
				Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("choir_attributes", DEFAULT.attributes)
						.forGetter(Heart::attributes)
		).apply(i, Heart::new));
	}

	/**
	 * One phase end: the districts whose loaded lights come on in real time, and the Grid % bonus the city stands at
	 * afterwards (80 + bonus: 87, 94, 100).
	 */
	public record Band(List<String> districts, int bonus) {
		public static final Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("districts", List.of()).forGetter(Band::districts),
				Codec.intRange(0, 20).fieldOf("bonus").forGetter(Band::bonus)
		).apply(i, Band::new));
	}

	public static final List<Band> DEFAULT_BANDS = List.of(new Band(List.of("river_arts", "south_slope"), 7),
			new Band(List.of("downtown"), 14), new Band(List.of("mission"), 20));

	/**
	 * Party-size scaling, indexed by online players minus one (the last entry covers bigger parties). Solo gets about
	 * 60% of the waves and twice the breaker grace (endgame-design: "solo gets about 60% of the waves and longer
	 * breaker timers").
	 */
	public record Scale(List<Double> waves, List<Double> breakerGrace) {
		public static final Scale DEFAULT = new Scale(List.of(0.6, 0.8, 1.0, 1.15, 1.3), List.of(2.0, 1.5, 1.0));
		public static final Codec<Scale> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.doubleRange(0, 10).listOf(1, 32).optionalFieldOf("waves", DEFAULT.waves).forGetter(Scale::waves),
				Codec.doubleRange(0, 10).listOf(1, 32).optionalFieldOf("breaker_grace", DEFAULT.breakerGrace).forGetter(Scale::breakerGrace)
		).apply(i, Scale::new));

		static double pick(List<Double> list, int players) {
			return list.get(Math.clamp(players - 1, 0, list.size() - 1));
		}

		/** Wave multiplier for {@code players} online party members (0 counts as solo). */
		public double waves(int players) {
			return pick(waves, players);
		}

		public double breakerGrace(int players) {
			return pick(breakerGrace, players);
		}
	}

	public static final FinaleSettings DEFAULT = new FinaleSettings(450, Buffer.DEFAULT, Stimulus.DEFAULT, Waves.DEFAULT,
			Breakers.DEFAULT, Vents.DEFAULT, Uplink.DEFAULT, 96, 20, 60, Heart.DEFAULT, DEFAULT_BANDS, Scale.DEFAULT);

	public static final Codec<FinaleSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 36_000).optionalFieldOf("surge_seconds", DEFAULT.surgeSeconds).forGetter(FinaleSettings::surgeSeconds),
			Buffer.CODEC.optionalFieldOf("buffer", Buffer.DEFAULT).forGetter(FinaleSettings::buffer),
			Stimulus.CODEC.optionalFieldOf("stimulus", Stimulus.DEFAULT).forGetter(FinaleSettings::stimulus),
			Waves.CODEC.optionalFieldOf("waves", Waves.DEFAULT).forGetter(FinaleSettings::waves),
			Breakers.CODEC.optionalFieldOf("breakers", Breakers.DEFAULT).forGetter(FinaleSettings::breakers),
			Vents.CODEC.optionalFieldOf("vents", Vents.DEFAULT).forGetter(FinaleSettings::vents),
			Uplink.CODEC.optionalFieldOf("uplink", Uplink.DEFAULT).forGetter(FinaleSettings::uplink),
			Codec.intRange(8, 512).optionalFieldOf("presence_radius", DEFAULT.presenceRadius).forGetter(FinaleSettings::presenceRadius),
			Codec.intRange(1, 3600).optionalFieldOf("abandon_seconds", DEFAULT.abandonSeconds).forGetter(FinaleSettings::abandonSeconds),
			Codec.intRange(0, 36_000).optionalFieldOf("fail_setback_seconds", DEFAULT.failSetbackSeconds)
					.forGetter(FinaleSettings::failSetbackSeconds),
			Heart.CODEC.optionalFieldOf("heart", Heart.DEFAULT).forGetter(FinaleSettings::heart),
			Band.CODEC.listOf(3, 3).optionalFieldOf("bands", DEFAULT_BANDS).forGetter(FinaleSettings::bands),
			Scale.CODEC.optionalFieldOf("scale", Scale.DEFAULT).forGetter(FinaleSettings::scale)
	).apply(i, FinaleSettings::new));

	public long surgeTicks() {
		return surgeSeconds * 20L;
	}

	public long cellTicks() {
		return buffer.cellSeconds() * 20L;
	}

	public long maxBufferTicks() {
		return buffer.maxSeconds() * 20L;
	}

	public long holdTicks() {
		return breakers.holdSeconds() * 20L;
	}

	/** The Grid % bonus once {@code bandsLit} phases have ended (0 before the first). */
	public int bonus(int bandsLit) {
		return bandsLit <= 0 ? 0 : bands.get(Math.min(bandsLit, bands.size()) - 1).bonus();
	}

	private static volatile FinaleSettings current = DEFAULT;

	public static FinaleSettings get() {
		return current;
	}

	/** Reload listener for {@code data/<ns>/ashfall/finale/settings.json}; the {@code ashfall} file wins. */
	public static final class Loader extends SimpleJsonResourceReloadListener<FinaleSettings> {
		public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "finale_settings");
		private static final Identifier FILE = Identifier.fromNamespaceAndPath("ashfall", "settings");

		public Loader() {
			super(CODEC, FileToIdConverter.json("ashfall/finale"));
		}

		@Override
		protected void apply(Map<Identifier, FinaleSettings> loaded, ResourceManager manager, ProfilerFiller profiler) {
			current = loaded.getOrDefault(FILE, DEFAULT);
			AshfallArms.LOGGER.info("Finale settings loaded (surge {} s, uplink {} hp, {} pylons)", current.surgeSeconds,
					current.uplink.hp(), current.heart.pylons().size());
		}
	}
}
