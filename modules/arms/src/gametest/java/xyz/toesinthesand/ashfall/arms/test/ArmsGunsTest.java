package xyz.toesinthesand.ashfall.arms.test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyItem;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.arms.gun.GunAttachments;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunState;
import xyz.toesinthesand.ashfall.arms.gun.GunStats;
import xyz.toesinthesand.ashfall.arms.gun.GunStatsManager;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Energy weapons on a real server: stats datapack, components, firing (energy, heat, lockout, noise),
 * automatic fire, reload, the Dampener recipe, bolt damage and headshots, recipes and loot.
 */
public class ArmsGunsTest {
	private static final List<String> GUNS = List.of("spark_pistol", "volt_sidearm", "pulse_carbine", "ion_rifle",
			"arc_magnum", "lancer_longrifle", "storm_repeater");

	private static Identifier ashfall(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static ItemStack pack(Item item, long energy) {
		return EnergyItems.withEnergy(new ItemStack(item), energy);
	}

	private static ItemStack loadedGun(Item gun, Item pack, long energy) {
		ItemStack stack = new ItemStack(gun);
		stack.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(pack(pack, energy)));
		return stack;
	}

	private static long loadedEnergy(ItemStack gun) {
		ItemStackTemplate pack = gun.get(ArmsComponents.LOADED_PACK);
		return pack == null ? -1 : EnergyItems.stored(pack);
	}

	/** Moves the last shot {@code ticks} into the past (keeping heat and credit) so the next shot is off cooldown. */
	private static void rewind(ItemStack gun, long ticks) {
		GunState s = gun.getOrDefault(ArmsComponents.GUN_STATE, GunState.EMPTY);
		gun.set(ArmsComponents.GUN_STATE, new GunState(s.heat(), s.lastShotTick() - ticks, s.locked(), s.energyCredit()));
	}

	private static Player shooter(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		player.setPos(pos.x, pos.y, pos.z);
		return player;
	}

	@GameTest
	public void statsLoadForEveryGun(GameTestHelper helper) {
		Map<Identifier, GunStats> stats = GunStatsManager.serverStats();
		for (String id : GUNS) {
			helper.assertTrue(stats.containsKey(ashfall(id)), "no stats loaded for ashfall:" + id);
			GunStats s = stats.get(ashfall(id));
			helper.assertValueEqual(s.heatCapacity(), 100f, id + " heat capacity");
			helper.assertValueEqual(s.headshotMultiplier(), 1.75f, id + " headshot multiplier");
			helper.assertValueEqual(s.range(), 96f, id + " range");
			helper.assertTrue(BuiltInRegistries.ITEM.getValue(ashfall(id)) instanceof EnergyGunItem, id + " is not an energy gun");
		}
		GunStats spark = stats.get(ashfall("spark_pistol"));
		helper.assertValueEqual(spark.damage(), 4f, "spark damage");
		helper.assertValueEqual(spark.ticksBetweenShots(), 4, "spark fire interval");
		helper.assertValueEqual(spark.boltColor(), 0xff5a3c, "spark colour");
		GunStats lancer = stats.get(ashfall("lancer_longrifle"));
		helper.assertValueEqual(lancer.damage(), 28f, "lancer damage");
		helper.assertValueEqual(lancer.energyPerShot(), 6f, "lancer energy per shot");
		helper.assertValueEqual(stats.get(ashfall("volt_sidearm")).energyPerShot(), 1.5f, "volt energy per shot");
		helper.assertTrue(((EnergyGunItem) ArmsItems.PULSE_CARBINE).isAutomatic(), "pulse carbine should be automatic");
		helper.assertTrue(((EnergyGunItem) ArmsItems.STORM_REPEATER).isAutomatic(), "storm repeater should be automatic");
		helper.assertTrue(!((EnergyGunItem) ArmsItems.ION_RIFLE).isAutomatic(), "ion rifle should be semi-automatic");
		helper.succeed();
	}

