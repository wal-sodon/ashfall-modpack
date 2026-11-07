package xyz.toesinthesand.ashfall.salvage.lock;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Building keys and safes: the Strongbox the Director puts in better buildings, the Building Key one of the building's
 * zombies carries, and the key's data component. Salvage owns them because the block entity is a salvage loot
 * container; the Crowbar that forces a box open is an arms item and joins in through {@link #PRY_TOOLS}.
 */
public final class Locks {
	/** Which building a key opens. Persistent and synchronised (the tooltip reads it client-side). */
	public static final DataComponentType<KeyData> KEY_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			id("key_data"), DataComponentType.<KeyData>builder().persistent(KeyData.CODEC).networkSynchronized(KeyData.STREAM_CODEC).build());

	/**
	 * Items a locked Strongbox lets through to their own {@code useOn} instead of rattling at the player: the arms
	 * Crowbar. A tag, because salvage can't see arms.
	 */
	public static final TagKey<Item> PRY_TOOLS = TagKey.create(Registries.ITEM, id("pries_strongboxes"));

	/**
	 * A squat steel safe. Hard and blast-proof; while locked it can't be mined or blown open at all (see
	 * {@link StrongboxBlock}). Can't be pushed, so a piston can't shove a locked box out of its building.
	 */
	public static final StrongboxBlock STRONGBOX = register("strongbox", StrongboxBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE).strength(5.0f, 1200.0f)
			.requiresCorrectToolForDrops().sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE));

	public static final BlockEntityType<StrongboxBlockEntity> STRONGBOX_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("strongbox"), new BlockEntityType<>(StrongboxBlockEntity::new, Set.of(STRONGBOX)));

	public static final Item STRONGBOX_ITEM = registerBlockItem(STRONGBOX);
	public static final Item BUILDING_KEY = registerItem("building_key", BuildingKeyItem::new, new Item.Properties().stacksTo(1));

	/** Everything this registers, in creative-tab order. */
	public static final List<Item> ITEMS = List.of(STRONGBOX_ITEM, BUILDING_KEY);

	private Locks() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static <B extends Block> B register(String path, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerBlockItem(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static Item registerItem(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading (and so registration) and adds the Strongbox and a blank key to the Ashfall tab. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(SalvageItems.ASHFALL_TAB).register(output -> ITEMS.forEach(output::accept));
	}
}
