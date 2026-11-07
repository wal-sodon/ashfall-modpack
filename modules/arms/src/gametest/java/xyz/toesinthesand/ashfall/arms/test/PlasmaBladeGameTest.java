package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.blade.BladeCrystal;
import xyz.toesinthesand.ashfall.arms.blade.BladeStats;
import xyz.toesinthesand.ashfall.arms.blade.BladeStatsManager;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.charger.ChargerBlockEntity;
import xyz.toesinthesand.ashfall.arms.charger.ChargerKind;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.senses.ItemLight;

import java.util.ArrayList;
import java.util.List;

/**
 * Plasma Blades on a real server: the 3-target sweep with 60% for the extra targets, charge per swing,
 * crystal wear, hunger only for extra targets, the weak hilt, re-socketing, chargers and light.
 * <p>
 * The sweep is driven the way Better Combat drives it: {@code Player.attack} once per target in one tick.
 */
public class PlasmaBladeGameTest {
	private static final float EPS = 0.01f;

	private static ItemStack blade(long energy, int integrity) {
		ItemStack stack = new ItemStack(ArmsItems.PLASMA_BLADE);
		SimpleEnergyItem.setStoredEnergyUnchecked(stack, energy);
		stack.set(ArmsComponents.BLADE_CRYSTAL, new BladeCrystal(integrity, 600));
		PlasmaBladeItem.refreshLit(stack);
		return stack;
	}

