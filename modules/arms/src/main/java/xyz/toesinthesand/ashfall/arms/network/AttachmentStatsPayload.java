package xyz.toesinthesand.ashfall.arms.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentStats;

import java.util.Map;

/**
 * S2C: every loaded attachment's stats, sent with {@link GunStatsPayload} on join and after {@code /reload}.
 * The client needs them for the HUD heat bar (Heat Sink), the Optic's zoom and the attachment tooltips.
 */
public record AttachmentStatsPayload(Map<String, AttachmentStats> stats) implements CustomPacketPayload {
	public static final Type<AttachmentStatsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "attachment_stats"));
	public static final StreamCodec<ByteBuf, AttachmentStatsPayload> CODEC =
			ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, AttachmentStats.CODEC))
					.map(AttachmentStatsPayload::new, AttachmentStatsPayload::stats);

	public AttachmentStatsPayload {
		stats = Map.copyOf(stats);
	}

	@Override
	public Type<AttachmentStatsPayload> type() {
		return TYPE;
	}
}
