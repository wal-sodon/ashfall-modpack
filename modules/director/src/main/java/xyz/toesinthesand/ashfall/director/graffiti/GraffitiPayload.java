package xyz.toesinthesand.ashfall.director.graffiti;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * S2C {@code ashfall:graffiti}: the Graffiti marks this player may see on the Grid Map (their own and their party's,
 * each owner's newest {@link Graffiti#MAP_MARKS}). Sent when the list changes. Director owns the marks and the party
 * lookup; arms' Grid Map only draws them, which keeps the dependency direction (arms -> director).
 */
public record GraffitiPayload(List<Mark> marks) implements CustomPacketPayload {
	public static final Type<GraffitiPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ashfall", "graffiti"));

	public record Mark(int x, int z, GraffitiSymbol symbol, MarkState state) {
		public static final StreamCodec<ByteBuf, Mark> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Mark::x,
				ByteBufCodecs.VAR_INT, Mark::z,
				GraffitiSymbol.STREAM_CODEC, Mark::symbol,
				MarkState.STREAM_CODEC, Mark::state,
				Mark::new);
	}

	public static final StreamCodec<ByteBuf, GraffitiPayload> CODEC = StreamCodec.composite(
			Mark.CODEC.apply(ByteBufCodecs.list()), GraffitiPayload::marks,
			GraffitiPayload::new);

	@Override
	public Type<GraffitiPayload> type() {
		return TYPE;
	}
}
