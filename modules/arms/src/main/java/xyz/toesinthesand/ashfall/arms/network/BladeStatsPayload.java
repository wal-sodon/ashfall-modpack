package xyz.toesinthesand.ashfall.arms.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.blade.BladeStats;

import java.util.Map;

/**
 * S2C: every loaded plasma blade's stats, sent on join and after each {@code /reload}. Why: the client predicts
 * mining with {@code PlasmaBladeItem#getDestroySpeed}, which reads {@code pylon_mining_speed}; without this a remote
 * co-op client used the built-in default (12) while the server used the file (16 for the Resonant blade), so a
 * LAN guest cut Heart pylons slower than the host.
 */
public record BladeStatsPayload(Map<Identifier, BladeStats> stats) implements CustomPacketPayload {
	public static final Type<BladeStatsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "blade_stats"));
	public static final StreamCodec<ByteBuf, BladeStatsPayload> CODEC =
			ByteBufCodecs.fromCodec(Codec.unboundedMap(Identifier.CODEC, BladeStats.CODEC))
					.map(BladeStatsPayload::new, BladeStatsPayload::stats);

	public BladeStatsPayload {
		stats = Map.copyOf(stats);
	}

	@Override
	public Type<BladeStatsPayload> type() {
		return TYPE;
	}
}
