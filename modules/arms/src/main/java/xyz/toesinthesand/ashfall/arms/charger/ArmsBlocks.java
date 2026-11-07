package xyz.toesinthesand.ashfall.arms.charger;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import team.reborn.energy.api.EnergyStorage;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.List;
import java.util.Set;

/**
 * Arms' blocks: the chargers, their block items, the shared block entity type and one menu type per
 * kind. Block items join the {@code ashfall:main} tab through the tab event, so this class never edits
 * the tab's own item list in {@code ArmsItems}.
 */
public final class ArmsBlocks {
	/** Craftable; breaks like a furnace and drops itself (loot table {@code blocks/field_charger}). */
	public static final ChargerBlock FIELD_CHARGER = registerBlock(ChargerKind.FIELD_CHARGER, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.5f, 6.0f).requiresCorrectToolForDrops()
			.lightLevel(state -> state.getValue(ChargerBlock.ACTIVE) ? 7 : 0));
	/** Craftable upgrade of the Field Charger; same block behaviour, faster charging. */
	public static final ChargerBlock RAPID_CHARGER = registerBlock(ChargerKind.RAPID_CHARGER, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.5f, 6.0f).requiresCorrectToolForDrops()
			.lightLevel(state -> state.getValue(ChargerBlock.ACTIVE) ? 9 : 0));
	/**
	 * A world fixture, not craftable: unbreakable and immovable in survival so the atlas's relays can't be
	 * carried home. Admins place and remove them in creative or with commands.
	 */
	public static final ChargerBlock POWER_RELAY = registerBlock(ChargerKind.POWER_RELAY, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(-1.0f, 3_600_000.0f).noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
			.lightLevel(state -> state.getValue(ChargerBlock.ACTIVE) ? 10 : 3));

	public static final Item FIELD_CHARGER_ITEM = registerItem(FIELD_CHARGER, new Item.Properties());
	public static final Item RAPID_CHARGER_ITEM = registerItem(RAPID_CHARGER, new Item.Properties());
	public static final Item POWER_RELAY_ITEM = registerItem(POWER_RELAY, new Item.Properties().rarity(Rarity.RARE));

	public static final BlockEntityType<ChargerBlockEntity> CHARGER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("charger"), new BlockEntityType<>(ChargerBlockEntity::new, Set.of(FIELD_CHARGER, RAPID_CHARGER, POWER_RELAY)));

	public static final MenuType<ChargerMenu> FIELD_CHARGER_MENU = registerMenu(ChargerKind.FIELD_CHARGER);
	public static final MenuType<ChargerMenu> RAPID_CHARGER_MENU = registerMenu(ChargerKind.RAPID_CHARGER);
	public static final MenuType<ChargerMenu> POWER_RELAY_MENU = registerMenu(ChargerKind.POWER_RELAY);

	/** Block items, in creative-tab order. */
	public static final List<Item> ITEMS = List.of(FIELD_CHARGER_ITEM, RAPID_CHARGER_ITEM, POWER_RELAY_ITEM);

	private ArmsBlocks() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static ChargerBlock registerBlock(ChargerKind kind, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(kind.id()));
		return Registry.register(BuiltInRegistries.BLOCK, key, new ChargerBlock(kind, properties.setId(key)));
	}

	private static Item registerItem(Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix()));
	}

	private static MenuType<ChargerMenu> registerMenu(ChargerKind kind) {
		return Registry.register(BuiltInRegistries.MENU, id(kind.id()),
				new MenuType<>((containerId, inventory) -> new ChargerMenu(containerId, inventory, kind), FeatureFlags.VANILLA_SET));
	}

	public static MenuType<ChargerMenu> menuType(ChargerKind kind) {
		return switch (kind) {
			case FIELD_CHARGER -> FIELD_CHARGER_MENU;
			case RAPID_CHARGER -> RAPID_CHARGER_MENU;
			case POWER_RELAY -> POWER_RELAY_MENU;
		};
	}

	/** Forces class loading (and so registration), exposes the buffers to other mods, fills the tab. */
	public static void init() {
		EnergyStorage.SIDED.registerForBlockEntity((charger, side) -> charger.externalStorage(), CHARGER_ENTITY);
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> ITEMS.forEach(output::accept));
	}
}
