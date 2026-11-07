package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.Optional;

/**
 * An admin-drawn loot area. Arnis places fixed vanilla items in its chests, and building profiles
 * only know what the sidecar knows; a zone lets an admin hand-curate a place (a hospital wing, a
 * hand-built bunker) with its own table and refill rules, overriding the building profile.
 * <p>
 * Membership is a vertical cylinder around {@code center}: Arnis buildings are tall and an admin
 * standing in the lobby still means the whole tower.
 *
 * @param id            admin-chosen name, also the {@code "z:<id>"} presence key
 * @param containerType containers the zone claims: "any" chest or barrel, one block, or a block tag
 * @param mode          "shared" (one inventory for everyone); kept so per-player caches can be added
 */
public record LootZone(String id, ResourceKey<Level> dimension, BlockPos center, int radius,
					   ContainerFilter containerType, Identifier table, BuildingProfile.Refill refill,
					   String mode) {
	public static final String ANY = "any";
	public static final String SHARED = "shared";

	public static final Codec<LootZone> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(LootZone::id),
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(LootZone::dimension),
			BlockPos.CODEC.fieldOf("center").forGetter(LootZone::center),
			Codec.intRange(0, 4096).fieldOf("radius").forGetter(LootZone::radius),
			ContainerFilter.CODEC.optionalFieldOf("container_type", ContainerFilter.ANY).forGetter(LootZone::containerType),
			Identifier.CODEC.fieldOf("table").forGetter(LootZone::table),
			BuildingProfile.Refill.CODEC.optionalFieldOf("refill", BuildingProfile.Refill.DEFAULT).forGetter(LootZone::refill),
			Codec.STRING.optionalFieldOf("mode", SHARED).forGetter(LootZone::mode)
	).apply(i, LootZone::new));

	/** The pre-tag constructor: an optional block id, empty = "any" (kept for existing callers and tests). */
	public LootZone(String id, ResourceKey<Level> dimension, BlockPos center, int radius, Optional<Identifier> containerType,
					Identifier table, BuildingProfile.Refill refill, String mode) {
		this(id, dimension, center, radius, ContainerFilter.of(containerType), table, refill, mode);
	}

	/** Presence-tracking key of this zone. */
	public String areaKey() {
		return "z:" + id;
	}

	public String containerTypeName() {
		return containerType.spec();
	}

	/** True if the column of {@code pos} lies within the zone's cylinder (horizontal distance). */
	public boolean contains(BlockPos pos) {
		return horizontalDistSq(pos) <= (long) radius * radius;
	}

	public long horizontalDistSq(BlockPos pos) {
		long dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
		return dx * dx + dz * dz;
	}

	public LootZone withTable(Identifier value) {
		return new LootZone(id, dimension, center, radius, containerType, value, refill, mode);
	}

	public LootZone withRadius(int value) {
		return new LootZone(id, dimension, center, value, containerType, table, refill, mode);
	}

	public LootZone withContainerType(Optional<Identifier> value) {
		return withContainerType(ContainerFilter.of(value));
	}

	public LootZone withContainerType(ContainerFilter value) {
		return new LootZone(id, dimension, center, radius, value, table, refill, mode);
	}

	public LootZone withCenter(ResourceKey<Level> newDimension, BlockPos value) {
		return new LootZone(id, newDimension, value, radius, containerType, table, refill, mode);
	}

	public LootZone withRefill(BuildingProfile.Refill value) {
		return new LootZone(id, dimension, center, radius, containerType, table, value, mode);
	}
}
