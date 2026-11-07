package xyz.toesinthesand.ashfall.arms.gun;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/**
 * What a blaster's bolts do, saved on the gun as {@link ArmsComponents#FIRE_MODE}. Only guns whose stats set
 * {@code stun_energy_multiplier} can be switched to {@link #STUN}; see {@link GunLogic#toggleMode}.
 */
public enum FireMode implements StringRepresentable {
	/** Ordinary bolts: damage, sound and noise. */
	DAMAGE("damage"),
	/** Silent bolts that stun instead of hurting, at a higher energy and heat cost. */
	STUN("stun");

	public static final Codec<FireMode> CODEC = StringRepresentable.fromEnum(FireMode::values);
	private static final IntFunction<FireMode> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
	public static final StreamCodec<ByteBuf, FireMode> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

	private final String id;

	FireMode(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public FireMode next() {
		return this == DAMAGE ? STUN : DAMAGE;
	}
}
