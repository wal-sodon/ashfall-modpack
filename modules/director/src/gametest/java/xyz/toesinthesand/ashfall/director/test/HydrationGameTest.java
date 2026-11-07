package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.UseOnContext;
import xyz.toesinthesand.ashfall.director.hydration.RainCollectors;
import xyz.toesinthesand.ashfall.director.hydration.RainCollectorBlockEntity;
import xyz.toesinthesand.ashfall.director.hydration.RainCollectorBlock;
import xyz.toesinthesand.ashfall.director.hydration.SleepCost;
import xyz.toesinthesand.ashfall.director.hydration.ThirstData;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hydration on a real server: drinks, the bottle-on-water swap, purification recipes, activity drain,
 * the regeneration and sprint gates, dehydration damage and the death reset.
 * <p>
 * Uses its own survival mock player: vanilla's {@code makeMockServerPlayerInLevel} is hard-wired to
 * creative, which thirst deliberately ignores. Its connection is not ticked by the server, so only our
 * end-of-tick pass touches it; the synchronous tests finish before that pass ever runs.
 */
public class HydrationGameTest {
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		return survivalPlayer(helper, "thirsty-mock");
	}

	/** A survival mock with its own name, for tests that select the player by name in a command. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		ServerPlayer player = mockPlayer(helper, GameType.SURVIVAL, name);
		// the test server's default game mode may be creative; abilities come from it, not from gameMode()
		player.getAbilities().invulnerable = false;
		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.getAbilities().instabuild = false;
		return player;
	}

	/** A creative mock (vanilla's own helper is creative too, but deprecated for removal). */
	private static ServerPlayer creativePlayer(GameTestHelper helper) {
		return mockPlayer(helper, GameType.CREATIVE, "thirsty-mock");
	}

	private static ServerPlayer mockPlayer(GameTestHelper helper, GameType mode, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return mode;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	/** Thirst only drains off Peaceful; returns the old difficulty so the test can restore it. */
	private static Difficulty normal(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty old = server.getWorldData().getDifficulty();
		server.setDifficulty(Difficulty.NORMAL, true);
		return old;
	}

	@GameTest
	public void dirtyWaterQuenchesAndReturnsBottle(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(5, 0.0f, 0.0f));
			ItemStack drink = new ItemStack(SalvageItems.DIRTY_WATER);
			player.setItemInHand(InteractionHand.MAIN_HAND, drink);
			ItemStack left = drink.finishUsingItem(helper.getLevel(), player);
			ThirstData after = Hydration.get(player);
			helper.assertValueEqual(after.thirst(), 10, "thirst after dirty water");
			helper.assertValueEqual(after.saturation(), 1.0f, "saturation after dirty water");
			helper.assertTrue(left.is(Items.GLASS_BOTTLE), "dirty water leaves a glass bottle, got " + left);
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void bottledWaterIsCleanAndStillFood(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(5, 0.0f, 0.0f));
			player.getFoodData().setFoodLevel(10);
			ItemStack drink = new ItemStack(SalvageItems.BOTTLED_WATER);
			player.setItemInHand(InteractionHand.MAIN_HAND, drink);
			ItemStack left = drink.finishUsingItem(helper.getLevel(), player);
			ThirstData after = Hydration.get(player);
			helper.assertValueEqual(after.thirst(), 13, "thirst after bottled water");
			helper.assertValueEqual(after.saturation(), 4.0f, "saturation after bottled water");
			helper.assertValueEqual(player.getFoodData().getFoodLevel(), 12, "bottled water keeps its 2 nutrition");
			helper.assertTrue(left.is(Items.GLASS_BOTTLE), "bottled water leaves a glass bottle, got " + left);
			helper.assertTrue(player.getEffect(net.minecraft.world.effect.MobEffects.NAUSEA) == null, "clean water never nauseates");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void vanillaWaterBottleCountsAsDirty(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(5, 0.0f, 0.0f));
			ItemStack drink = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			player.setItemInHand(InteractionHand.MAIN_HAND, drink);
			drink.finishUsingItem(helper.getLevel(), player);
			helper.assertValueEqual(Hydration.get(player).thirst(), 10, "thirst after a vanilla water bottle");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void otherFoodDoesNotQuench(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(5, 0.0f, 0.0f));
			player.getFoodData().setFoodLevel(10);
			ItemStack bread = new ItemStack(Items.BREAD);
			player.setItemInHand(InteractionHand.MAIN_HAND, bread);
			bread.finishUsingItem(helper.getLevel(), player);
			helper.assertValueEqual(Hydration.get(player).thirst(), 5, "bread leaves thirst alone");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void bottleOnWaterSourceGivesDirtyWater(GameTestHelper helper) {
		helper.setBlock(1, 0, 1, Blocks.STONE);
		helper.setBlock(1, 1, 1, Blocks.WATER);
		BlockPos water = helper.absolutePos(new BlockPos(1, 1, 1));
		ServerPlayer player = survivalPlayer(helper);
		try {
			player.snapTo(water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 0.0f, 90.0f);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
			InteractionResult result = bottle.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(result instanceof InteractionResult.Success, "bottle use succeeds, got " + result);
			ItemStack filled = ((InteractionResult.Success) result).heldItemTransformedTo();
			helper.assertTrue(filled != null && filled.is(SalvageItems.DIRTY_WATER), "bottle filled with dirty water, got " + filled);
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void cookingPurifiesDirtyWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SingleRecipeInput input = new SingleRecipeInput(new ItemStack(SalvageItems.DIRTY_WATER));
		for (RecipeType<?> type : new RecipeType<?>[]{RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING}) {
			@SuppressWarnings("unchecked")
			RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe> cooking =
					(RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>) (RecipeType<?>) type;
			ItemStack out = level.recipeAccess().getRecipeFor(cooking, input, level)
					.map(holder -> holder.value().assemble(input))
					.orElse(ItemStack.EMPTY);
			helper.assertTrue(out.is(SalvageItems.BOTTLED_WATER), type + " turns dirty water into bottled water, got " + out);
		}
		helper.succeed();
	}

	@GameTest
	public void activityDrainsThirstAtOneAndAHalfTimesHunger(GameTestHelper helper) {
		Difficulty old = normal(helper);
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(20, 5.0f, 0.0f));
			player.causeFoodExhaustion(2.0f);
			helper.assertValueEqual(Hydration.get(player).exhaustion(), 3.0f, "thirst exhaustion after 2 hunger exhaustion");
		} finally {
			remove(player);
			helper.getLevel().getServer().setDifficulty(old, true);
		}
		helper.succeed();
	}

	@GameTest
	public void creativePlayersDoNotGetThirsty(GameTestHelper helper) {
		Difficulty old = normal(helper);
		ServerPlayer player = creativePlayer(helper);
		try {
			Hydration.set(player, new ThirstData(20, 5.0f, 0.0f));
			Hydration.onFoodExhaustion(player, 2.0f);
			Hydration.tick(player, 0);
			helper.assertValueEqual(Hydration.get(player).exhaustion(), 0.0f, "creative exhaustion");
		} finally {
			remove(player);
			helper.getLevel().getServer().setDifficulty(old, true);
		}
		helper.succeed();
	}

	@GameTest
	public void lowThirstBlocksNaturalRegeneration(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			player.setHealth(10.0f);
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5.0f);
			Hydration.set(player, ThirstData.DEFAULT.withThirst(6));
			for (int i = 0; i < 40; i++) {
				player.getFoodData().tick(player);
			}
			helper.assertValueEqual(player.getHealth(), 10.0f, "no regeneration at 6 thirst");

			Hydration.set(player, ThirstData.DEFAULT);
			for (int i = 0; i < 40; i++) {
				player.getFoodData().tick(player);
			}
			helper.assertTrue(player.getHealth() > 10.0f, "regeneration resumes at full thirst, health " + player.getHealth());
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void emptyThirstBlocksSprinting(GameTestHelper helper) throws ReflectiveOperationException {
		Method gate = Player.class.getDeclaredMethod("hasEnoughFoodToDoExhaustiveManoeuvres");
		gate.setAccessible(true);
		ServerPlayer player = survivalPlayer(helper);
		try {
			player.getFoodData().setFoodLevel(20);
			Hydration.set(player, ThirstData.DEFAULT.withThirst(1));
			helper.assertTrue((boolean) gate.invoke(player), "can sprint at 1 thirst");
			Hydration.set(player, ThirstData.DEFAULT.withThirst(0));
			helper.assertFalse((boolean) gate.invoke(player), "can not sprint at 0 thirst");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void emptyThirstHurtsWithDehydration(GameTestHelper helper) {
		Difficulty old = normal(helper);
		ServerPlayer player = survivalPlayer(helper);
		Hydration.set(player, new ThirstData(0, 0.0f, 0.0f));
		helper.succeedWhen(() -> {
			DamageSource last = player.getLastDamageSource();
			helper.assertTrue(player.getHealth() < player.getMaxHealth(), "dehydration hit landed");
			helper.assertTrue(last != null && last.is(Hydration.DEHYDRATION), "damage type is ashfall:dehydration, got " + last);
			remove(player);
			helper.getLevel().getServer().setDifficulty(old, true);
		});
	}

	@GameTest
	public void deathResetsThirstButPortalsKeepIt(GameTestHelper helper) {
		ServerPlayer dead = survivalPlayer(helper);
		ServerPlayer respawned = survivalPlayer(helper);
		ServerPlayer returned = survivalPlayer(helper);
		try {
			Hydration.set(dead, new ThirstData(3, 0.0f, 2.0f));
			// Fabric moves attachments in its AFTER_RESPAWN listener; fire it like PlayerList.respawn does
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(dead, respawned, false);
			helper.assertValueEqual(Hydration.get(respawned), ThirstData.DEFAULT, "thirst after death");
			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(dead, returned, true);
			helper.assertValueEqual(Hydration.get(returned).thirst(), 3, "thirst after leaving the End");
		} finally {
			remove(dead);
			remove(respawned);
			remove(returned);
		}
		helper.succeed();
	}

	@GameTest
	public void peacefulKeepsPlayersFull(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty old = server.getWorldData().getDifficulty();
		server.setDifficulty(Difficulty.PEACEFUL, true);
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(2, 0.0f, 0.0f));
			Hydration.tick(player, 1);
			helper.assertValueEqual(Hydration.get(player), ThirstData.DEFAULT, "thirst on Peaceful");
		} finally {
			remove(player);
			server.setDifficulty(old, true);
		}
		helper.succeed();
	}

	@GameTest
	public void dirtyWaterRollsNauseaAndInfection(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.applyDirtyWaterRisk(player, 0.99f, 0.99f);
			helper.assertTrue(player.getActiveEffects().isEmpty(), "high rolls give nothing, got " + player.getActiveEffects());
			helper.assertTrue(!Infection.isInfected(player), "a high infection roll leaves the player healthy");
			// 0.0 hits both chances. Infection is Ashfall's own state: it works without Contagion loaded, and
			// stage 1 adds no effect, so nausea stays the only effect.
			Hydration.applyDirtyWaterRisk(player, 0.0f, 0.0f);
			MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
			helper.assertTrue(nausea != null && nausea.getDuration() == Hydration.NAUSEA_TICKS, "10 s of nausea, got " + nausea);
			helper.assertValueEqual(player.getActiveEffects().size(), 1, "only nausea without Contagion");
			helper.assertValueEqual(Infection.stage(player), 1, "dirty water infects at stage 1");
			// the boundary: exactly the chance is a miss, just under is a hit
			player.removeAllEffects();
			Hydration.applyDirtyWaterRisk(player, Hydration.NAUSEA_CHANCE, 0.99f);
			helper.assertTrue(player.getEffect(MobEffects.NAUSEA) == null, "a roll of exactly 0.4 misses");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void thirstCommandGetsAndSetsForOpsOnly(GameTestHelper helper) throws CommandSyntaxException {
		MinecraftServer server = helper.getLevel().getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		// a name of its own: the player selector rejects bare UUIDs and other mocks share "thirsty-mock"
		ServerPlayer player = survivalPlayer(helper, "thirst-cmd");
		try {
			String target = player.getScoreboardName();
			CommandSourceStack op = server.createCommandSourceStack().withSuppressedOutput();
			helper.assertValueEqual(dispatcher.execute("thirst set " + target + " 4", op), 4, "set result");
			helper.assertValueEqual(Hydration.get(player).thirst(), 4, "thirst after /thirst set");
			helper.assertTrue(Hydration.get(player).saturation() <= 4.0f, "saturation capped at the new thirst");
			helper.assertValueEqual(dispatcher.execute("thirst get " + target, op), 4, "get result");

			boolean rejected = false;
			try {
				dispatcher.execute("thirst set " + target + " 21", op);
			} catch (CommandSyntaxException e) {
				rejected = true;
			}
			helper.assertTrue(rejected, "values above 20 are rejected");

			CommandSourceStack plain = player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS).withSuppressedOutput();
			boolean denied = false;
			try {
				dispatcher.execute("thirst set " + target + " 20", plain);
			} catch (CommandSyntaxException e) {
				denied = true;
			}
			helper.assertTrue(denied, "non-ops can not use /thirst");
			helper.assertValueEqual(Hydration.get(player).thirst(), 4, "thirst unchanged by a denied command");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void skippedNightCostsWaterAndFood(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			Hydration.set(player, new ThirstData(6, 0.0f, 0.0f));
			player.getFoodData().setFoodLevel(20);
			helper.assertTrue(SleepCost.problem(player) != null, "too thirsty to sleep at 6 thirst");
			Hydration.set(player, new ThirstData(20, 5.0f, 0.0f));
			player.getFoodData().setFoodLevel(6);
			helper.assertTrue(SleepCost.problem(player) != null, "too hungry to sleep at 6 food");
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5.0f);
			helper.assertTrue(SleepCost.problem(player) == null, "fed and watered players may sleep");
			SleepCost.charge(player);
			helper.assertValueEqual(Hydration.get(player).thirst(), 14, "thirst after a skipped night");
			helper.assertValueEqual(player.getFoodData().getFoodLevel(), 16, "food after a skipped night");
		} finally {
			remove(player);
		}
		ServerPlayer creative = creativePlayer(helper);
		try {
			Hydration.set(creative, new ThirstData(2, 0.0f, 0.0f));
			helper.assertTrue(SleepCost.problem(creative) == null, "creative players are never refused");
		} finally {
			remove(creative);
		}
		helper.succeed();
	}

	@GameTest
	public void rainCollectorFillsBottlesAndStaysInBunkers(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, RainCollectors.RAIN_COLLECTOR);
		RainCollectorBlockEntity collector = helper.getBlockEntity(pos, RainCollectorBlockEntity.class);
		for (int i = 0; i < RainCollectorBlockEntity.CAPACITY; i++) {
			helper.assertTrue(collector.addBottle(), "bottle " + (i + 1) + " fits");
		}
		helper.assertFalse(collector.addBottle(), "a fifth bottle does not fit");
		helper.assertFalse(collector.collect(false, RainCollectorBlockEntity.FILL_TICKS), "no rain, no water");
		helper.assertTrue(collector.collect(true, RainCollectorBlockEntity.FILL_TICKS), "a minute of rain fills a bottle");
		helper.assertBlockProperty(pos, RainCollectorBlock.FILLED, true);
		ItemStack out = collector.takeOut();
		helper.assertTrue(out.is(SalvageItems.BOTTLED_WATER) && out.getCount() == 1, "one clean bottle comes out, got " + out);
		helper.assertBlockProperty(pos, RainCollectorBlock.FILLED, false);
		helper.assertTrue(collector.takeOut().getCount() == 3, "then the three empty bottles");

		ServerPlayer player = survivalPlayer(helper);
		try {
			BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RainCollectors.ITEM));
			InteractionResult result = RainCollectors.ITEM.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false)));
			helper.assertValueEqual(result, InteractionResult.FAIL, "placing outside a Bunker Core area");
			helper.assertBlockNotPresent(RainCollectors.RAIN_COLLECTOR, new BlockPos(2, 1, 2));
		} finally {
			remove(player);
		}
		helper.succeed();
	}
}
