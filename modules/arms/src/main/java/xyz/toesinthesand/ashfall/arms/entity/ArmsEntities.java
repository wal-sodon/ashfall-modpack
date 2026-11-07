package xyz.toesinthesand.ashfall.arms.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types registered by arms. */
public final class ArmsEntities {
	/** Small and tracked every tick: bolts move up to 8 blocks a tick and must not visibly lag. */
	public static final EntityType<EnergyBolt> ENERGY_BOLT = register("energy_bolt",
			EntityType.Builder.<EnergyBolt>of(EnergyBolt::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.noLootTable());

	public static final EntityType<Nail> NAIL = register("nail",
			EntityType.Builder.<Nail>of(Nail::new, MobCategory.MISC)
					.sized(0.15f, 0.15f)
					.clientTrackingRange(4)
					.updateInterval(5)
					.noSave()
					.noLootTable());

	public static final EntityType<BottleLure> BOTTLE_LURE = register("bottle_lure",
			EntityType.Builder.<BottleLure>of(BottleLure::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(4)
					.updateInterval(10));

	public static final EntityType<ArcCharge> ARC_CHARGE = register("arc_charge",
			EntityType.Builder.<ArcCharge>of(ArcCharge::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(4)
					.updateInterval(10));

	private ArmsEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("ashfall", path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Forces class loading, and so registration. */
	public static void init() {
	}
}