	/**
	 * A survival mock player holding {@code blade}. The mock never ticks, so the held item's attributes are
	 * applied by hand, plus a huge attack speed so every attack in the tick lands at full strength (Better
	 * Combat restores attack strength between the targets of a sweep in the same way).
	 */
	private static Player swordsman(GameTestHelper helper, ItemStack blade) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		player.setPos(pos.x, pos.y, pos.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, blade);
		blade.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> player.getAttribute(attribute).addTransientModifier(modifier));
		player.getAttribute(Attributes.ATTACK_SPEED).addTransientModifier(new AttributeModifier(
				Identifier.fromNamespaceAndPath("ashfall", "test_fast_swing"), 1000.0, AttributeModifier.Operation.ADD_VALUE));
		return player;
	}

	/** Food exhaustion has no getter; read it the way the save file does. */
	private static float exhaustion(Player player) {
		TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		player.getFoodData().addAdditionalSaveData(out);
		return out.buildResult().getFloatOr("foodExhaustionLevel", 0f);
	}

	private static List<IronGolem> golems(GameTestHelper helper, int count) {
		List<IronGolem> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			out.add(helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(1.5 + i * 2, 1, 4.5)));
		}
		return out;
	}

	@GameTest(maxTicks = 40)
	public void sweepStopsAfterTheThirdTarget(GameTestHelper helper) {
		helper.assertTrue(BladeStatsManager.loaded(ArmsItems.PLASMA_BLADE), "plasma_blade stats did not load from the datapack");
		ItemStack blade = blade(PlasmaBladeItem.CAPACITY, 600);
		Player player = swordsman(helper, blade);
		List<IronGolem> targets = golems(helper, 4);
		float start = targets.getFirst().getHealth();
		float exhaustionBefore = exhaustion(player);
		for (IronGolem golem : targets) {
			player.attack(golem); // one tick, one swing: exactly what a Better Combat sweep does
		}
		float[] expected = {9f, 5.4f, 5.4f, 0f};
		for (int i = 0; i < 4; i++) {
			helper.assertValueEqual(Math.round((start - targets.get(i).getHealth()) * 100) / 100f, expected[i],
					"damage to target " + (i + 1));
		}
		ItemStack held = player.getMainHandItem();
		helper.assertValueEqual(SimpleEnergyItem.getStoredEnergyUnchecked(held), PlasmaBladeItem.CAPACITY - 1, "one swing costs 1 energy");
		helper.assertValueEqual(PlasmaBladeItem.crystal(held).integrity(), 597, "each of the three hits wears the crystal");
		float extra = exhaustion(player) - exhaustionBefore;
		// Vanilla adds 0.1 per landed attack; the blade adds 1.0 for each of the two extra targets.
		helper.assertTrue(Math.abs(extra - 2.3f) < EPS, "exhaustion should be 2.3 (2 extra targets + 3 vanilla hits), got " + extra);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void unpoweredAndDullBladesAreBluntHilts(GameTestHelper helper) {
		for (ItemStack blade : List.of(blade(0, 600), blade(PlasmaBladeItem.CAPACITY, 0))) {
			helper.assertTrue(!blade.has(ArmsComponents.BLADE_LIT), "an empty or dull blade must not be lit");
			Player player = swordsman(helper, blade);
			IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(3.5, 1, 4.5));
			float start = golem.getHealth();
			player.attack(golem);
			helper.assertTrue(Math.abs(start - golem.getHealth() - BladeStats.DEFAULT.unpoweredDamage()) < EPS,
					"a hilt hit should deal " + BladeStats.DEFAULT.unpoweredDamage() + ", dealt " + (start - golem.getHealth()));
			ItemStack held = player.getMainHandItem();
			helper.assertValueEqual(PlasmaBladeItem.crystal(held).integrity(), PlasmaBladeItem.crystal(blade).integrity(),
					"a hilt hit must not wear the crystal");
			golem.discard();
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void crystalWearsOutAndCanBeResocketed(GameTestHelper helper) {
		ItemStack blade = blade(50, 1);
		Player player = swordsman(helper, blade);
		IronGolem golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(3.5, 1, 4.5));
		player.attack(golem);
		ItemStack dull = player.getMainHandItem();
		helper.assertTrue(PlasmaBladeItem.crystal(dull).dull(), "the last point of integrity should leave the blade dull");
		helper.assertTrue(!dull.has(ArmsComponents.BLADE_LIT), "a dull blade goes dark");

		CraftingInput input = CraftingInput.of(2, 1, List.of(dull, new ItemStack(ArmsItems.RESONANCE_CRYSTAL)));
		ItemStack socketed = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertTrue(socketed.is(ArmsItems.PLASMA_BLADE), "socketing should give the blade back, got " + socketed);
		helper.assertValueEqual(PlasmaBladeItem.crystal(socketed).integrity(), 600, "a fresh crystal");
		helper.assertValueEqual(SimpleEnergyItem.getStoredEnergyUnchecked(socketed), 49L, "socketing keeps the charge");
		helper.assertTrue(socketed.has(ArmsComponents.BLADE_LIT), "a re-socketed charged blade is lit again");

		CraftingInput full = CraftingInput.of(2, 1, List.of(blade(10, 600), new ItemStack(ArmsItems.RESONANCE_CRYSTAL)));
		helper.assertTrue(helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, full, helper.getLevel()).isEmpty(),
				"a blade with a full crystal must not eat a crystal");
		helper.succeed();
	}

	@GameTest
	public void chargersChargeBladesButNeverDrawOnThem(GameTestHelper helper) {
		ItemStack empty = blade(0, 600);
		helper.assertTrue(ChargerBlockEntity.canCharge(ChargerKind.FIELD_CHARGER, empty), "a Field Charger must charge a blade");
		helper.assertTrue(ChargerBlockEntity.canCharge(ChargerKind.POWER_RELAY, empty), "a Power Relay must charge a blade");
		helper.assertTrue(!ChargerBlockEntity.canFuel(blade(PlasmaBladeItem.CAPACITY, 600)), "a blade must not fuel a charger");
		helper.succeed();
	}

	@GameTest
	public void litBladesShineForTheDirector(GameTestHelper helper) {
		helper.assertValueEqual(ItemLight.luminance(blade(PlasmaBladeItem.CAPACITY, 600)), 12, "lit blade light");
		helper.assertValueEqual(ItemLight.luminance(blade(0, 600)), 0, "an unpowered blade is dark");
		helper.assertTrue(helper.getLevel().getServer().getResourceManager()
				.getResource(Identifier.fromNamespaceAndPath("ashfall", "weapon_attributes/plasma_blade.json")).isPresent(),
				"Better Combat weapon attributes missing");
		helper.succeed();
	}
}
