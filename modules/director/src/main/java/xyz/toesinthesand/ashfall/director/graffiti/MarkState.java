package xyz.toesinthesand.ashfall.director.graffiti;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How far a Cleared mark can be trusted, drawn as an overlay on the wall and as the symbol's colour on the Grid Map.
 * Other symbols stay {@link #FRESH}: only the Director knows whether a building is really clear.
 */
public enum MarkState implements StringRepresentable {
	/** True as far as the Director knows. */
	FRESH,
	/** Sprayed while (or since) the building had a population: "you hear movement inside". */
	WARN,
	/** The building refilled after it was marked; the mark is old news. */
	STALE;

	private static final MarkState[] VALUES = values();
	public static final StreamCodec<ByteBuf, MarkState> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
			id -> VALUES[Math.floorMod(id, VALUES.length)], Enum::ordinal);

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
