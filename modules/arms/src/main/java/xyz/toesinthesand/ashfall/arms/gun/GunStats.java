package xyz.toesinthesand.ashfall.arms.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Balance numbers for one gun, loaded from {@code data/<ns>/ashfall/energy_gun/<item path>.json} so
 * admins can rebalance with a datapack and {@code /reload}. The file name is the gun's item id.
 * <p>
 * Firing mode (semi/auto) and which cell pack fits are part of the item's identity and live on
 * {@link EnergyGunItem}, not here: the client must know them before any stats have synced.
 *
 * @param damage             base damage per bolt (before headshot and attachment multipliers)
 * @param rpm                rounds per minute; converted to whole ticks between shots
 * @param energyPerShot      energy units drawn from the loaded pack (fractions accumulate, see {@link EnergyCost})
 * @param heatPerShot        heat added per shot
 * @param coolPerSecond      heat lost per second (0.8x while locked)
 * @param noiseRadius        radius passed to {@link ArmsNoise#emit} per shot
 * @param boltSpeed          bolt speed in blocks per tick
 * @param spread             inaccuracy in roughly degrees (vanilla projectile inaccuracy units)
 * @param boltColor          0xRRGGBB, written as {@code "#rrggbb"}
 * @param heatCapacity       heat at which the gun locks
 * @param headshotMultiplier damage multiplier for head hits
 * @param range              blocks a bolt may travel before fizzling
 * @param scopeZoom          optional {@code scope_zoom}: the highest magnification (e.g. 7 for 7x) this gun's scope
 *                           reaches with an Optic fitted, for marksman rifles; 0 (absent) uses the Optic's own zoom.
 *                           Client-side only, see {@link ScopeMath#levels}
 * @param drawTicks          optional {@code draw_ticks}: item cooldown when the gun is drawn into the main hand,
 *                           see {@link DrawTime}; 0 (absent) draws instantly
 * @param stunEnergyMultiplier optional {@code stun_energy_multiplier}: energy cost multiplier of a stun shot; 0
 *                           (absent) means the gun has no stun mode
 */
public record GunStats(float damage, float rpm, float energyPerShot, float heatPerShot, float coolPerSecond,
					   float noiseRadius, float boltSpeed, float spread, int boltColor, float heatCapacity,
					   float headshotMultiplier, float range, float scopeZoom, int drawTicks, float stunEnergyMultiplier) {
	public static final float DEFAULT_HEAT_CAPACITY = 100f;
	public static final float DEFAULT_HEADSHOT_MULTIPLIER = 1.75f;
	public static final float DEFAULT_RANGE = 96f;

	private static final Codec<Float> NON_NEGATIVE = Codec.floatRange(0f, Float.MAX_VALUE);
	private static final Codec<Float> POSITIVE = Codec.floatRange(1e-3f, Float.MAX_VALUE);
	public static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(text -> {
		try {
			return DataResult.success(ColorHex.parse(text));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid bolt colour '" + text + "': expected #rrggbb");
		}
	}, ColorHex::format);

	public static final Codec<GunStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			NON_NEGATIVE.fieldOf("damage").forGetter(GunStats::damage),
			POSITIVE.fieldOf("rpm").forGetter(GunStats::rpm),
			NON_NEGATIVE.fieldOf("energy_per_shot").forGetter(GunStats::energyPerShot),
			NON_NEGATIVE.fieldOf("heat_per_shot").forGetter(GunStats::heatPerShot),
			NON_NEGATIVE.fieldOf("cool_per_second").forGetter(GunStats::coolPerSecond),
			NON_NEGATIVE.fieldOf("noise_radius").forGetter(GunStats::noiseRadius),
			POSITIVE.fieldOf("bolt_speed").forGetter(GunStats::boltSpeed),
			NON_NEGATIVE.optionalFieldOf("spread", 0f).forGetter(GunStats::spread),
			COLOR.fieldOf("bolt_color").forGetter(GunStats::boltColor),
			POSITIVE.optionalFieldOf("heat_capacity", DEFAULT_HEAT_CAPACITY).forGetter(GunStats::heatCapacity),
			NON_NEGATIVE.optionalFieldOf("headshot_multiplier", DEFAULT_HEADSHOT_MULTIPLIER).forGetter(GunStats::headshotMultiplier),
			POSITIVE.optionalFieldOf("range", DEFAULT_RANGE).forGetter(GunStats::range),
			Codec.floatRange(0f, ScopeMath.MAX_MAGNIFICATION).optionalFieldOf("scope_zoom", 0f).forGetter(GunStats::scopeZoom),
			Codec.intRange(0, 200).optionalFieldOf("draw_ticks", 0).forGetter(GunStats::drawTicks),
			NON_NEGATIVE.optionalFieldOf("stun_energy_multiplier", 0f).forGetter(GunStats::stunEnergyMultiplier)
	).apply(i, GunStats::new));

	/** Network form: the same shape as the JSON, so the client sees exactly what the server loaded. */
	public static final StreamCodec<ByteBuf, GunStats> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Game ticks between shots. */
	public int ticksBetweenShots() {
		return HeatModel.ticksBetweenShots(rpm);
	}
}
