package xyz.toesinthesand.ashfall.director.hydration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;

/**
 * A player's thirst, shaped exactly like vanilla {@code FoodData} (level, saturation, exhaustion) so
 * players who know hunger already know thirst: saturation is the hidden buffer that drains first,
 * exhaustion is the activity meter that turns into lost points.
 * <p>
 * Immutable on purpose: the attachment API only syncs to the client when {@code setAttached} sees a
 * value that is not {@code equals} to the old one, so every change goes through a new record.
 * All the arithmetic lives here, free of entities and levels, so it can be unit tested.
 */
public record ThirstData(int thirst, float saturation, float exhaustion) {
	public static final int MAX_THIRST = 20;
	public static final float MAX_SATURATION = 20.0f;
	/** Same cap as vanilla's exhaustion, so a long sprint can not bank unlimited future drain. */
	public static final float MAX_EXHAUSTION = 40.0f;
	/** Exhaustion that costs one point of saturation (or thirst once saturation is gone), as in FoodData. */
	public static final float EXHAUSTION_PER_POINT = 4.0f;
	/** At or below this (3 droplets) natural regeneration stops. */
	public static final int NO_REGEN_AT_OR_BELOW = 6;

	/** A new player, and every player after death. */
	public static final ThirstData DEFAULT = new ThirstData(MAX_THIRST, 5.0f, 0.0f);

	/**
	 * Plain number codecs, not range codecs: an out-of-range value (hand-edited save, a future cap
	 * change) is clamped by the constructor instead of failing the decode, which would silently drop
	 * the whole attachment and refill the player.
	 */
	public static final Codec<ThirstData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("thirst").forGetter(ThirstData::thirst),
			Codec.FLOAT.fieldOf("saturation").forGetter(ThirstData::saturation),
			Codec.FLOAT.fieldOf("exhaustion").forGetter(ThirstData::exhaustion)
	).apply(i, ThirstData::new));

	public static final StreamCodec<ByteBuf, ThirstData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ThirstData::thirst,
			ByteBufCodecs.FLOAT, ThirstData::saturation,
			ByteBufCodecs.FLOAT, ThirstData::exhaustion,
			ThirstData::new);

	/** Clamps instead of throwing, so a command or a corrupt save can never push values out of range. */
	public ThirstData {
		thirst = Mth.clamp(thirst, 0, MAX_THIRST);
		saturation = Mth.clamp(saturation, 0.0f, MAX_SATURATION);
		exhaustion = Mth.clamp(exhaustion, 0.0f, MAX_EXHAUSTION);
	}

	public ThirstData addExhaustion(float amount) {
		return new ThirstData(thirst, saturation, exhaustion + amount);
	}

	/**
	 * One drain step, run once per tick: at 4 exhaustion, spend 4 and take a point from saturation if
	 * any is left, otherwise from thirst. Mirrors {@code FoodData.tick}.
	 */
	public ThirstData drain() {
		if (exhaustion < EXHAUSTION_PER_POINT) {
			return this;
		}
		float e = exhaustion - EXHAUSTION_PER_POINT;
		if (saturation > 0) {
			return new ThirstData(thirst, Math.max(saturation - 1.0f, 0.0f), e);
		}
		return new ThirstData(thirst - 1, saturation, e);
	}

	/**
	 * Drinking: thirst goes up, and saturation is capped at the new thirst level, like vanilla eating,
	 * so a sip at low thirst can not hide a big buffer behind an almost empty bar.
	 */
	public ThirstData drink(int amount, float addedSaturation) {
		int t = Mth.clamp(thirst + amount, 0, MAX_THIRST);
		return new ThirstData(t, Math.min(saturation + addedSaturation, t), exhaustion);
	}

	public ThirstData withThirst(int value) {
		return new ThirstData(value, Math.min(saturation, Mth.clamp(value, 0, MAX_THIRST)), exhaustion);
	}

	public boolean blocksRegeneration() {
		return thirst <= NO_REGEN_AT_OR_BELOW;
	}

	public boolean blocksSprinting() {
		return thirst <= 0;
	}

	/**
	 * Whether a dehydration hit may land, with the same floors as starvation: Easy stops at 10 HP,
	 * Normal at 1 HP (half a heart), Hard kills.
	 */
	public static boolean dehydrationHurts(float health, Difficulty difficulty) {
		return health > 10.0f || difficulty == Difficulty.HARD || (health > 1.0f && difficulty == Difficulty.NORMAL);
	}
}
