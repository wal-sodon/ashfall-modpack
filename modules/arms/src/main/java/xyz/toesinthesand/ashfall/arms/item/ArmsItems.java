package xyz.toesinthesand.ashfall.arms.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import xyz.toesinthesand.ashfall.arms.blade.BladeCrystal;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.AttachmentItem;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.arms.gun.BoltStyle;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem.FireSound;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerItem;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunClass;

import java.util.List;
import java.util.function.Function;

/**
 * The energy-weapon economy: raw materials, the seven energy guns, their Cell Packs, the blaster
 * attachments, the flamethrower and the Plasma Blade.
 * Energy Cells are the scarce currency (mostly from special zombies, see {@link MobDrops}); the rest of
 * the materials are salvage that weapons and chargers are crafted from.
 * <p>
 * Arms owns the {@code ashfall:main} creative tab because it is the top of the dependency chain:
 * lower modules add to the tab by key ({@link DirectorItems#ASHFALL_TAB}) without needing arms.
 */
public final class ArmsItems {
	public static final ResourceKey<CreativeModeTab> ASHFALL_TAB = DirectorItems.ASHFALL_TAB;

	/** A 240-unit energy item; see {@link EnergyCellItem} for why new cells are filled by their source. */
	public static final Item ENERGY_CELL = register("energy_cell", EnergyCellItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item SCRAP_ELECTRONICS = register("scrap_electronics", Item::new, new Item.Properties());
	public static final Item WEAPON_PARTS = register("weapon_parts", Item::new, new Item.Properties());
	public static final Item FOCUSING_LENS = register("focusing_lens", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item RESONANCE_CRYSTAL = register("resonance_crystal", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());
	/** Everyday salvage (homes, shops, electronics stores): the low-tech half of an Energy Cell recipe. */
	public static final Item BATTERY = register("battery", Item::new, new Item.Properties().stacksTo(16));
	/** Salvage stand-in for iron in energy gear: smelted from Scrap Metal, so no blaster needs mining. */
	public static final Item ALLOY_PLATE = register("alloy_plate", Item::new, new Item.Properties());
	/** Salvage stand-in for copper in energy gear: looted, or stripped from Scrap Electronics. */
	public static final Item WIRE_SPOOL = register("wire_spool", Item::new, new Item.Properties());

	// Quiet weapons (rc14): scrap-built, and melee makes no noise at all, so these are the stealth kit.
	/** Slow, heavy hits (10.2.2: 1.4 swings a second, so it is a real choice against the machete, best on armour). */
	public static final Item PIPE_WRENCH = register("pipe_wrench", Item::new, new Item.Properties()
			.sword(ToolMaterial.IRON, 4.0f, -2.6f).durability(300).repairable(xyz.toesinthesand.ashfall.salvage.item.SalvageItems.SCRAP_METAL));
	/** Fast swings, wears out quickly. */
	public static final Item SCRAP_MACHETE = register("scrap_machete", Item::new, new Item.Properties()
			.sword(ToolMaterial.IRON, 2.5f, -2.0f).durability(150).repairable(xyz.toesinthesand.ashfall.salvage.item.SalvageItems.SCRAP_METAL));
	/** Reach (Better Combat's spear moves): hit through a gap or over a barricade. */
	public static final Item REBAR_SPEAR = register("rebar_spear", Item::new, new Item.Properties()
			.sword(ToolMaterial.IRON, 3.0f, -2.8f).durability(220).repairable(xyz.toesinthesand.ashfall.salvage.item.SalvageItems.SCRAP_METAL));
	/** Ammunition for the Nail Launcher. */
	public static final Item NAILS = register("nails", Item::new, new Item.Properties());
	public static final Item NAIL_LAUNCHER = register("nail_launcher", NailLauncherItem::new, new Item.Properties().durability(400));
	/** A thrown noisemaker; see {@link xyz.toesinthesand.ashfall.arms.entity.BottleLure}. */
	public static final Item BOTTLE_LURE = register("bottle_lure", BottleLureItem::new, new Item.Properties().stacksTo(16));
	/** A thrown stun grenade; see {@link xyz.toesinthesand.ashfall.arms.entity.ArcCharge}. */
	public static final Item ARC_CHARGE = register("arc_charge", ArcChargeItem::new, new Item.Properties().stacksTo(8));
	/** A weak swing, and the noisy way into a locked Strongbox; see {@link CrowbarItem}. */
	public static final Item CROWBAR = register("crowbar", CrowbarItem::new, new Item.Properties()
			.sword(ToolMaterial.IRON, 1.5f, -2.6f).durability(250).repairable(net.minecraft.world.item.Items.IRON_INGOT));


	// Energy weapons. Class decides the pack; mode and sound are identity; numbers are datapack stats.
	public static final Item SPARK_PISTOL = gun("spark_pistol", GunClass.PISTOL, false, FireSound.PISTOL, BoltStyle.PISTOL, Rarity.COMMON);
	public static final Item VOLT_SIDEARM = gun("volt_sidearm", GunClass.PISTOL, false, FireSound.PISTOL, BoltStyle.PISTOL, Rarity.COMMON);
	public static final Item PULSE_CARBINE = gun("pulse_carbine", GunClass.RIFLE, true, FireSound.CARBINE, BoltStyle.CARBINE, Rarity.UNCOMMON);
	public static final Item ION_RIFLE = gun("ion_rifle", GunClass.RIFLE, false, FireSound.RIFLE, BoltStyle.RIFLE, Rarity.RARE);
	public static final Item ARC_MAGNUM = gun("arc_magnum", GunClass.PISTOL, false, FireSound.MAGNUM, BoltStyle.MAGNUM, Rarity.RARE);
	public static final Item LANCER_LONGRIFLE = gun("lancer_longrifle", GunClass.RIFLE, false, FireSound.LANCER, BoltStyle.LANCER, Rarity.EPIC);
	public static final Item STORM_REPEATER = gun("storm_repeater", GunClass.HEAVY, true, FireSound.STORM, BoltStyle.STORM, Rarity.EPIC);

	/**
	 * Holds right-click for a short cone of fire, fed from a Lighter Fluid tank; see {@link FlamethrowerItem}. The
	 * default tank is empty: the recipe and loot tables set the fuel.
	 */
	public static final Item FLAMETHROWER = register("flamethrower", FlamethrowerItem::new, new Item.Properties()
			.stacksTo(1).rarity(Rarity.RARE)
			.component(ArmsComponents.FUEL, 0)
			.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, ArmsItems.HEAVY_GUN_USE_SPEED)));

