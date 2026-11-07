package xyz.toesinthesand.ashfall.director.graffiti;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * One sprayed mark as {@link GraffitiIndex} remembers it, so it can be updated and shown on the Grid Map while its
 * chunk is unloaded.
 *
 * @param pos        packed block position of the Graffiti block
 * @param building   the building it was sprayed on, if any
 * @param generation that building's population generation when sprayed: a higher one now means it refilled since
 * @param placed     game time it was sprayed, for "newest first"
 */
public record GraffitiMark(long pos, UUID owner, GraffitiSymbol symbol, Optional<Long> building, int generation, long placed) {
	public static final Codec<GraffitiMark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(GraffitiMark::pos),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(GraffitiMark::owner),
			GraffitiSymbol.CODEC.fieldOf("symbol").forGetter(GraffitiMark::symbol),
			Codec.LONG.optionalFieldOf("building").forGetter(GraffitiMark::building),
			Codec.INT.optionalFieldOf("generation", 0).forGetter(GraffitiMark::generation),
			Codec.LONG.optionalFieldOf("placed", 0L).forGetter(GraffitiMark::placed)
	).apply(i, GraffitiMark::new));
}
