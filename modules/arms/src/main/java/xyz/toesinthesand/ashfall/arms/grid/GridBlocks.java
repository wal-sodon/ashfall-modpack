package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The grid's blocks and the Grid Coupler: the Substation Core (one per district, a world fixture like the
 * Power Relay), the grid lamp the relight puts back, and the coupler that repairs cores and links relays.
 */
public final class GridBlocks {
	/**
	 * Unbreakable and immovable in survival: the four cores are the district loop, and a core carried home would
	 * strand a district. Light follows the loop ({@link SubstationCoreBlock#POWER}).
	 */
	public static final SubstationCoreBlock SUBSTATION_CORE = registerBlock("substation_core", SubstationCoreBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.HEAVY_CORE).strength(-1.0f, 3_600_000.0f)
					.noLootTable().pushReaction(PushReaction.IMMOVEABLE)
					.lightLevel(state -> SubstationCoreBlock.light(state.getValue(SubstationCoreBlock.POWER))));
	/**
	 * A street or room light the grid powers again. Its own block rather than a lit redstone lamp, because a lit
	 * lamp with no redstone signal switches itself off at the first neighbour update. Drops a plain redstone
	 * lamp, so taking one home gives a lamp, not a free light.
	 */
	public static final Block GRID_LAMP = registerBlock("grid_lamp", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.GLASS).strength(0.3f).lightLevel(state -> 15));

	public static final Item SUBSTATION_CORE_ITEM = registerBlockItem(SUBSTATION_CORE, new Item.Properties().rarity(Rarity.EPIC));
	/** Scrap Electronics, a Battery, copper and Weapon Parts (recipe {@code ashfall:grid_coupler}); never a crystal. */
	public static final Item GRID_COUPLER = registerItem("grid_coupler", GridCouplerItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	public static final BlockEntityType<SubstationCoreBlockEntity> SUBSTATION_CORE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, id("substation_core"),
			new BlockEntityType<>(SubstationCoreBlockEntity::new, Set.of(SUBSTATION_CORE)));

	/** Creative-tab order. */
	public static final List<Item> ITEMS = List.of(GRID_COUPLER, SUBSTATION_CORE_ITEM);

	private GridBlocks() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static <B extends Block> B registerBlock(String path, Function<BlockBehaviour.Properties, B> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerBlockItem(Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix()));
	}

	private static Item registerItem(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading (and so registration) and adds the items to the Ashfall tab. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> ITEMS.forEach(output::accept));
	}
}
