package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.DrawTime;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunStatsManager;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;
import xyz.toesinthesand.ashfall.arms.item.WeaponWeight;

import java.util.Map;

/** Weapon weight (main-hand movement modifiers) and blaster draw and holster time. */
public class WeaponHandlingGameTest {
	private static final double EPS = 1e-6;

	private static AttributeModifier modifier(Item item, Holder<Attribute> attribute, Identifier id) {
		ItemAttributeModifiers mods = new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.matches(attribute, id) && e.slot() == EquipmentSlotGroup.MAINHAND) {
				return e.modifier();
			}
		}
		return null;
	}

	private static int drawTicks(Item gun) {
		return GunStatsManager.get(false, gun).drawTicks();
	}

	@GameTest
	public void weightByClass(GameTestHelper helper) {
		Map<Item, WeaponWeight> expected = Map.ofEntries(
				Map.entry(ArmsItems.SPARK_PISTOL, WeaponWeight.LIGHT), Map.entry(ArmsItems.VOLT_SIDEARM, WeaponWeight.LIGHT),
				Map.entry(ArmsItems.ARC_MAGNUM, WeaponWeight.LIGHT), Map.entry(ArmsItems.CROWBAR, WeaponWeight.LIGHT),
				Map.entry(ArmsItems.SCRAP_MACHETE, WeaponWeight.LIGHT), Map.entry(ArmsItems.PIPE_WRENCH, WeaponWeight.MEDIUM),
				Map.entry(ArmsItems.NAIL_LAUNCHER, WeaponWeight.MEDIUM), Map.entry(ArmsItems.PULSE_CARBINE, WeaponWeight.MEDIUM),
				Map.entry(ArmsItems.ION_RIFLE, WeaponWeight.MEDIUM), Map.entry(ArmsItems.PLASMA_BLADE, WeaponWeight.MEDIUM),
				Map.entry(ArmsItems.RESONANT_PLASMA_BLADE, WeaponWeight.MEDIUM), Map.entry(ArmsItems.REBAR_SPEAR, WeaponWeight.HEAVY),
				Map.entry(ArmsItems.LANCER_LONGRIFLE, WeaponWeight.HEAVY), Map.entry(ArmsItems.STORM_REPEATER, WeaponWeight.VERY_HEAVY));
		for (Map.Entry<Item, WeaponWeight> e : expected.entrySet()) {
			String id = BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
			helper.assertValueEqual(WeaponWeight.of(e.getKey()), e.getValue(), id + " weight class");
			AttributeModifier m = modifier(e.getKey(), Attributes.MOVEMENT_SPEED, WeaponWeight.MODIFIER_ID);
			helper.assertTrue(m != null, id + " has no main-hand weight modifier");
			helper.assertTrue(Math.abs(m.amount() - e.getValue().speed()) < EPS, id + " weight " + m.amount());
			helper.assertValueEqual(m.operation(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, id + " weight operation");
		}
		helper.assertValueEqual(WeaponWeight.LIGHT.speed(), -0.03, "light");
		helper.assertValueEqual(WeaponWeight.MEDIUM.speed(), -0.07, "medium");
		helper.assertValueEqual(WeaponWeight.HEAVY.speed(), -0.10, "heavy");
		helper.assertValueEqual(WeaponWeight.VERY_HEAVY.speed(), -0.15, "very heavy");

		// Only the main hand counts; an unweighted item (or anything in the off hand) leaves speed alone.
		ItemAttributeModifiers storm = new ItemStack(ArmsItems.STORM_REPEATER).get(DataComponents.ATTRIBUTE_MODIFIERS);
		helper.assertTrue(Math.abs(storm.compute(Attributes.MOVEMENT_SPEED, 0.1, EquipmentSlot.MAINHAND) - 0.085) < EPS, "storm in hand");
		helper.assertTrue(Math.abs(storm.compute(Attributes.MOVEMENT_SPEED, 0.1, EquipmentSlot.OFFHAND) - 0.1) < EPS, "storm in off hand");
		helper.assertTrue(modifier(ArmsItems.CELL_PACK_SMALL, Attributes.MOVEMENT_SPEED, WeaponWeight.MODIFIER_ID) == null, "a pack has weight");
		helper.assertTrue(modifier(Items.IRON_SWORD, Attributes.MOVEMENT_SPEED, WeaponWeight.MODIFIER_ID) == null, "a vanilla sword has weight");
		helper.succeed();
	}

	@GameTest
	public void meleeAttributesUnchanged(GameTestHelper helper) {
		float iron = ToolMaterial.IRON.attackDamageBonus();
		Object[][] swords = {
				{ArmsItems.PIPE_WRENCH, iron + 4.0, -2.6}, // 10.2.2: was -3.0
				{ArmsItems.SCRAP_MACHETE, iron + 2.5, -2.0},
				{ArmsItems.REBAR_SPEAR, iron + 3.0, -2.8},
				{ArmsItems.CROWBAR, iron + 1.5, -2.6},
				{ArmsItems.PLASMA_BLADE, PlasmaBladeItem.ATTRIBUTE_DAMAGE - 1.0, PlasmaBladeItem.ATTRIBUTE_SPEED - 4.0},
				{ArmsItems.RESONANT_PLASMA_BLADE, PlasmaBladeItem.ATTRIBUTE_DAMAGE - 1.0, PlasmaBladeItem.ATTRIBUTE_SPEED - 4.0},
		};
		for (Object[] row : swords) {
			Item item = (Item) row[0];
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			AttributeModifier damage = modifier(item, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
			AttributeModifier speed = modifier(item, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID);
			helper.assertTrue(damage != null && Math.abs(damage.amount() - ((Number) row[1]).doubleValue()) < 1e-4,
					id + " attack damage " + (damage == null ? "missing" : damage.amount()));
			helper.assertTrue(speed != null && Math.abs(speed.amount() - ((Number) row[2]).doubleValue()) < 1e-4,
					id + " attack speed " + (speed == null ? "missing" : speed.amount()));
			// Damage, speed and the weight: nothing lost, nothing doubled.
			helper.assertValueEqual(new ItemStack(item).get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size(), 3, id + " modifier count");
		}
		helper.succeed();
	}

	@GameTest
	public void drawTimesFromStats(GameTestHelper helper) {
		helper.assertValueEqual(drawTicks(ArmsItems.SPARK_PISTOL), 6, "spark draw");
		helper.assertValueEqual(drawTicks(ArmsItems.VOLT_SIDEARM), 6, "volt draw");
		helper.assertValueEqual(drawTicks(ArmsItems.ARC_MAGNUM), 6, "magnum draw");
		helper.assertValueEqual(drawTicks(ArmsItems.PULSE_CARBINE), 10, "carbine draw");
		helper.assertValueEqual(drawTicks(ArmsItems.ION_RIFLE), 10, "ion draw");
		helper.assertValueEqual(drawTicks(ArmsItems.LANCER_LONGRIFLE), 14, "lancer draw");
		helper.assertValueEqual(drawTicks(ArmsItems.STORM_REPEATER), 18, "storm draw");
		helper.succeed();
	}

	@GameTest
	public void drawBlocksFire(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long now = helper.getLevel().getGameTime();
		ItemStack carbine = new ItemStack(ArmsItems.PULSE_CARBINE);
		carbine.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(
				EnergyItems.withEnergy(new ItemStack(ArmsItems.CELL_PACK_STANDARD), 60)));

		helper.assertValueEqual(DrawTime.onMainHandTick(player, 0, carbine, 10, now), 10, "fresh draw");
		helper.assertTrue(player.getCooldowns().isOnCooldown(carbine), "a drawn gun should be cooling down");
		helper.assertValueEqual(DrawTime.remainingCooldown(player, carbine), 10, "draw cooldown");
		helper.assertValueEqual(DrawTime.onMainHandTick(player, 0, carbine, 10, now + 1), 0, "still held next tick");

		// A trigger held through the draw does nothing until it ends.
		carbine.getItem().onUseTick(helper.getLevel(), player, carbine, 0);
		ItemStackTemplate pack = carbine.get(ArmsComponents.LOADED_PACK);
		helper.assertValueEqual(EnergyItems.stored(pack), 60L, "energy after firing during the draw");
		helper.assertTrue(carbine.get(ArmsComponents.GUN_STATE) == null, "a shot was fired during the draw");
		helper.succeed();
	}

	@GameTest
	public void holsterAddsTime(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long now = helper.getLevel().getGameTime();
		ItemStack spark = new ItemStack(ArmsItems.SPARK_PISTOL);
		ItemStack ion = new ItemStack(ArmsItems.ION_RIFLE);
		DrawTime.onMainHandTick(player, 0, spark, 6, now);
		// Straight from the pistol to the rifle: draw plus the pistol's holster time.
		helper.assertValueEqual(DrawTime.onMainHandTick(player, 1, ion, 10, now + 1), 10 + DrawTime.HOLSTER_TICKS, "gun to gun");
		helper.assertValueEqual(DrawTime.remainingCooldown(player, ion), 10 + DrawTime.HOLSTER_TICKS, "gun to gun cooldown");
		// Via a melee weapon (a tick without a gun): just the draw.
		Player other = helper.makeMockPlayer(GameType.SURVIVAL);
		DrawTime.onMainHandTick(other, 0, spark, 6, now);
		helper.assertValueEqual(DrawTime.onMainHandTick(other, 1, ion, 10, now + 2), 10, "gun, melee, gun");
		// The same gun type in another slot is still a draw.
		helper.assertValueEqual(DrawTime.onMainHandTick(other, 2, new ItemStack(ArmsItems.ION_RIFLE), 10, now + 3),
				10 + DrawTime.HOLSTER_TICKS, "slot to slot");
		helper.succeed();
	}

	@GameTest
	public void drawNeverShortensReload(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long now = helper.getLevel().getGameTime();
		ItemStack ion = new ItemStack(ArmsItems.ION_RIFLE);
		player.getCooldowns().addCooldown(ion, 30);
		DrawTime.onMainHandTick(player, 0, ion, 10, now);
		helper.assertValueEqual(DrawTime.remainingCooldown(player, ion), 30, "a draw shortened a running reload");
		// A shorter cooldown (a dry click) is stretched to the full draw.
		Player other = helper.makeMockPlayer(GameType.SURVIVAL);
		other.getCooldowns().addCooldown(ion, 3);
		DrawTime.onMainHandTick(other, 0, ion, 10, now);
		helper.assertValueEqual(DrawTime.remainingCooldown(other, ion), 10, "a draw should outlast a shorter cooldown");
		helper.succeed();
	}

	@GameTest
	public void inventoryTickStartsTheDraw(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().setSelectedSlot(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmsItems.LANCER_LONGRIFLE));
		ItemStack lancer = player.getMainHandItem();
		player.getInventory().tick();
		helper.assertValueEqual(DrawTime.remainingCooldown(player, lancer), 14, "lancer draw from the inventory tick");
		// Melee weapons draw instantly.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArmsItems.PIPE_WRENCH));
		player.getInventory().tick();
		helper.assertTrue(!player.getCooldowns().isOnCooldown(player.getMainHandItem()), "a melee weapon got a draw time");
		helper.succeed();
	}

	/** Guns and the flamethrower do nothing from the off hand, so the weight and draw time can't be skipped. */
	@GameTest
	public void offHandWeaponsDoNothing(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		ItemStack storm = new ItemStack(ArmsItems.STORM_REPEATER);
		storm.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(
				EnergyItems.withEnergy(new ItemStack(ArmsItems.CELL_PACK_STANDARD), 60)));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, storm);
		InteractionResult gun = storm.getItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
		helper.assertValueEqual(gun, InteractionResult.PASS, "off-hand gun use");
		helper.assertTrue(!player.isUsingItem(), "an off-hand gun started firing");
		helper.assertValueEqual(EnergyItems.stored(storm.get(ArmsComponents.LOADED_PACK)), 60L, "off-hand gun spent energy");
		helper.assertTrue(GunLogic.heldGun(player).isEmpty(), "reload/mode/scope found the off-hand gun");

		ItemStack flamer = FlamethrowerLogic.withFuel(FlamethrowerLogic.TANK_TICKS);
		player.setItemInHand(InteractionHand.OFF_HAND, flamer);
		InteractionResult flame = flamer.getItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
		helper.assertValueEqual(flame, InteractionResult.PASS, "off-hand flamethrower use");
		helper.assertTrue(!player.isUsingItem(), "an off-hand flamethrower started firing");
		helper.assertTrue(FlamethrowerLogic.heldFlamethrower(player).isEmpty(), "refuel found the off-hand flamethrower");

		// The main hand still works.
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.MAIN_HAND, flamer);
		helper.assertTrue(FlamethrowerLogic.heldFlamethrower(player) == flamer, "main-hand flamethrower lookup");
		helper.succeed();
	}
}
