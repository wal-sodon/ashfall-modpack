package xyz.toesinthesand.ashfall.director.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

/**
 * Ashfall's three armour sets. Protection lives in the materials; each set's perk is counted from the
 * {@code #ashfall:armor/<set>} item tags so a pack can add pieces to a set without code.
 * <ul>
 *   <li><b>Scavenger</b>: light, craftable from salvage; quieter footsteps ({@code GameEventNoise}).</li>
 *   <li><b>Hazmat</b>: hospital loot; lowers every infection roll ({@code Infection}).</li>
 *   <li><b>Riot</b>: police loot; the heaviest set, with toughness and knockback resistance from the material.</li>
 * </ul>
 */
public final class DirectorArmor {
	public static final ArmorMaterial SCAVENGER = material("scavenger", 9, 1, 3, 2, 1, 12, 0f, 0f);
	public static final ArmorMaterial HAZMAT = material("hazmat", 12, 1, 4, 3, 1, 10, 0f, 0f);
	public static final ArmorMaterial RIOT = material("riot", 20, 2, 7, 5, 2, 9, 1.0f, 0.1f);

	public static final TagKey<Item> SCAVENGER_SET = TagKey.create(Registries.ITEM, id("armor/scavenger"));
	public static final TagKey<Item> HAZMAT_SET = TagKey.create(Registries.ITEM, id("armor/hazmat"));
	public static final TagKey<Item> RIOT_SET = TagKey.create(Registries.ITEM, id("armor/riot"));

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private DirectorArmor() {
	}

	/** How many of {@code entity}'s four armour slots hold a piece of {@code set} (0-4). */
	public static int pieces(LivingEntity entity, TagKey<Item> set) {
		int n = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (entity.getItemBySlot(slot).is(set)) {
				n++;
			}
		}
		return n;
	}

	/** See {@link ArmorPerks#factor}. */
	public static float perkFactor(int pieces, float perPiece) {
		return ArmorPerks.factor(pieces, perPiece);
	}

	private static ArmorMaterial material(String name, int durability, int head, int chest, int legs, int feet,
										  int enchantability, float toughness, float knockbackResistance) {
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, id(name));
		TagKey<Item> repair = TagKey.create(Registries.ITEM, id("repairs_" + name + "_armor"));
		return new ArmorMaterial(durability,
				Map.of(ArmorType.HELMET, head, ArmorType.CHESTPLATE, chest, ArmorType.LEGGINGS, legs, ArmorType.BOOTS, feet, ArmorType.BODY, chest),
				enchantability, name.equals("riot") ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_LEATHER,
				toughness, knockbackResistance, repair, asset);
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}
}
