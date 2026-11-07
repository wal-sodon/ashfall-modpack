package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.gun.DrawTime;
import xyz.toesinthesand.ashfall.arms.gun.FlamerBurn;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic;
import xyz.toesinthesand.ashfall.arms.gun.FlamethrowerLogic.FireResult;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.WeaponWeight;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.ArrayList;
import java.util.List;

/**
 * The flamethrower: refuelling and crafting carry Lighter Fluid's remaining uses, the cone hits and ignites what it
 * reaches and nothing past a wall or outside it, it never places fire, never touches friendlies, burns fuel and makes
 * noise on its schedule, lights campfires and candles, and its burn never ends a Mutant's revive. Husks stand in for
 * zombies because they don't catch fire in the sun on their own.
 */
public class FlamethrowerGameTest {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A shooter standing at (1.5, 1, 1.5) facing south (+z), level. */
	private static Player shooter(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		player.setPos(pos.x, pos.y, pos.z);
		player.setYRot(0f);
		player.setXRot(0f);
		return player;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		return helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(x, 1, z));
	}

	private static ItemStack can(int used) {
		ItemStack can = new ItemStack(SalvageItems.LIGHTER_FLUID);
		can.setDamageValue(used);
		return can;
	}

	/** One flame hit: {@link FlamethrowerLogic#DAMAGE}, less a little for armour. */
	private static boolean hitOnce(float before, float after) {
		float lost = before - after;
		return lost > FlamethrowerLogic.DAMAGE * 0.8f && lost <= FlamethrowerLogic.DAMAGE + 1e-4f;
	}

	/** Not burning and not hurt. */
	private static boolean untouched(LivingEntity e) {
		return e.getHealth() >= e.getMaxHealth() && e.getRemainingFireTicks() <= 0 && e.getLastHurtByMob() == null;
	}

	@GameTest
	public void flamerRefuelProportional(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack flamer = FlamethrowerLogic.withFuel(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, flamer);
		player.getInventory().setItem(10, can(0));
		player.getInventory().setItem(11, can(SalvageItems.LIGHTER_FLUID_USES - 8));
		// The half-used can goes first: 8 uses, 80 ticks, and it is used up.
		helper.assertValueEqual(FlamethrowerLogic.refuel(player, flamer), 80, "half can");
		helper.assertTrue(player.getInventory().getItem(11).isEmpty(), "the emptied can is used up");
		helper.assertTrue(player.getCooldowns().isOnCooldown(flamer), "refuelling takes a moment");
		// A full can: 16 uses, 160 ticks.
		helper.assertValueEqual(FlamethrowerLogic.refuel(player, flamer), 160, "full can");
		helper.assertValueEqual(FlamethrowerLogic.fuel(flamer), 240, "fuel after two cans");
		// Only 8 uses fit: the can pours those and keeps the rest.
		player.getInventory().setItem(12, can(0));
		helper.assertValueEqual(FlamethrowerLogic.refuel(player, flamer), 80, "partial pour");
		helper.assertValueEqual(FlamethrowerLogic.fuel(flamer), FlamethrowerLogic.TANK_TICKS, "full tank");
		helper.assertValueEqual(player.getInventory().getItem(12).getDamageValue(), 8, "the can kept its other 8 uses");
		// A full tank refuses.
		helper.assertValueEqual(FlamethrowerLogic.refuel(player, flamer), 0, "a full tank took fuel");
		helper.assertValueEqual(player.getInventory().getItem(12).getDamageValue(), 8, "a refused pour used the can");
		// No can at all: nothing happens.
		Player dry = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack empty = FlamethrowerLogic.withFuel(0);
		dry.setItemInHand(InteractionHand.MAIN_HAND, empty);
		helper.assertValueEqual(FlamethrowerLogic.refuel(dry, empty), 0, "refuelled from nothing");
		helper.succeed();
	}

	@GameTest
	public void flamerCraftCarriesFuel(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		ItemStack t = new ItemStack(SalvageItems.DUCT_TAPE), s = new ItemStack(SalvageItems.SCRAP_METAL),
				w = new ItemStack(ArmsItems.WIRE_SPOOL), p = new ItemStack(ArmsItems.WEAPON_PARTS), e = ItemStack.EMPTY;
		// Weapon Parts are part of the cost: without them the old cheap recipe makes nothing.
		CraftingInput cheap = CraftingInput.of(3, 3, List.of(t, s, s, can(0), w, s, t, e, e));
		helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, cheap, helper.getLevel()).isEmpty(),
				"a flamethrower crafted without Weapon Parts");
		for (int used : new int[]{0, 4, 15}) {
			CraftingInput input = CraftingInput.of(3, 3, List.of(t, s, s, can(used), w, s, t, p, e));
			ItemStack out = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
					.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
			helper.assertTrue(out.is(ArmsItems.FLAMETHROWER), "crafted " + out + " from a can with " + used + " uses gone");
			int left = SalvageItems.LIGHTER_FLUID_USES - used;
			helper.assertValueEqual(FlamethrowerLogic.fuel(out), left * FlamethrowerLogic.TICKS_PER_USE, "starting fuel, " + left + " uses left");
		}
		helper.succeed();
	}

	@GameTest
	public void flamerHitsAndIgnitesInCone(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		Husk near = husk(helper, 1.5, 4.5);
		Husk aside = husk(helper, 3.0, 5.5); // about 20 degrees off the aim, still in the cone
		ItemStack flamer = FlamethrowerLogic.withFuel(100);
		// Compare against what each started with: a husk's natural armour takes a little off every hit.
		float nearStart = near.getHealth();
		float asideStart = aside.getHealth();
		helper.assertValueEqual(FlamethrowerLogic.tick(level, player, flamer, 0), FireResult.FIRED, "fire");
		helper.assertTrue(hitOnce(asideStart, aside.getHealth()), "aside husk health " + aside.getHealth());
		for (Husk husk : List.of(near, aside)) {
			helper.assertTrue(husk.getRemainingFireTicks() >= FlamethrowerLogic.BURN_TICKS - 1, "husk not burning: " + husk.getRemainingFireTicks());
			helper.assertTrue(FlamerBurn.isFlamerBurn(husk), "the burn should be the flamethrower's");
		}
		helper.assertValueEqual(FlamethrowerLogic.fuel(flamer), 99, "one tick of fuel");
		// Ticks between hits spend fuel but don't hit; the damage type ignores hurt cooldowns, so the next hit lands.
		FlamethrowerLogic.tick(level, player, flamer, 1);
		helper.assertTrue(hitOnce(nearStart, near.getHealth()), "near husk health " + near.getHealth());
		float afterOne = near.getHealth();
		FlamethrowerLogic.tick(level, player, flamer, FlamethrowerLogic.HIT_INTERVAL);
		helper.assertTrue(hitOnce(afterOne, near.getHealth()), "second hit blocked by the hurt cooldown: " + near.getHealth());
		helper.assertValueEqual(FlamethrowerLogic.fuel(flamer), 97, "fuel per tick");
		// Mobs never fire it.
		helper.assertValueEqual(FlamethrowerLogic.tick(level, near, flamer, 0), FireResult.NOT_A_PLAYER, "a husk fired it");
		helper.succeed();
	}

	@GameTest
	public void flamerBlockedByWall(GameTestHelper helper) {
		floor(helper);
		for (int x = 0; x < 4; x++) {
			for (int y = 1; y < 4; y++) {
				helper.setBlock(x, y, 3, Blocks.STONE);
			}
		}
		Player player = shooter(helper);
		Husk behind = husk(helper, 1.5, 5.0);
		FlamethrowerLogic.tick(helper.getLevel(), player, FlamethrowerLogic.withFuel(100), 0);
		helper.assertTrue(untouched(behind), "the flame went through a wall");
		helper.succeed();
	}

	@GameTest
	public void flamerOutsideCone(GameTestHelper helper) {
		floor(helper);
		Player player = shooter(helper);
		Husk wide = husk(helper, 4.5, 4.5);  // 45 degrees off the aim
		Husk behind = husk(helper, 1.5, 0.5); // behind the shooter
		Husk far = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(1.5, 1, 1.5 + FlamethrowerLogic.RANGE + 1.5));
		helper.assertTrue(!FlamethrowerLogic.inCone(Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 0, FlamethrowerLogic.RANGE + 0.1)),
				"cone reaches past its range");
		FlamethrowerLogic.tick(helper.getLevel(), player, FlamethrowerLogic.withFuel(100), 0);
		helper.assertTrue(untouched(wide), "hit outside the cone's angle");
		helper.assertTrue(untouched(behind), "hit behind the shooter");
		helper.assertTrue(untouched(far), "hit past the cone's range");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void flamerNeverPlacesFire(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.OAK_PLANKS);
			}
		}
		helper.setBlock(1, 1, 6, Blocks.HAY_BLOCK);
		helper.setBlock(2, 1, 5, Blocks.BOOKSHELF);
		Player player = shooter(helper);
		player.setXRot(30f); // aimed down at the wooden floor
		husk(helper, 1.5, 3.5);
		ItemStack flamer = FlamethrowerLogic.withFuel(FlamethrowerLogic.TANK_TICKS);
		int[] tick = {0};
		helper.onEachTick(() -> FlamethrowerLogic.tick(helper.getLevel(), player, flamer, tick[0]++));
		helper.runAfterDelay(60, () -> {
			List<BlockPos> fire = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 7, 4, 7)) {
				if (helper.getBlockState(pos).is(Blocks.FIRE)) {
					fire.add(pos.immutable());
				}
			}
			helper.assertTrue(fire.isEmpty(), "fire placed at " + fire);
			helper.succeed();
		});
	}

	@GameTest
	public void flamerSparesFriendlies(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		Wolf dog = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(1.5, 1, 3.5));
		dog.tame(helper.makeMockPlayer(GameType.SURVIVAL));
		Survivor survivor = Survivors.spawn(level, helper.absolutePos(new BlockPos(2, 1, 4)), SurvivorRole.SETTLER, null);
		helper.assertTrue(survivor != null, "survivor spawned");
		Player friend = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(0.8, 1, 4.5));
		friend.setPos(at.x, at.y, at.z);
		Husk husk = husk(helper, 1.5, 5.5);
		FlamethrowerLogic.tick(level, player, FlamethrowerLogic.withFuel(100), 0);
		helper.assertTrue(untouched(dog), "a pet was burnt");
		helper.assertTrue(untouched(survivor), "a survivor was burnt");
		helper.assertTrue(friend.getRemainingFireTicks() <= 0 && friend.getHealth() == friend.getMaxHealth(), "a player was burnt");
		helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the husk behind them should still be hit");
		helper.succeed();
	}

	@GameTest
	public void flamerFuelAndNoise(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		List<Float> heard = new ArrayList<>();
		ArmsNoise.Listener listener = (l, pos, radius) -> {
			if (pos.equals(player.blockPosition())) {
				heard.add(radius);
			}
		};
		ItemStack flamer = FlamethrowerLogic.withFuel(15);
		ArmsNoise.addListener(listener);
		try {
			for (int t = 0; t < 15; t++) {
				helper.assertValueEqual(FlamethrowerLogic.tick(level, player, flamer, t), FireResult.FIRED, "tick " + t);
			}
			helper.assertValueEqual(heard, List.of(FlamethrowerLogic.NOISE_RADIUS, FlamethrowerLogic.NOISE_RADIUS),
					"noise at ticks 0 and 10 only");
			helper.assertValueEqual(FlamethrowerLogic.NOISE_RADIUS, 40f, "flamethrower noise radius (louder than a Spark shot)");
			helper.assertValueEqual(FlamethrowerLogic.fuel(flamer), 0, "fuel after 15 ticks");
			FireResult dry = FlamethrowerLogic.tick(level, player, flamer, 15);
			helper.assertValueEqual(dry, FireResult.EMPTY, "an empty tank fired");
			helper.assertTrue(dry.stopsFiring(), "an empty tank should release the trigger");
			helper.assertValueEqual(heard.size(), 2, "a dry click made noise");
		} finally {
			ArmsNoise.removeListener(listener);
		}
		helper.succeed();
	}

	@GameTest
	public void flamerLightsCampfire(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(1, 1, 4, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		helper.setBlock(2, 1, 5, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false));
		helper.setBlock(6, 1, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)); // off to the side
		Player player = shooter(helper);
		FlamethrowerLogic.tick(helper.getLevel(), player, FlamethrowerLogic.withFuel(100), 0);
		helper.assertBlockProperty(new BlockPos(1, 1, 4), CampfireBlock.LIT, true);
		helper.assertBlockProperty(new BlockPos(2, 1, 5), CandleBlock.LIT, true);
		helper.assertBlockProperty(new BlockPos(6, 1, 1), CampfireBlock.LIT, false);
		helper.succeed();
	}

	@GameTest
	public void flamerBurnSparesMutantRevive(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		Husk flamed = husk(helper, 1.5, 3.5);
		FlamethrowerLogic.tick(level, player, FlamethrowerLogic.withFuel(100), 0);
		helper.assertTrue(FlamerBurn.isFlamerBurn(flamed), "the flamethrower's burn is its own");
		// At death the flamethrower's burn is put out, so a Mutant still revives.
		helper.assertTrue(FlamerBurn.spareRevive(flamed), "the flamethrower's burn was not put out");
		helper.assertTrue(flamed.getRemainingFireTicks() <= 0, "still burning");

		// A longer burn from anything else (a Lancer's 100 ticks) takes over: that fire counts.
		Husk lanced = husk(helper, 4.5, 4.5);
		FlamerBurn.ignite(lanced, FlamethrowerLogic.BURN_TICKS);
		lanced.setRemainingFireTicks(100);
		helper.assertTrue(!FlamerBurn.isFlamerBurn(lanced), "someone else's longer burn still counted as the flamethrower's");
		helper.assertTrue(!FlamerBurn.spareRevive(lanced) && lanced.getRemainingFireTicks() == 100, "someone else's fire was put out");

		// Someone else's fire first: the flamethrower lengthens it but doesn't claim it.
		Husk sunburnt = husk(helper, 6.5, 6.5);
		sunburnt.setRemainingFireTicks(40);
		FlamerBurn.ignite(sunburnt, FlamethrowerLogic.BURN_TICKS);
		helper.assertValueEqual(sunburnt.getRemainingFireTicks(), FlamethrowerLogic.BURN_TICKS, "lengthened");
		helper.assertTrue(!FlamerBurn.isFlamerBurn(sunburnt), "the flamethrower claimed someone else's fire");

		// A dying mob is never ignited (Mutant Monsters ends the revive on ignition mid-death).
		Husk dying = husk(helper, 1.5, 5.5);
		dying.setHealth(0f);
		FlamethrowerLogic.burnCone(level, player, player.getEyePosition(), player.getLookAngle());
		helper.assertTrue(dying.getRemainingFireTicks() <= 0, "a dying mob was set alight");

		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(FlamerBurn.REVIVE_SAFE).isPresent(), "#ashfall:flamer_revive_safe did not load");
		helper.succeed();
	}

	@GameTest
	public void flamerWeightAndDraw(GameTestHelper helper) {
		helper.assertValueEqual(WeaponWeight.of(ArmsItems.FLAMETHROWER), WeaponWeight.VERY_HEAVY, "flamethrower weight class");
		ItemAttributeModifiers mods = new ItemStack(ArmsItems.FLAMETHROWER).get(DataComponents.ATTRIBUTE_MODIFIERS);
		helper.assertTrue(mods != null && mods.modifiers().stream().anyMatch(e -> e.matches(Attributes.MOVEMENT_SPEED, WeaponWeight.MODIFIER_ID)
				&& e.slot() == EquipmentSlotGroup.MAINHAND && Math.abs(e.modifier().amount() - WeaponWeight.VERY_HEAVY.speed()) < 1e-6),
				"no very heavy main-hand modifier");

		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getInventory().setSelectedSlot(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, FlamethrowerLogic.withFuel(100));
		ItemStack flamer = player.getMainHandItem();
		player.getInventory().tick();
		helper.assertValueEqual(DrawTime.remainingCooldown(player, flamer), FlamethrowerLogic.DRAW_TICKS, "flamethrower draw");
		helper.assertValueEqual(FlamethrowerLogic.tick(helper.getLevel(), player, flamer, 0), FireResult.COOLDOWN, "fired during the draw");
		helper.assertValueEqual(FlamethrowerLogic.fuel(flamer), 100, "fuel spent during the draw");
		helper.succeed();
	}
}
