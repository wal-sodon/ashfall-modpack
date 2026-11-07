package xyz.toesinthesand.ashfall.arms.item;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Weapon weight: a weapon in your main hand slows you down by its class. Only the hand counts (a hotbar full of guns
 * weighs nothing) and there is no armour weight. It is one {@code MOVEMENT_SPEED} modifier added to the item's
 * default attributes, so vanilla shows it in the tooltip and melee weapons keep their exact sword numbers. It stacks
 * with the slowdown while firing (see {@link ArmsItems#GUN_USE_SPEED}): the Storm Repeater firing walks at
 * 0.5 x 0.85 = about 0.43 of normal speed.
 */
public enum WeaponWeight {
	LIGHT(-0.03),
	MEDIUM(-0.07),
	HEAVY(-0.10),
	VERY_HEAVY(-0.15);

	public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("ashfall", "weapon_weight");

	/** Item to class, filled once at mod init (by {@link #init} and by other features' {@link #assign}). */
	private static final Map<Item, WeaponWeight> ASSIGNED = new LinkedHashMap<>();

	private final double speed;

	WeaponWeight(double speed) {
		this.speed = speed;
	}

	/** The movement multiplier added while held, e.g. -0.03 for -3%. */
	public double speed() {
		return speed;
	}

	/** {@code base} plus this class's main-hand movement modifier; everything already in {@code base} is kept. */
	public static ItemAttributeModifiers with(ItemAttributeModifiers base, WeaponWeight weight) {
		return base.withModifierAdded(Attributes.MOVEMENT_SPEED,
				new AttributeModifier(MODIFIER_ID, weight.speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				EquipmentSlotGroup.MAINHAND);
	}

	/** Gives {@code item} a weight class. Call during mod init, before item components are built. */
	public static void assign(Item item, WeaponWeight weight) {
		ASSIGNED.put(item, weight);
	}

	/** The weight class of {@code item}, or null for anything weightless. */
	public static WeaponWeight of(Item item) {
		return ASSIGNED.get(item);
	}

	public static Map<Item, WeaponWeight> assigned() {
		return Collections.unmodifiableMap(ASSIGNED);
	}

	static void init() {
		assign(ArmsItems.SPARK_PISTOL, LIGHT);
		assign(ArmsItems.VOLT_SIDEARM, LIGHT);
		assign(ArmsItems.ARC_MAGNUM, LIGHT);
		assign(ArmsItems.CROWBAR, LIGHT);
		assign(ArmsItems.SCRAP_MACHETE, LIGHT);
		assign(ArmsItems.PIPE_WRENCH, MEDIUM);
		assign(ArmsItems.NAIL_LAUNCHER, MEDIUM);
		assign(ArmsItems.PULSE_CARBINE, MEDIUM);
		assign(ArmsItems.ION_RIFLE, MEDIUM);
		assign(ArmsItems.PLASMA_BLADE, MEDIUM);
		assign(ArmsItems.RESONANT_PLASMA_BLADE, MEDIUM);
		assign(ArmsItems.REBAR_SPEAR, HEAVY);
		assign(ArmsItems.LANCER_LONGRIFLE, HEAVY);
		assign(ArmsItems.STORM_REPEATER, VERY_HEAVY);
		assign(ArmsItems.FLAMETHROWER, VERY_HEAVY);

		// Read the item's own default attributes and add to them, rather than rebuilding the list: the sword
		// numbers from Item.Properties#sword stay exactly as vanilla computed them.
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(ASSIGNED::containsKey, (builder, item) ->
				builder.set(DataComponents.ATTRIBUTE_MODIFIERS,
						with(builder.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY), ASSIGNED.get(item)))));
	}
}
