package xyz.toesinthesand.ashfall.director.hydration;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.Set;

/** The Rain Collector: block, block entity and item. */
public final class RainCollectors {
	private static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, id("rain_collector"));
	public static final Block RAIN_COLLECTOR = Registry.register(BuiltInRegistries.BLOCK, KEY,
			new RainCollectorBlock(BlockBehaviour.Properties.of().setId(KEY).mapColor(MapColor.METAL).sound(SoundType.METAL)
					.strength(1.5f, 3.0f).noOcclusion()));
	public static final BlockEntityType<RainCollectorBlockEntity> ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("rain_collector"), new BlockEntityType<>(RainCollectorBlockEntity::new, Set.of(RAIN_COLLECTOR)));
	public static final Item ITEM = registerItem();

	private RainCollectors() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static Item registerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, KEY.identifier());
		return Registry.register(BuiltInRegistries.ITEM, key, new RainCollectorItem(RAIN_COLLECTOR,
				new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	static void init() {
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> output.accept(ITEM));
	}
}
