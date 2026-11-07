package xyz.toesinthesand.ashfall.arms.blade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Plasma Blade balance numbers, from {@code data/<ns>/ashfall/plasma_blade/<item path>.json}, so admins
 * rebalance with a datapack and {@code /reload}. Server side only: the client needs none of these
 * (the charge bar uses the item's fixed capacity, the crystal readout travels in {@link BladeCrystal}).
 *
 * @param damage                 damage of a powered first hit (the tooltip shows the item attribute, 9)
 * @param unpoweredDamage        damage of the bare hilt: no charge, or a dull crystal
 * @param energyPerSwing         energy drawn once per swing that connects, however many targets it hits
 * @param crystalIntegrity       hits a freshly socketed Resonance Crystal lasts
 * @param wearPerHit             integrity lost per target hit by a powered swing
 * @param maxTargets             targets one swing may damage; the rest take nothing (plan: 3)
 * @param extraTargetMultiplier  damage share of the 2nd..maxTargets-th target (plan: 60%)
 * @param extraTargetExhaustion  food exhaustion per extra target (4.0 is one hunger point); the first target costs nothing extra
 * @param noiseRadius            SOUND stimulus per swing: blades are quiet next to blasters
 * @param light                  light level of a lit blade for the Director's light sense without LambDynamicLights
 * @param pylonDamageMultiplier  damage multiplier against {@code #ashfall:crystal_pylons} entities
 * @param pylonMiningSpeed       mining speed against {@code #ashfall:crystal_pylons} blocks while lit
 */
public record BladeStats(float damage, float unpoweredDamage, int energyPerSwing, int crystalIntegrity, int wearPerHit,
						 int maxTargets, float extraTargetMultiplier, float extraTargetExhaustion, float noiseRadius,
						 int light, float pylonDamageMultiplier, float pylonMiningSpeed) {
	/** The shipped datapack values; used before the datapack loads (and by JUnit). */
	public static final BladeStats DEFAULT = new BladeStats(9f, 2f, 1, 600, 1, 3, 0.6f, 1.0f, 6f, 12, 2.0f, 12f);

	private static final Codec<Float> NON_NEGATIVE = Codec.floatRange(0f, Float.MAX_VALUE);

	public static final Codec<BladeStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			NON_NEGATIVE.fieldOf("damage").forGetter(BladeStats::damage),
			NON_NEGATIVE.fieldOf("unpowered_damage").forGetter(BladeStats::unpoweredDamage),
			Codec.intRange(0, 1_000).fieldOf("energy_per_swing").forGetter(BladeStats::energyPerSwing),
			Codec.intRange(1, 1_000_000).fieldOf("crystal_integrity").forGetter(BladeStats::crystalIntegrity),
			Codec.intRange(0, 1_000).fieldOf("wear_per_hit").forGetter(BladeStats::wearPerHit),
			Codec.intRange(1, 64).fieldOf("max_targets").forGetter(BladeStats::maxTargets),
			Codec.floatRange(0f, 1f).fieldOf("extra_target_multiplier").forGetter(BladeStats::extraTargetMultiplier),
			NON_NEGATIVE.fieldOf("extra_target_exhaustion").forGetter(BladeStats::extraTargetExhaustion),
			NON_NEGATIVE.fieldOf("noise_radius").forGetter(BladeStats::noiseRadius),
			Codec.intRange(0, 15).fieldOf("light").forGetter(BladeStats::light),
			NON_NEGATIVE.optionalFieldOf("pylon_damage_multiplier", 2.0f).forGetter(BladeStats::pylonDamageMultiplier),
			NON_NEGATIVE.optionalFieldOf("pylon_mining_speed", 12f).forGetter(BladeStats::pylonMiningSpeed)
	).apply(i, BladeStats::new));
}
