package xyz.toesinthesand.ashfall.salvage.bike;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/** The bicycle entity and its item. */
public final class Bikes {
	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, AshfallSalvage.id("bicycle"));
	public static final EntityType<Bicycle> BICYCLE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
			EntityType.Builder.<Bicycle>of(Bicycle::new, MobCategory.MISC)
					.sized(0.8f, 1.0f)
					.passengerAttachments(0.65f)
					.clientTrackingRange(10)
					.build(KEY));
	public static final Item ITEM = registerItem();

	private Bikes() {
	}

	private static Item registerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AshfallSalvage.id("bicycle"));
		return Registry.register(BuiltInRegistries.ITEM, key, new BicycleItem(new Item.Properties().setId(key).stacksTo(1)));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(BICYCLE, Bicycle.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(SalvageItems.ASHFALL_TAB).register(output -> output.accept(ITEM));
	}
}
