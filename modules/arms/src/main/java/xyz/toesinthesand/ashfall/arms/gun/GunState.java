package xyz.toesinthesand.ashfall.arms.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The {@code ashfall:gun_state} component: heat as of the last shot, that shot's game tick, the
 * overheat lockout flag, and the fractional energy credit (see {@link EnergyCost}).
 * <p>
 * Heat is resolved lazily from game time through {@link HeatModel}, so this component only changes when
 * the gun fires. It is network-synchronised so the client HUD can compute the same heat locally.
 */
public record GunState(float heat, long lastShotTick, boolean locked, float energyCredit) {
	public static final GunState EMPTY = new GunState(0f, 0L, false, 0f);

	public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("heat", 0f).forGetter(GunState::heat),
			Codec.LONG.optionalFieldOf("last_shot_tick", 0L).forGetter(GunState::lastShotTick),
			Codec.BOOL.optionalFieldOf("locked", false).forGetter(GunState::locked),
			Codec.floatRange(0f, 1f).optionalFieldOf("energy_credit", 0f).forGetter(GunState::energyCredit)
	).apply(i, GunState::new));

	public static final StreamCodec<ByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, GunState::heat,
			ByteBufCodecs.VAR_LONG, GunState::lastShotTick,
			ByteBufCodecs.BOOL, GunState::locked,
			ByteBufCodecs.FLOAT, GunState::energyCredit,
			GunState::new);

	public float heatAt(long now, float coolPerSecond) {
		return HeatModel.heatAt(heat, lastShotTick, lockedAt(now, coolPerSecond), now, coolPerSecond);
	}

	public boolean lockedAt(long now, float coolPerSecond) {
		return HeatModel.lockedAt(heat, lastShotTick, locked, now, coolPerSecond);
	}
}
