package xyz.toesinthesand.ashfall.director.haven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * One placed Bunker Core, as saved in {@link HavenState}.
 *
 * @param ownerKey      the party id when the placer was in an OPAC party, else the placer's own UUID; the
 *                      "one core per party or player" rule is one record per key
 * @param placedBy      the player who placed it (OPAC claims are made in their name)
 * @param radius        claim radius in chunks at placement time, kept so a settings change later does not
 *                      shrink or grow an existing haven
 * @param claimedChunks packed chunks OPAC actually claimed for this core, released when it is broken
 */
public record BunkerCoreRecord(UUID ownerKey, UUID placedBy, ResourceKey<Level> dimension, BlockPos pos, int radius,
							   List<Long> claimedChunks) {
	public static final Codec<BunkerCoreRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner_key").forGetter(BunkerCoreRecord::ownerKey),
			UUIDUtil.CODEC.fieldOf("placed_by").forGetter(BunkerCoreRecord::placedBy),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(BunkerCoreRecord::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(BunkerCoreRecord::pos),
			Codec.INT.fieldOf("radius").forGetter(BunkerCoreRecord::radius),
			Codec.LONG.listOf().optionalFieldOf("claimed_chunks", List.of()).forGetter(BunkerCoreRecord::claimedChunks)
	).apply(i, BunkerCoreRecord::new));

	public ChunkArea area() {
		return ChunkArea.around(pos.getX(), pos.getZ(), radius);
	}
}
