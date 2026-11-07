package xyz.toesinthesand.ashfall.salvage.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

import java.util.List;
import java.util.function.Function;

/**
 * Everyday survival salvage: the food, water and fire-making supplies that fill ordinary buildings, so
 * looting feels like an apocalypse instead of vanilla filler. Salvage is the lowest module, so the
 * higher ones (director's bandage recipe, arms' loot tables) can all use these items.
 * <p>
 * The creative tab {@code ashfall:main} is registered by arms; salvage only refers to it by key, like
 * director does, so the dependency direction (arms -> director -> salvage) stays intact.
 */
public final class SalvageItems {
	/** The shared Ashfall creative tab (registered by ashfall_arms). */
	public static final ResourceKey<CreativeModeTab> ASHFALL_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));

	/**
	 * Burn time of lighter fluid: a datapack-tunable provider in the {@code context_int_provider} registry
	 * ({@code data/ashfall/context_int_provider/cooking/time_lighter_fluid.json}, 2400 ticks = 1.5 coal).
	 * Furnaces read it through the {@code cooking_fuel} component, and so does Unlit Campfire when a player
	 * adds a burnable to a lit campfire.
	 */
	public static final ResourceKey<ContextIntProvider> LIGHTER_FLUID_BURN_TIME =
			ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, id("cooking/time_lighter_fluid"));

	/** Uses per box of matches; flint and steel has 64. */
	public static final int MATCHES_USES = 8;
	public static final int LIGHTER_FLUID_USES = 16;

	public static final Item CANNED_FOOD = register("canned_food", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).build()));
	public static final Item BOTTLED_WATER = register("bottled_water", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).alwaysEdible().build(),
					Consumables.DEFAULT_DRINK)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.stacksTo(16));
	/**
	 * River or pond water in a glass bottle (a bottle used on a water source gives this, not the vanilla
	 * water bottle). Drinkable but risky; director's hydration applies the thirst and the sickness
	 * rolls. Cooking it (furnace, smoker, campfire) purifies it into bottled water.
	 */
	public static final Item DIRTY_WATER = register("dirty_water", Item::new, new Item.Properties()
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.stacksTo(16));
	public static final Item CLOTH = register("cloth", Item::new, new Item.Properties());
	public static final Item DUCT_TAPE = register("duct_tape", Item::new, new Item.Properties().stacksTo(16));
	public static final Item SCRAP_METAL = register("scrap_metal", Item::new, new Item.Properties());
	/** What a window leaves without Silk Touch; four make a block of glass again. */
	public static final Item GLASS_SHARD = register("glass_shard", Item::new, new Item.Properties());
	/** A fire starter like flint and steel (campfires, candles, fires, burnt-out torches), with few uses. */
	public static final Item MATCHES = register("matches", FlintAndSteelItem::new,
			new Item.Properties().durability(MATCHES_USES));
	/**
	 * A squeeze can of fuel: a fire starter like {@link #MATCHES} with twice the uses (it relights burnt-out torches and
	 * campfires the same way), or furnace fuel. Why a fire starter: as fuel alone it was a filler item nobody kept.
	 */
	public static final Item LIGHTER_FLUID = register("lighter_fluid", FlintAndSteelItem::new, new Item.Properties()
			.durability(LIGHTER_FLUID_USES)
			.cookingFuel(LIGHTER_FLUID_BURN_TIME));
	/** A small sewn bag that carries a row of items; see {@link SatchelItem}. */
	public static final Item SATCHEL = register("satchel", SatchelItem::new, new Item.Properties().stacksTo(1));

	/** Everything salvage registers, in creative-tab order. */
	public static final List<Item> ALL = List.of(CANNED_FOOD, BOTTLED_WATER, DIRTY_WATER, CLOTH, DUCT_TAPE, SCRAP_METAL, GLASS_SHARD, MATCHES, LIGHTER_FLUID, SATCHEL);

	private SalvageItems() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading (and so registration, the satchel menu too) and adds the items to the Ashfall tab. */
	public static void init() {
		SatchelMenu.MENU.getClass();
		CreativeModeTabEvents.modifyOutputEvent(ASHFALL_TAB).register(output -> ALL.forEach(output::accept));
	}
}
