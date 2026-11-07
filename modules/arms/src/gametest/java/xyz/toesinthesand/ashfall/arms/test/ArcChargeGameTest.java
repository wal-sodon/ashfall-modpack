package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.entity.ArcCharge;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.item.ArcChargeItem;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.senses.Stun;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.List;

/**
 * The Arc Charge stun grenade: it sticks, waits out its fuse, then stuns every non-friendly mob in range and in sight
 * through the ordinary stun rules, hurts nothing and is heard 32 blocks away.
 */
public class ArcChargeGameTest {
	/** Where the charges go off: just above the floor in the corner of the 8x8 plot. */
	private static final Vec3 BURST = new Vec3(1.5, 1.3, 1.5);

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new Vec3(x, 1, z));
		husk.setNoAi(true);
		return husk;
	}

	@GameTest(maxTicks = 80)
	public void arcChargeStunsWithinRadiusAfterFuse(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Husk near = husk(helper, 4.5, 1.5); // 3 blocks away
		Husk far = husk(helper, 1.5, 7.5);  // about 5.7 blocks to its nearest side
		ArcCharge charge = new ArcCharge(ArmsEntities.ARC_CHARGE, level);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.2, 1.5));
		charge.setPos(at);
		level.addFreshEntity(charge);
		charge.arm(at);
		helper.runAfterDelay(ArcCharge.FUSE_TICKS / 2, () -> {
			helper.assertTrue(!charge.isRemoved(), "the charge went off before its fuse ran out");
			helper.assertTrue(!Stun.isStunned(near), "stunned before the burst");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(charge.isRemoved(), "the charge has not gone off");
			helper.assertTrue(Stun.isStunned(near), "a husk 3 blocks away should be stunned");
			helper.assertTrue(!Stun.isStunned(far), "a husk beyond 5 blocks must not be stunned");
		});
	}

	@GameTest(maxTicks = 80)
	public void arcChargeThrownSticksAndBursts(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Husk near = husk(helper, 4.5, 2.5);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		player.setXRot(90f); // straight down at the floor, so nothing flies into a neighbouring test
		ItemStack stack = new ItemStack(ArmsItems.ARC_CHARGE, 3);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(stack.getCount(), 2, "one charge thrown");
		helper.assertTrue(player.getCooldowns().isOnCooldown(stack), "a short cooldown after a throw");
		helper.assertValueEqual(ArcChargeItem.COOLDOWN_TICKS, 10, "cooldown ticks");
		helper.assertValueEqual(new ItemStack(ArmsItems.ARC_CHARGE).getMaxStackSize(), 8, "stack size");
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getEntities(ArmsEntities.ARC_CHARGE).isEmpty(), "the thrown charge has not gone off");
			helper.assertTrue(Stun.isStunned(near), "the thrown charge should stun the husk");
		});
	}

	@GameTest
	public void arcChargeBlockedByWalls(GameTestHelper helper) {
		floor(helper);
		for (int z = 0; z < 8; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(3, y, z, Blocks.STONE);
			}
		}
		Husk behindWall = husk(helper, 4.5, 1.5);
		Husk inSight = husk(helper, 1.5, 4.5);
		int stunned = ArcCharge.burst(helper.getLevel(), helper.absoluteVec(BURST), null);
		helper.assertTrue(!Stun.isStunned(behindWall), "the burst must not stun through a wall");
		helper.assertTrue(Stun.isStunned(inSight), "the husk in sight should be stunned");
		helper.assertValueEqual(stunned, 1, "mobs stunned");
		helper.succeed();
	}

	@GameTest
	public void arcChargeSparesFriendlies(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 1.5)));
		Wolf wolf = helper.spawn(EntityTypes.WOLF, new Vec3(1.5, 1, 3.5));
		wolf.tame(player);
		Vec3 rel = new Vec3(3.5, 1, 3.5);
		Survivor survivor = Survivors.spawn(level, helper.absolutePos(BlockPos.containing(rel)), SurvivorRole.SETTLER, null);
		helper.assertTrue(survivor != null, "survivor spawned");
		Vec3 abs = helper.absoluteVec(rel);
		survivor.snapTo(abs.x, abs.y, abs.z);
		survivor.setNoAi(true);
		Husk husk = husk(helper, 2.5, 4.5);
		ArcCharge.burst(level, helper.absoluteVec(BURST), player);
		helper.assertTrue(Stun.isStunned(husk), "the husk should be stunned");
		helper.assertTrue(!Stun.isStunned(wolf), "a tamed wolf is never stunned");
		helper.assertTrue(!Stun.isStunned(survivor), "a survivor is never stunned");
		helper.assertTrue(!player.hasEffect(Stun.STUNNED), "a player is never stunned");
		// Even with no thrower known, players and pets are spared.
		Player other = helper.makeMockPlayer(GameType.SURVIVAL);
		other.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		ArcCharge.burst(level, helper.absoluteVec(BURST), null);
		helper.assertTrue(!other.hasEffect(Stun.STUNNED) && !Stun.isStunned(wolf), "an ownerless burst spared friendlies");
		helper.succeed();
	}

	@GameTest
	public void arcChargeLoudButHarmless(GameTestHelper helper) {
		floor(helper);
		Husk husk = husk(helper, 3.5, 2.5);
		float max = husk.getHealth();
		float[] heard = {0f};
		BlockPos[] at = {null};
		ArmsNoise.Listener listener = (level, pos, radius) -> {
			heard[0] = radius;
			at[0] = pos;
		};
		Vec3 burst = helper.absoluteVec(BURST);
		ArmsNoise.addListener(listener);
		try {
			ArcCharge.burst(helper.getLevel(), burst, null);
		} finally {
			ArmsNoise.removeListener(listener);
		}
		helper.assertValueEqual(heard[0], 32f, "the burst is heard 32 blocks away");
		helper.assertValueEqual(at[0], BlockPos.containing(burst), "the noise is at the burst");
		helper.assertTrue(Stun.isStunned(husk), "the husk should be stunned");
		helper.assertValueEqual(husk.getHealth(), max, "husk health after the burst");
		helper.assertTrue(husk.getLastHurtByMob() == null, "the burst must not anger anything");
		helper.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() < 1e-6, "no knockback");
		helper.succeed();
	}

	@GameTest
	public void arcChargeRespectsLockout(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		Husk husk = husk(helper, 3.5, 2.5);
		helper.assertValueEqual(Stun.tryStun(husk, null), Stun.STUN_TICKS, "first stun");
		husk.removeEffect(Stun.STUNNED);
		helper.assertValueEqual(ArcCharge.burst(level, helper.absoluteVec(BURST), null), 0, "stunned inside the lockout");
		helper.assertTrue(!Stun.isStunned(husk), "the lockout holds against an Arc Charge");
		husk.setAttached(Stun.LOCKED_UNTIL, level.getGameTime());
		helper.assertValueEqual(ArcCharge.burst(level, helper.absoluteVec(BURST), null), 1, "stunned once the lockout is over");
		helper.succeed();
	}

	@GameTest
	public void arcChargeRecipeYieldsTwo(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
		helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("ashfall", "arc_charge"))).isPresent(),
				"recipe ashfall:arc_charge did not load");
		CraftingInput input = CraftingInput.of(3, 1, List.of(new ItemStack(SalvageItems.DUCT_TAPE), new ItemStack(ArmsItems.BATTERY),
				new ItemStack(ArmsItems.SCRAP_ELECTRONICS)));
		ItemStack result = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
		helper.assertTrue(result.is(ArmsItems.ARC_CHARGE) && result.getCount() == 2, "expected 2 Arc Charges, got " + result);
		helper.succeed();
	}
}
