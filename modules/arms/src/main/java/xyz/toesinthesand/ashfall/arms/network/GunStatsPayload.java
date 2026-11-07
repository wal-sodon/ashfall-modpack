package xyz.toesinthesand.ashfall.arms.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.gun.GunStats;

import java.util.Map;

/**
 * S2C: every loaded gun's stats, sent on join and after each {@code /reload}. The client needs them to
 * draw lazy heat (cooling rate, capacity) on the HUD exactly as the server computes it.
 */
public record GunStatsPayload(Map<Identifier, GunStats> stats) implements CustomPacketPayload {
	public static final Type<GunStatsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "gun_stats"));
	public static final StreamCodec<ByteBuf, GunStatsPayload> CODEC =
			ByteBufCodecs.fromCodec(Codec.unboundedMap(Identifier.CODEC, GunStats.CODEC))
					.map(GunStatsPayload::new, GunStatsPayload::stats);

	public GunStatsPayload {
		stats = Map.copyOf(stats);
	}

	@Override
	public Type<GunStatsPayload> type() {
		return TYPE;
	}
}
