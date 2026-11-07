package xyz.toesinthesand.ashfall.salvage.lock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Building Key opens: the building's id (the Strongbox placed in it stores the same id) and a readable name for
 * the tooltip. The name is fixed when the key is made, so a key still reads right after the building index is gone.
 *
 * @param building {@code Building#id()} of the building whose Strongbox this key opens
 * @param name     what the tooltip calls the building, e.g. "Bank at 120, -340"
 */
public record KeyData(long building, String name) {
	public static final Codec<KeyData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("building").forGetter(KeyData::building),
			Codec.STRING.optionalFieldOf("name", "").forGetter(KeyData::name)
	).apply(i, KeyData::new));
	public static final StreamCodec<ByteBuf, KeyData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, KeyData::building,
			ByteBufCodecs.STRING_UTF8, KeyData::name,
			KeyData::new);
}
