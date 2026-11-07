package xyz.toesinthesand.ashfall.arms.blade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The {@code ashfall:blade_crystal} component: how much of the socketed Resonance Crystal is left.
 * <p>
 * The crystal is the blade's durability. It wears per hit and, unlike vanilla durability, never
 * destroys the item: at 0 the blade is "dull" (a blunt hilt) until a fresh crystal is socketed. The
 * maximum travels with the stack so the client can show {@code 412/600} without the server's
 * datapack, and so a later rebalance only changes newly socketed crystals.
 *
 * @param integrity hits left before the crystal goes dull
 * @param max       integrity of a fresh crystal when this one was socketed
 */
public record BladeCrystal(int integrity, int max) {
	/** A fresh crystal at the default integrity (the datapack value is applied when socketing). */
	public static final BladeCrystal FRESH = new BladeCrystal(BladeStats.DEFAULT.crystalIntegrity(), BladeStats.DEFAULT.crystalIntegrity());

	public static final Codec<BladeCrystal> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, Integer.MAX_VALUE).fieldOf("integrity").forGetter(BladeCrystal::integrity),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max").forGetter(BladeCrystal::max)
	).apply(i, BladeCrystal::new));

	public static final StreamCodec<ByteBuf, BladeCrystal> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BladeCrystal::integrity,
			ByteBufCodecs.VAR_INT, BladeCrystal::max,
			BladeCrystal::new);

	public BladeCrystal {
		max = Math.max(1, max);
		integrity = Math.clamp(integrity, 0, max);
	}

	public static BladeCrystal fresh(int max) {
		return new BladeCrystal(max, max);
	}

	public boolean dull() {
		return integrity <= 0;
	}

	public boolean worn() {
		return integrity < max;
	}

	/** This crystal after {@code wear} more hits (never below 0). */
	public BladeCrystal wear(int wear) {
		return new BladeCrystal(BladeLogic.wearAfter(integrity, wear), max);
	}
}
