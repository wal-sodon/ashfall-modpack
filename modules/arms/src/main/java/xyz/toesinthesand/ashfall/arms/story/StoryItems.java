package xyz.toesinthesand.ashfall.arms.story;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

/** The story's items. Only the Field Radio so far; it joins the Ashfall tab through the tab event. */
public final class StoryItems {
	public static final ResourceKey<Item> FIELD_RADIO_KEY = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath("ashfall", "field_radio"));
	public static final Item FIELD_RADIO = Registry.register(BuiltInRegistries.ITEM, FIELD_RADIO_KEY,
			new FieldRadioItem(new Item.Properties().setId(FIELD_RADIO_KEY).stacksTo(1).rarity(Rarity.UNCOMMON)));

	private StoryItems() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> output.accept(FIELD_RADIO));
	}
}
