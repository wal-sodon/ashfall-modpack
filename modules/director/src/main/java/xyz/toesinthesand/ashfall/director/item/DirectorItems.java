package xyz.toesinthesand.ashfall.director.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiSymbol;
import xyz.toesinthesand.ashfall.director.graffiti.SprayCanItem;

import java.util.List;
import java.util.function.Function;

/**
 * Medical supplies: the survival side of infection and wounds (see {@code infection.Infection}). They drop from pharmacies,
 * clinics and hospitals, so they are what pulls players into those buildings.
 * <p>
 * The creative tab {@code ashfall:main} is registered by arms; director only refers to it by key so the
 * dependency direction (arms -> director -> salvage) stays intact. Fabric's per-key event fires for
 * that tab whenever it is built, regardless of which mod registered it.
 */
public final class DirectorItems {
	/** The shared Ashfall creative tab (registered by ashfall_arms). */
	public static final ResourceKey<CreativeModeTab> ASHFALL_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));

	/**
	 * Used like food: hold use and it eats for as long as a meal, so releasing early is as obvious as with
	 * bread (the old bow draw looked finished and was let go). What it does is in {@code Infection.treatWithBandage}.
	 */
	public static final Item BANDAGE = register("bandage", p -> new MedicalItem(p, "tooltip.ashfall.bandage"),
			new Item.Properties().stacksTo(16).component(DataComponents.CONSUMABLE, Consumable.builder()
					.consumeSeconds(1.6f).animation(ItemUseAnimation.EAT).sound(SoundEvents.ARMOR_EQUIP_LEATHER)
					.build()));
	/** Pills: swallowed quickly like food. What they do is in {@code Infection.treatWithAntibiotics}. */
	public static final Item ANTIBIOTICS = register("antibiotics", p -> new MedicalItem(p, "tooltip.ashfall.antibiotics"),
			new Item.Properties().stacksTo(8).component(DataComponents.CONSUMABLE, Consumable.builder()
					.consumeSeconds(1.0f).animation(ItemUseAnimation.EAT).sound(SoundEvents.GENERIC_EAT)
					.hasConsumeParticles(false).build()));

	// Armour sets (see DirectorArmor): Scavenger is crafted from salvage, Hazmat and Riot are found.
	public static final Item SCAVENGER_HOOD = armor("scavenger_hood", DirectorArmor.SCAVENGER, ArmorType.HELMET);
	public static final Item SCAVENGER_JACKET = armor("scavenger_jacket", DirectorArmor.SCAVENGER, ArmorType.CHESTPLATE);
	public static final Item SCAVENGER_TROUSERS = armor("scavenger_trousers", DirectorArmor.SCAVENGER, ArmorType.LEGGINGS);
	public static final Item SCAVENGER_BOOTS = armor("scavenger_boots", DirectorArmor.SCAVENGER, ArmorType.BOOTS);
	public static final Item HAZMAT_MASK = armor("hazmat_mask", DirectorArmor.HAZMAT, ArmorType.HELMET);
	public static final Item HAZMAT_SUIT = armor("hazmat_suit", DirectorArmor.HAZMAT, ArmorType.CHESTPLATE);
	public static final Item HAZMAT_LEGGINGS = armor("hazmat_leggings", DirectorArmor.HAZMAT, ArmorType.LEGGINGS);
	public static final Item HAZMAT_BOOTS = armor("hazmat_boots", DirectorArmor.HAZMAT, ArmorType.BOOTS);
	public static final Item RIOT_HELMET = armor("riot_helmet", DirectorArmor.RIOT, ArmorType.HELMET);
	public static final Item RIOT_VEST = armor("riot_vest", DirectorArmor.RIOT, ArmorType.CHESTPLATE);
	public static final Item RIOT_LEGGINGS = armor("riot_leggings", DirectorArmor.RIOT, ArmorType.LEGGINGS);
	public static final Item RIOT_BOOTS = armor("riot_boots", DirectorArmor.RIOT, ArmorType.BOOTS);

	/** Paints Graffiti marks on walls; sneak + use picks the symbol (see graffiti.SprayCanItem). */
	public static final Item SPRAY_CAN = register("spray_can", SprayCanItem::new, new Item.Properties()
			.durability(SprayCanItem.USES).component(SprayCanItem.SYMBOL, GraffitiSymbol.CLEARED));

	/** Everything director registers, in creative-tab order. */
	public static final List<Item> ALL = List.of(BANDAGE, ANTIBIOTICS,
			SCAVENGER_HOOD, SCAVENGER_JACKET, SCAVENGER_TROUSERS, SCAVENGER_BOOTS,
			HAZMAT_MASK, HAZMAT_SUIT, HAZMAT_LEGGINGS, HAZMAT_BOOTS,
			RIOT_HELMET, RIOT_VEST, RIOT_LEGGINGS, RIOT_BOOTS,
			SPRAY_CAN);

	private DirectorItems() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item armor(String path, ArmorMaterial material, ArmorType type) {
		return register(path, ArmorPerkItem::new, new Item.Properties().humanoidArmor(material, type));
	}

	/** Forces class loading (and so registration) and adds the items to the Ashfall tab. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(ASHFALL_TAB).register(output -> ALL.forEach(output::accept));
	}
}
