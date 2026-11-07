package xyz.toesinthesand.ashfall.director.graffiti;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a Graffiti mark says. The serialized name is the block state value, the texture suffix and the lang key
 * ({@code graffiti.ashfall.symbol.<name>}). The four "place" symbols also show on the Grid Map; arrows only make
 * sense on the wall they point along.
 */
public enum GraffitiSymbol implements StringRepresentable {
	CLEARED,
	DANGER,
	LOOT,
	SAFEHOUSE,
	ARROW_UP,
	ARROW_DOWN,
	ARROW_LEFT,
	ARROW_RIGHT;

	private static final GraffitiSymbol[] VALUES = values();
	public static final Codec<GraffitiSymbol> CODEC = StringRepresentable.fromEnum(GraffitiSymbol::values);
	public static final StreamCodec<ByteBuf, GraffitiSymbol> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(GraffitiSymbol::byId, Enum::ordinal);

	public static GraffitiSymbol byId(int id) {
		return VALUES[Math.floorMod(id, VALUES.length)];
	}

	/** The symbol the Spray Can switches to on sneak + use, wrapping round. */
	public GraffitiSymbol next() {
		return byId(ordinal() + 1);
	}

	/** Whether marks with this symbol are sent to the Grid Map. */
	public boolean onMap() {
		return this == CLEARED || this == DANGER || this == LOOT || this == SAFEHOUSE;
	}

	public String translationKey() {
		return "graffiti.ashfall.symbol." + getSerializedName();
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
