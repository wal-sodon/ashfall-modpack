package xyz.toesinthesand.ashfall.director.haven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * A server safe zone: no Director spawns, no ZB&B building or breaking, and a respawn point open to everyone.
 * Defined in {@code data/<ns>/ashfall/safe_zone/<id>.json} or added in game with {@code /ashfall safezone add}.
 * <p>
 * JSON fields: {@code name} (shown in chat), {@code dimension} (default overworld), {@code area}
 * ({@code chunk_x}, {@code chunk_z}, {@code radius} in chunks), optional {@code respawn} block position, and
 * {@code use_world_spawn}: respawn at the world spawn when it lies inside the area. That last one exists for
 * the Montford safehouse, whose exact floor height depends on Lost Cities' terrain: on an Ashfall world the
 * world spawn is already the safehouse's front hall ({@code CitySpawn}), so pointing at it is exact on every
 * seed. Without either, players respawn on the surface at the area's centre.
 *
 * @param landmark the Lost Cities landmark the zone wraps, informational (lets {@code list} say what it is)
 */
public record SafeZone(String name, ResourceKey<Level> dimension, ChunkArea area, Optional<BlockPos> respawn,
					   boolean useWorldSpawn, Optional<String> landmark) {
	public static final Codec<SafeZone> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(SafeZone::name),
			Level.RESOURCE_KEY_CODEC.optionalFieldOf("dimension", Level.OVERWORLD).forGetter(SafeZone::dimension),
			ChunkArea.CODEC.fieldOf("area").forGetter(SafeZone::area),
			BlockPos.CODEC.optionalFieldOf("respawn").forGetter(SafeZone::respawn),
			Codec.BOOL.optionalFieldOf("use_world_spawn", false).forGetter(SafeZone::useWorldSpawn),
			Codec.STRING.optionalFieldOf("landmark").forGetter(SafeZone::landmark)
	).apply(i, SafeZone::new));

	public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
		return dimension.equals(dim) && area.contains(pos.getX(), pos.getZ());
	}
}