	// Ammunition: rechargeable Team Reborn Energy packs, one size per weapon class. Crafted empty.
	public static final Item CELL_PACK_SMALL = register("cell_pack_small", p -> new CellPackItem(p, 30), new Item.Properties().stacksTo(1));
	public static final Item CELL_PACK_STANDARD = register("cell_pack_standard", p -> new CellPackItem(p, 60), new Item.Properties().stacksTo(1));
	public static final Item CELL_PACK_HEAVY = register("cell_pack_heavy", p -> new CellPackItem(p, 120), new Item.Properties().stacksTo(1));

	// Blaster attachments, fitted at the Weaponsmith Bench (the Dampener also by its old crafting recipe).
	// What each does is datapack data: data/ashfall/ashfall/gun_attachment/<id>.json.
	public static final Item OPTIC = register("optic", AttachmentItem::new, new Item.Properties().stacksTo(16));
	/** Noise x0.4, damage x0.9; also fitted with the {@code ashfall:attach_dampener} crafting recipe. */
	public static final Item DAMPENER = register("dampener", AttachmentItem::new, new Item.Properties().stacksTo(16));
	public static final Item HEAT_SINK = register("heat_sink", AttachmentItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item CELL_REGULATOR = register("cell_regulator", AttachmentItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item OVERCHARGER = register("overcharger", AttachmentItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	/**
	 * The Plasma Blade. The attribute shows the plan's 9 damage at sword speed; the real numbers (hilt
	 * damage, sweep, wear) are datapack stats applied per hit. The Weapon component is what makes vanilla
	 * call {@link PlasmaBladeItem#hurtEnemy} (it costs no durability: the crystal is the durability).
	 */
	public static final Item PLASMA_BLADE = register("plasma_blade", PlasmaBladeItem::new, new Item.Properties()
			.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
			.component(ArmsComponents.BLADE_CRYSTAL, BladeCrystal.FRESH)
			.component(DataComponents.WEAPON, new Weapon(0))
			.attributes(ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
							PlasmaBladeItem.ATTRIBUTE_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
							PlasmaBladeItem.ATTRIBUTE_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build()));

	/**
	 * The Resonant Plasma Blade: the Bind ending's reward, never crafted. Same hilt and rules as the Plasma Blade, but
	 * its crystal resonates with the bound Heart: a socketed crystal holds {@link #RESONANT_CRYSTAL_INTEGRITY} hits
	 * (four times the plain blade's, so it wears at a quarter of the rate) and its datapack stats hit harder. The
	 * attribute stays the plain blade's 9 because strikes are rescaled from it ({@link PlasmaBladeItem#ATTRIBUTE_DAMAGE}).
	 */
	public static final Item RESONANT_PLASMA_BLADE = register("resonant_plasma_blade", PlasmaBladeItem::new, new Item.Properties()
			.stacksTo(1).rarity(Rarity.EPIC).fireResistant()
			.component(ArmsComponents.BLADE_CRYSTAL, BladeCrystal.fresh(PlasmaBladeItem.RESONANT_CRYSTAL_INTEGRITY))
			.component(DataComponents.WEAPON, new Weapon(0))
			.attributes(ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
							PlasmaBladeItem.ATTRIBUTE_DAMAGE - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
							PlasmaBladeItem.ATTRIBUTE_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build()));

	public static final List<Item> GUNS = List.of(SPARK_PISTOL, VOLT_SIDEARM, PULSE_CARBINE, ION_RIFLE, ARC_MAGNUM,
			LANCER_LONGRIFLE, STORM_REPEATER);
	public static final List<Item> CELL_PACKS = List.of(CELL_PACK_SMALL, CELL_PACK_STANDARD, CELL_PACK_HEAVY);
	public static final List<Item> ATTACHMENTS = List.of(OPTIC, DAMPENER, HEAT_SINK, CELL_REGULATOR, OVERCHARGER);

	/** Everything arms registers, in creative-tab order. */
	public static final List<Item> ALL = List.of(ENERGY_CELL, SCRAP_ELECTRONICS, WEAPON_PARTS, FOCUSING_LENS, RESONANCE_CRYSTAL, BATTERY, ALLOY_PLATE, WIRE_SPOOL,
			PIPE_WRENCH, SCRAP_MACHETE, REBAR_SPEAR, NAIL_LAUNCHER, NAILS, BOTTLE_LURE, ARC_CHARGE, CROWBAR,
			SPARK_PISTOL, VOLT_SIDEARM, PULSE_CARBINE, ION_RIFLE, ARC_MAGNUM, LANCER_LONGRIFLE, STORM_REPEATER, FLAMETHROWER,
			CELL_PACK_SMALL, CELL_PACK_STANDARD, CELL_PACK_HEAVY, OPTIC, DAMPENER, HEAT_SINK, CELL_REGULATOR, OVERCHARGER,
			PLASMA_BLADE, RESONANT_PLASMA_BLADE);

	private ArmsItems() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	/**
	 * Walking speed while the trigger is held. A held trigger counts as "using an item", which vanilla
	 * slows to 20% (bow-draw speed); that would make every firefight a crawl. Guns slow you a little,
	 * the heavy class a lot, and nobody sprints while firing.
	 */
	public static final float GUN_USE_SPEED = 0.8f;
	public static final float HEAVY_GUN_USE_SPEED = 0.5f;

	private static Item gun(String path, GunClass gunClass, boolean automatic, FireSound sound, BoltStyle style, Rarity rarity) {
		float speed = gunClass == GunClass.HEAVY ? HEAVY_GUN_USE_SPEED : GUN_USE_SPEED;
		return register(path, p -> new EnergyGunItem(p, gunClass, automatic, sound, style), new Item.Properties().stacksTo(1).rarity(rarity)
				// No item-use vibrations: the shot itself emits PROJECTILE_SHOOT, holding the trigger shouldn't.
				.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, speed)));
	}

	/** A full copy of an energy item (packs and cells), for the creative tab. */
	public static ItemStack charged(Item item) {
		ItemStack stack = new ItemStack(item);
		if (item instanceof CellPackItem energy) {
			energy.setStoredEnergy(stack, energy.getEnergyCapacity(stack));
		} else if (item instanceof PlasmaBladeItem blade) {
			blade.setStoredEnergy(stack, blade.getEnergyCapacity(stack));
			PlasmaBladeItem.refreshLit(stack);
		}
		return stack;
	}

	private static Item register(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Forces class loading (and so item registration), assigns weapon weights and registers the shared creative tab. */
	public static void init() {
		WeaponWeight.init();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ASHFALL_TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.ashfall.main"))
				.icon(() -> charged(ENERGY_CELL))
				.displayItems((parameters, output) -> {
					ALL.forEach(output::accept);
					// Charged variants next to the empty ones a crafting table makes.
					output.accept(charged(ENERGY_CELL));
					CELL_PACKS.forEach(pack -> output.accept(charged(pack)));
					output.accept(charged(PLASMA_BLADE));
					output.accept(charged(RESONANT_PLASMA_BLADE));
					output.accept(FlamethrowerLogic.withFuel(FlamethrowerLogic.TANK_TICKS));
				})
				.build());
	}
}