	@GameTest
	public void componentsAndEnergyItemsWork(GameTestHelper helper) {
		for (String id : List.of("gun_state", "loaded_pack", "attachments")) {
			helper.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(ashfall(id)), "component ashfall:" + id + " missing");
		}
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ashfall("energy_bolt")), "entity ashfall:energy_bolt missing");
		helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(ashfall("gun.fire.light")), "sound ashfall:gun.fire.light missing");

		// Components survive a save round trip.
		ItemStack gun = loadedGun(ArmsItems.ION_RIFLE, ArmsItems.CELL_PACK_STANDARD, 42);
		gun.set(ArmsComponents.GUN_STATE, new GunState(37.5f, 1234L, true, 0.5f));
		gun.set(ArmsComponents.ATTACHMENTS, GunAttachments.NONE.with(GunAttachments.DAMPENER));
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		Tag saved = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), gun).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), saved).getOrThrow();
		helper.assertTrue(ItemStack.isSameItemSameComponents(gun, loaded), "gun components did not round-trip: " + saved);
		helper.assertValueEqual(loadedEnergy(loaded), 42L, "loaded pack energy after round trip");

		// Packs and cells are Team Reborn Energy items with the spec capacities, discoverable through the API.
		Map<Item, Long> capacities = Map.of(ArmsItems.CELL_PACK_SMALL, 30L, ArmsItems.CELL_PACK_STANDARD, 60L,
				ArmsItems.CELL_PACK_HEAVY, 120L, ArmsItems.ENERGY_CELL, 240L);
		for (Map.Entry<Item, Long> e : capacities.entrySet()) {
			ItemStack stack = new ItemStack(e.getKey());
			EnergyStorage storage = EnergyStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
			helper.assertTrue(storage != null, BuiltInRegistries.ITEM.getKey(e.getKey()) + " exposes no EnergyStorage");
			helper.assertValueEqual(storage.getCapacity(), e.getValue(), BuiltInRegistries.ITEM.getKey(e.getKey()) + " capacity");
			helper.assertValueEqual(storage.getAmount(), 0L, "new " + BuiltInRegistries.ITEM.getKey(e.getKey()) + " should be empty");
			helper.assertTrue(stack.isBarVisible(), BuiltInRegistries.ITEM.getKey(e.getKey()) + " shows no charge bar");
		}
		helper.assertValueEqual(new ItemStack(ArmsItems.CELL_PACK_STANDARD).getBarWidth(), 0, "empty pack bar");
		helper.assertValueEqual(pack(ArmsItems.CELL_PACK_STANDARD, 30).getBarWidth(), 7, "half pack bar (13 * 0.5, rounded)");
		helper.succeed();
	}

	@GameTest
	public void newGunIsEmptyAndClicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		ItemStack gun = new ItemStack(ArmsItems.SPARK_PISTOL);
		helper.assertTrue(!gun.has(ArmsComponents.LOADED_PACK), "a new gun must be empty");
		helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.EMPTY, "fire with no pack");
		helper.assertTrue(player.getCooldowns().isOnCooldown(gun), "a dry click should pause the trigger");

		ItemStack drained = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 0);
		helper.assertValueEqual(GunLogic.tryFire(level, player, drained), GunLogic.FireResult.EMPTY, "fire with a drained pack");
		helper.assertTrue(helper.getEntities(ArmsEntities.ENERGY_BOLT).isEmpty(), "an empty gun spawned a bolt");
		helper.succeed();
	}

	@GameTest
	public void firingSpendsEnergyAddsHeatAndMakesNoise(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		List<Float> noises = new ArrayList<>();
		ArmsNoise.Listener listener = (l, pos, radius) -> noises.add(radius);
		ArmsNoise.addListener(listener);
		try {
			ItemStack gun = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
			helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.FIRED, "first shot");
			helper.assertValueEqual(loadedEnergy(gun), 29L, "energy after one shot");
			GunState state = gun.get(ArmsComponents.GUN_STATE);
			helper.assertTrue(state != null && Math.abs(state.heat() - 12f) < 1e-3, "heat after one shot: " + state);
			helper.assertValueEqual(state.lastShotTick(), level.getGameTime(), "last shot tick");
			helper.assertValueEqual(noises, List.of(24f), "noise per shot");
			helper.assertTrue(player.getCooldowns().isOnCooldown(gun), "semi gun should be on cooldown after a shot");
			// Same tick again: rate-limited by RPM.
			helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.COOLDOWN, "second shot, same tick");

			List<EnergyBolt> bolts = helper.getEntities(ArmsEntities.ENERGY_BOLT);
			helper.assertValueEqual(bolts.size(), 1, "bolts spawned");
			helper.assertValueEqual(bolts.getFirst().getColor(), 0xff5a3c, "bolt colour");
			helper.assertValueEqual(bolts.getFirst().getDamage(), 4f, "bolt damage");

			// Volt sidearm: 1.5 energy per shot alternates 2, 1.
			ItemStack volt = loadedGun(ArmsItems.VOLT_SIDEARM, ArmsItems.CELL_PACK_SMALL, 30);
			GunLogic.tryFire(level, player, volt);
			helper.assertValueEqual(loadedEnergy(volt), 28L, "volt energy after shot 1");
			rewind(volt, 100);
			GunLogic.tryFire(level, player, volt);
			helper.assertValueEqual(loadedEnergy(volt), 27L, "volt energy after shot 2");

			// Dampener: noise x0.4, damage x0.9.
			noises.clear();
			ItemStack quiet = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
			quiet.set(ArmsComponents.ATTACHMENTS, GunAttachments.NONE.with(GunAttachments.DAMPENER));
			GunLogic.tryFire(level, player, quiet);
			helper.assertTrue(noises.size() == 1 && Math.abs(noises.getFirst() - 24f * 0.4f) < 1e-3, "dampened noise: " + noises);
			boolean dampenedBolt = helper.getEntities(ArmsEntities.ENERGY_BOLT).stream()
					.anyMatch(b -> Math.abs(b.getDamage() - 3.6f) < 1e-3);
			helper.assertTrue(dampenedBolt, "dampened bolt should deal 4 x 0.9 damage");
		} finally {
			ArmsNoise.removeListener(listener);
		}
		helper.succeed();
	}

	@GameTest
	public void overheatLocksUntilCold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		List<Float> noises = new ArrayList<>();
		ArmsNoise.Listener listener = (l, pos, radius) -> noises.add(radius);
		ArmsNoise.addListener(listener);
		try {
			long now = level.getGameTime();
			ItemStack gun = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
			gun.set(ArmsComponents.GUN_STATE, new GunState(99f, now - 4, false, 0f));
			helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.FIRED, "the overheating shot still fires");
			GunState state = gun.get(ArmsComponents.GUN_STATE);
			helper.assertTrue(state.locked() && state.heat() == 100f, "gun should lock at 100: " + state);
			helper.assertValueEqual(noises, List.of(24f, 36f), "shot noise then louder overheat noise");

			rewind(gun, 4);
			helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.LOCKED, "locked gun");
			helper.assertValueEqual(loadedEnergy(gun), 29L, "a locked gun spends no energy");

			// Locked cooling is 0.8 x 25 = 20/s: still locked just before 5 s, free at 5 s.
			float cool = GunStatsManager.serverStats().get(ashfall("spark_pistol")).coolPerSecond();
			long lockedAt = state.lastShotTick();
			helper.assertTrue(state.lockedAt(lockedAt + 99, cool), "should still be locked after 4.95 s");
			helper.assertTrue(!state.lockedAt(lockedAt + 100, cool), "should unlock after 5 s");
		} finally {
			ArmsNoise.removeListener(listener);
		}
		helper.succeed();
	}

	@GameTest
	public void automaticGunsFireWhileHeld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		ItemStack gun = loadedGun(ArmsItems.PULSE_CARBINE, ArmsItems.CELL_PACK_STANDARD, 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		ArmsItems.PULSE_CARBINE.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.isUsingItem(), "auto gun should keep being used while held");
		helper.assertValueEqual(loadedEnergy(gun), 2L, "first auto shot");
		// Not yet due (same tick): nothing happens and it keeps firing.
		ArmsItems.PULSE_CARBINE.onUseTick(level, player, gun, 100);
		helper.assertValueEqual(loadedEnergy(gun), 2L, "no shot before the RPM interval");
		rewind(gun, 4);
		ArmsItems.PULSE_CARBINE.onUseTick(level, player, gun, 99);
		helper.assertValueEqual(loadedEnergy(gun), 0L, "second auto shot");
		rewind(gun, 4);
		ArmsItems.PULSE_CARBINE.onUseTick(level, player, gun, 98);
		helper.assertTrue(!player.isUsingItem(), "an empty auto gun should stop firing");
		helper.assertValueEqual(helper.getEntities(ArmsEntities.ENERGY_BOLT).size(), 2, "bolts from auto fire");
		helper.succeed();
	}

	@GameTest
	public void semiGunsFireOncePerClick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		ItemStack gun = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		ArmsItems.SPARK_PISTOL.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(loadedEnergy(gun), 29L, "the click fired");
		// Staying "in use" is what stops vanilla from re-clicking every 4 ticks while the button is held.
		helper.assertTrue(player.isUsingItem(), "a held semi gun should stay in use until released");
		rewind(gun, 100);
		ArmsItems.SPARK_PISTOL.onUseTick(level, player, gun, 100);
		helper.assertValueEqual(loadedEnergy(gun), 29L, "holding a semi gun must not fire again");

		// Holding any trigger walks at a usable speed, not the vanilla 20% item-use crawl.
		for (Item item : ArmsItems.GUNS) {
			UseEffects effects = new ItemStack(item).get(DataComponents.USE_EFFECTS);
			helper.assertTrue(effects != null && effects.speedMultiplier() >= 0.5f,
					BuiltInRegistries.ITEM.getKey(item) + " slows its user too much while firing: " + effects);
		}
		helper.succeed();
	}

	@GameTest
	public void autoFireRespectsTheReloadCooldown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		ItemStack gun = loadedGun(ArmsItems.STORM_REPEATER, ArmsItems.CELL_PACK_HEAVY, 100);
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		player.getCooldowns().addCooldown(gun, GunLogic.RELOAD_TICKS);
		ArmsItems.STORM_REPEATER.onUseTick(level, player, gun, 100);
		helper.assertValueEqual(loadedEnergy(gun), 100L, "a held trigger must not fire through the reload cooldown");
		helper.succeed();
	}

	@GameTest
	public void reloadSwapsInTheFullestMatchingPack(GameTestHelper helper) {
		Player player = shooter(helper);
		ItemStack gun = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 10);
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		player.getInventory().setItem(3, pack(ArmsItems.CELL_PACK_SMALL, 5));
		player.getInventory().setItem(4, pack(ArmsItems.CELL_PACK_SMALL, 25));
		player.getInventory().setItem(5, pack(ArmsItems.CELL_PACK_STANDARD, 60)); // wrong size for a pistol
		player.getInventory().setItem(6, pack(ArmsItems.CELL_PACK_SMALL, 15));

		helper.assertTrue(GunLogic.heldGun(player) == gun, "held gun lookup");
		helper.assertTrue(GunLogic.reload(player, gun), "reload should swap");
		helper.assertValueEqual(loadedEnergy(gun), 25L, "fullest small pack loaded");
		helper.assertTrue(player.getInventory().getItem(4).isEmpty(), "the loaded pack left the inventory");
		helper.assertTrue(player.getCooldowns().isOnCooldown(gun), "reload puts the gun on cooldown");
		boolean oldBack = false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack s = player.getInventory().getItem(slot);
			oldBack |= s.is(ArmsItems.CELL_PACK_SMALL) && SimpleEnergyItem.getStoredEnergyUnchecked(s) == 10;
		}
		helper.assertTrue(oldBack, "the old pack went back into the inventory");
		helper.assertTrue(!GunLogic.reload(player, gun), "no fuller pack left, so no swap");
		helper.assertValueEqual(loadedEnergy(gun), 25L, "still the 25 pack");

		ItemStack rifle = new ItemStack(ArmsItems.ION_RIFLE);
		helper.assertTrue(GunLogic.reload(player, rifle), "an empty rifle loads a standard pack");
		helper.assertValueEqual(loadedEnergy(rifle), 60L, "standard pack loaded");
		helper.succeed();
	}

	@GameTest
	public void craftingRecipes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getServer().getRecipeManager();
		for (String id : List.of("spark_pistol", "volt_sidearm", "pulse_carbine", "cell_pack_small", "cell_pack_standard",
				"cell_pack_heavy", "dampener", "attach_dampener")) {
			helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, ashfall(id))).isPresent(), "recipe ashfall:" + id + " did not load");
		}
		ItemStack s = new ItemStack(SalvageItems.SCRAP_METAL);
		ItemStack e = new ItemStack(ArmsItems.SCRAP_ELECTRONICS);
		ItemStack t = new ItemStack(SalvageItems.DUCT_TAPE);
		ItemStack i = new ItemStack(ArmsItems.ALLOY_PLATE);
		ItemStack x = ItemStack.EMPTY;
		ItemStack spark = craft(helper, recipes, CraftingInput.of(3, 3, List.of(s, s, e, t, i, x, s, x, x)));
		helper.assertTrue(spark.is(ArmsItems.SPARK_PISTOL), "spark pistol recipe crafted " + spark);
		helper.assertTrue(!spark.has(ArmsComponents.LOADED_PACK), "a crafted gun is empty");

		ItemStack c = new ItemStack(ArmsItems.WIRE_SPOOL);
		ItemStack r = new ItemStack(ArmsItems.WIRE_SPOOL);
		ItemStack small = craft(helper, recipes, CraftingInput.of(3, 2, List.of(c, r, c, x, e, x)));
		helper.assertTrue(small.is(ArmsItems.CELL_PACK_SMALL) && SimpleEnergyItem.getStoredEnergyUnchecked(small) == 0,
				"small pack recipe should craft an empty pack, got " + small);

		ItemStack b = new ItemStack(ArmsItems.BATTERY);
		ItemStack cell = craft(helper, recipes, CraftingInput.of(3, 3, List.of(x, c, x, c, b, c, x, c, x)));
		helper.assertValueEqual(SimpleEnergyItem.getStoredEnergyUnchecked(cell), 240L, "a crafted energy cell is full");

		// Dampener: keeps the gun's components and refuses a second dampener.
		ItemStack gun = loadedGun(ArmsItems.ARC_MAGNUM, ArmsItems.CELL_PACK_SMALL, 17);
		ItemStack dampener = new ItemStack(ArmsItems.DAMPENER);
		ItemStack fitted = craft(helper, recipes, CraftingInput.of(2, 1, List.of(dampener, gun)));
		helper.assertTrue(fitted.is(ArmsItems.ARC_MAGNUM), "dampener recipe should return the gun, got " + fitted);
		helper.assertTrue(fitted.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE).has(GunAttachments.DAMPENER),
				"result lacks the dampener attachment");
		helper.assertValueEqual(loadedEnergy(fitted), 17L, "dampener recipe kept the loaded pack");
		helper.assertTrue(craft(helper, recipes, CraftingInput.of(2, 1, List.of(fitted, dampener))).isEmpty(),
				"a gun can't take two dampeners");
		helper.assertTrue(craft(helper, recipes, CraftingInput.of(3, 1, List.of(gun, dampener, dampener))).isEmpty(),
				"gun + two dampeners must not match");
		helper.succeed();
	}

	private static ItemStack craft(GameTestHelper helper, RecipeManager recipes, CraftingInput input) {
		return recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}

	@GameTest(maxTicks = 60)
	public void boltsDamageAndHeadshotsHitHarder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pig body = helper.spawn(EntityTypes.PIG, new Vec3(1.5, 2, 5.5));
		Pig head = helper.spawn(EntityTypes.PIG, new Vec3(4.5, 2, 5.5));
		body.setNoAi(true);
		head.setNoAi(true);
		float max = body.getMaxHealth();

		fireAt(level, helper.absoluteVec(new Vec3(1.5, 2, 1.5)), body, body.getY() + 0.25);
		EnergyBolt headBolt = fireAt(level, helper.absoluteVec(new Vec3(4.5, 2, 1.5)), head, head.getEyeY() - 0.02);

		helper.succeedWhen(() -> {
			helper.assertTrue(Math.abs(body.getHealth() - (max - 4f)) < 1e-3, "body shot: health " + body.getHealth());
			helper.assertTrue(Math.abs(head.getHealth() - (max - 7f)) < 1e-3, "headshot (4 x 1.75): health " + head.getHealth());
			helper.assertTrue(headBolt.isRemoved() && headBolt.lastHitWasHeadshot(), "head bolt should register a headshot");
		});
	}

	private static EnergyBolt fireAt(ServerLevel level, Vec3 from, Pig target, double y) {
		// The bolt's box is 0.25 tall and its position is the bottom: centre it on the aim height.
		EnergyBolt bolt = EnergyBolt.create(level, null, new Vec3(from.x, y - 0.125, from.z), 4f, 1.75f, 96f, 0x3cc8ff);
		bolt.shoot(target.getX() - from.x, 0, target.getZ() - from.z, 1.0f, 0f);
		level.addFreshEntity(bolt);
		return bolt;
	}

	@GameTest
	public void gunsAndPacksAreInTheTabAndLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ArmsItems.ASHFALL_TAB);
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
		for (Item item : ArmsItems.GUNS) {
			helper.assertTrue(tab.contains(new ItemStack(item)), BuiltInRegistries.ITEM.getKey(item) + " not in the Ashfall tab");
		}
		for (Item item : ArmsItems.CELL_PACKS) {
			helper.assertTrue(tab.contains(new ItemStack(item)), BuiltInRegistries.ITEM.getKey(item) + " (empty) not in the tab");
			helper.assertTrue(tab.contains(ArmsItems.charged(item)), BuiltInRegistries.ITEM.getKey(item) + " (charged) not in the tab");
		}
		helper.assertTrue(tab.contains(new ItemStack(ArmsItems.DAMPENER)), "dampener not in the tab");

		HolderLookup.Provider registries = level.getServer().reloadableRegistries().lookup();
		String armory = encode(registries, Identifier.fromNamespaceAndPath("ashfall_cache", "police_armory"));
		for (String id : List.of("lancer_longrifle", "storm_repeater", "arc_magnum", "ion_rifle", "cell_pack_heavy")) {
			helper.assertTrue(armory.contains("ashfall:" + id), "police_armory lacks " + id);
		}
		String police = encode(registries, ashfall("buildings/police"));
		helper.assertTrue(police.contains("team_reborn_energy:energy"), "police packs are never partly charged");
		// The four rare blasters are loot-only and high-tier only: boss drops, the police armory cache, grid
		// reward caches and Wreck crash sites, never ordinary building loot.
		for (Identifier table : List.of(ashfall("chests/crash_site"), Identifier.fromNamespaceAndPath("ashfall_cache", "wreck_hull"),
				Identifier.fromNamespaceAndPath("ashfall_cache", "grid_reward_mission"), ashfall("entities/boss/juggernaut"))) {
			String text = encode(registries, table);
			helper.assertTrue(text.contains("ashfall:blasters/rare") || text.contains("ashfall:lancer_longrifle"),
					table + " lacks a rare blaster");
		}
		for (String building : List.of("police", "electronics", "warehouse")) {
			helper.assertTrue(encode(registries, ashfall("buildings/" + building)).contains("ashfall:cell_pack_"),
					"buildings/" + building + " lacks cell packs");
		}
		for (String building : List.of("police", "electronics", "warehouse", "home", "shop", "generic")) {
			String text = encode(registries, ashfall("buildings/" + building));
			for (String rare : List.of("lancer_longrifle", "storm_repeater", "arc_magnum", "ion_rifle")) {
				helper.assertTrue(!text.contains("ashfall:" + rare) && !text.contains("ashfall:blasters/rare"),
						"buildings/" + building + " must not hold the loot-only " + rare);
			}
		}
		helper.succeed();
	}

	private static String encode(HolderLookup.Provider registries, Identifier id) {
		LootTable table = registries.get(ResourceKey.create(Registries.LOOT_TABLE, id)).orElseThrow().value();
		JsonElement json = LootTable.DIRECT_CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), table).getOrThrow();
		return json.toString();
	}
}
