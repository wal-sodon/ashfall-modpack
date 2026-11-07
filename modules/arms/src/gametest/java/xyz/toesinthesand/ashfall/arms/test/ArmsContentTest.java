package xyz.toesinthesand.ashfall.arms.test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.director.haven.HavenBlocks;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Content checks on a real server: the items exist and show up in the Ashfall tab, the survival items
 * behave (food, drink, fire starter, fuel), the recipes load and craft, every building profile parsed,
 * and every loot table a profile points at loads and rolls. A typo in a JSON item id
 * makes the whole table fail to load, which these tests surface as "missing" instead of silent empty
 * chests in game.
 */
public class ArmsContentTest {
	/** Items whose behaviour is still to come: they must carry the "Not usable yet" tooltip. */
	private static final List<String> ITEMS = List.of("scrap_electronics", "weapon_parts",
			"focusing_lens", "resonance_crystal");
	/**
	 * Everyday survival salvage (salvage module, arms' battery, director's treatments): usable now, so no
	 * placeholder tooltip.
	 */
	private static final List<String> SURVIVAL_ITEMS = List.of("canned_food", "bottled_water", "cloth", "duct_tape",
			"scrap_metal", "glass_shard", "matches", "lighter_fluid", "satchel", "rain_collector", "battery", "alloy_plate", "wire_spool", "bandage", "antibiotics",
			"scavenger_hood", "scavenger_jacket", "scavenger_trousers", "scavenger_boots", "hazmat_mask", "hazmat_suit",
			"hazmat_leggings", "hazmat_boots", "riot_helmet", "riot_vest", "riot_leggings", "riot_boots");
	private static final List<String> RECIPES = List.of("bandage_from_cloth", "iron_nugget_from_scrap_metal",
			"cloth_from_string", "cloth_from_wool", "energy_cell", "glass_from_shards",
			"scavenger_hood", "scavenger_jacket", "scavenger_trousers", "scavenger_boots", "satchel", "rain_collector",
			"pipe_wrench", "scrap_machete", "rebar_spear", "nail_launcher", "nails", "bottle_lure", "arc_charge", "crowbar", "spray_can");
	/** Buildings where survival salvage must be the bulk of the loot (vanilla items under a quarter of the weight). */
	private static final double MAX_VANILLA_SHARE = 0.25;
	private static final Set<String> PROFILES = Set.of("generic", "home", "shop", "grocery", "pharmacy", "clinic",
			"hospital", "police", "fire_station", "government", "worship", "restaurant", "bar", "office", "bank",
			"school", "hotel", "museum", "warehouse", "workshop", "electronics", "brewery", "gym", "station", "fuel");
	private static final List<Identifier> CACHE_TABLES = List.of(
			Identifier.fromNamespaceAndPath("ashfall_cache", "police_armory"),
			Identifier.fromNamespaceAndPath("ashfall_cache", "hospital_supply"));
	private static final int ROLLS = 200;

	private static Identifier ashfall(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	@GameTest
	public void itemsRegisteredAndInAshfallTab(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ArmsItems.ASHFALL_TAB);
		helper.assertTrue(tab != null, "creative tab ashfall:main is not registered");
		helper.assertTrue(tab.getDisplayName().getContents().toString().contains("itemGroup.ashfall.main"),
				"tab is not titled itemGroup.ashfall.main");
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));

		for (String path : ITEMS) {
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ashfall(path)), "item ashfall:" + path + " is not registered");
			Item item = BuiltInRegistries.ITEM.getValue(ashfall(path));
			helper.assertTrue(item != Items.AIR, "item ashfall:" + path + " resolves to air");
			helper.assertTrue(tab.contains(new ItemStack(item)), "item ashfall:" + path + " is not in the Ashfall tab");

			List<Component> tooltip = new ArrayList<>();
			item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, tooltip::add, TooltipFlag.NORMAL);
			// The old placeholder line is gone (rc15): these are all crafting materials now.
			helper.assertTrue(tooltip.stream().noneMatch(c -> c.getContents().toString().contains("tooltip.ashfall.not_usable_yet")),
					"ashfall:" + path + " still says 'Not usable yet'");
		}
		for (String path : SURVIVAL_ITEMS) {
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ashfall(path)), "item ashfall:" + path + " is not registered");
			Item item = BuiltInRegistries.ITEM.getValue(ashfall(path));
			helper.assertTrue(item != Items.AIR, "item ashfall:" + path + " resolves to air");
			helper.assertTrue(tab.contains(new ItemStack(item)), "item ashfall:" + path + " is not in the Ashfall tab");

			List<Component> tooltip = new ArrayList<>();
			item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, tooltip::add, TooltipFlag.NORMAL);
			helper.assertTrue(tooltip.stream().noneMatch(c -> c.getContents().toString().contains("tooltip.ashfall.not_usable_yet")),
					"ashfall:" + path + " is usable but still says 'Not usable yet'");
		}
		helper.assertTrue(new ItemStack(ArmsItems.RESONANCE_CRYSTAL).getRarity() == Rarity.EPIC, "resonance crystal is not EPIC");
		helper.succeed();
	}

	@GameTest
	public void survivalItemComponents(GameTestHelper helper) {
		ItemStack canned = new ItemStack(SalvageItems.CANNED_FOOD);
		FoodProperties cannedFood = canned.get(DataComponents.FOOD);
		helper.assertTrue(cannedFood != null && cannedFood.nutrition() == 6, "canned food should restore 6 hunger");
		helper.assertTrue(cannedFood != null && Math.abs(cannedFood.saturation() - 6 * 0.6f * 2) < 1e-4,
				"canned food saturation modifier should be 0.6");
		Consumable cannedUse = canned.get(DataComponents.CONSUMABLE);
		helper.assertTrue(cannedUse != null && cannedUse.animation() == ItemUseAnimation.EAT, "canned food is not eaten");
		helper.assertTrue(canned.get(DataComponents.USE_REMAINDER) == null, "canned food must leave nothing behind");

		ItemStack water = new ItemStack(SalvageItems.BOTTLED_WATER);
		FoodProperties waterFood = water.get(DataComponents.FOOD);
		helper.assertTrue(waterFood != null && waterFood.nutrition() == 2 && waterFood.canAlwaysEat(),
				"bottled water should restore 2 hunger and always be drinkable");
		Consumable waterUse = water.get(DataComponents.CONSUMABLE);
		helper.assertTrue(waterUse != null && waterUse.animation() == ItemUseAnimation.DRINK, "bottled water is not drunk");
		UseRemainder bottle = water.get(DataComponents.USE_REMAINDER);
		helper.assertTrue(bottle != null && bottle.convertInto().item().value() == Items.GLASS_BOTTLE,
				"bottled water must leave a glass bottle");

		ItemStack matches = new ItemStack(SalvageItems.MATCHES);
		helper.assertTrue(SalvageItems.MATCHES instanceof FlintAndSteelItem, "matches are not a fire starter");
		helper.assertTrue(matches.getMaxDamage() == SalvageItems.MATCHES_USES, "matches should have " + SalvageItems.MATCHES_USES + " uses");

		helper.assertTrue(new ItemStack(SalvageItems.LIGHTER_FLUID).has(DataComponents.COOKING_FUEL), "lighter fluid is not a fuel");
		helper.assertTrue(SalvageItems.LIGHTER_FLUID instanceof FlintAndSteelItem, "lighter fluid is not a fire starter");
		helper.assertTrue(new ItemStack(SalvageItems.LIGHTER_FLUID).getMaxDamage() == SalvageItems.LIGHTER_FLUID_USES,
				"lighter fluid should have " + SalvageItems.LIGHTER_FLUID_USES + " uses");
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.CONTEXT_INT_PROVIDER)
				.get(SalvageItems.LIGHTER_FLUID_BURN_TIME).isPresent(), "lighter fluid burn time provider did not load");
		helper.succeed();
	}

	@GameTest
	public void matchesLightACampfire(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack matches = new ItemStack(SalvageItems.MATCHES);
		player.setItemInHand(InteractionHand.MAIN_HAND, matches);
		BlockPos absolute = helper.absolutePos(pos);
		SalvageItems.MATCHES.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
		helper.assertBlockProperty(pos, CampfireBlock.LIT, true);
		helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "lighting a campfire should use up one match");
		helper.succeed();
	}

	@GameTest
	public void lighterFluidLightsACampfire(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SalvageItems.LIGHTER_FLUID));
		BlockPos absolute = helper.absolutePos(pos);
		SalvageItems.LIGHTER_FLUID.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
		helper.assertBlockProperty(pos, CampfireBlock.LIT, true);
		helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "lighting a campfire should use one squirt");
		helper.succeed();
	}

	@GameTest
	public void bottleLureIsLoudWhereItLands(GameTestHelper helper) {
		float[] heard = {0f};
		BlockPos[] at = {null};
		xyz.toesinthesand.ashfall.arms.gun.ArmsNoise.Listener listener = (level, pos, radius) -> {
			heard[0] = radius;
			at[0] = pos;
		};
		xyz.toesinthesand.ashfall.arms.gun.ArmsNoise.addListener(listener);
		BlockPos landing = helper.absolutePos(new BlockPos(3, 1, 3));
		try {
			xyz.toesinthesand.ashfall.arms.entity.BottleLure.shatter(helper.getLevel(), landing);
		} finally {
			xyz.toesinthesand.ashfall.arms.gun.ArmsNoise.removeListener(listener);
		}
		helper.assertValueEqual(heard[0], xyz.toesinthesand.ashfall.arms.entity.BottleLure.NOISE_RADIUS, "the lure is loud");
		helper.assertValueEqual(at[0], landing, "the noise is where it lands");
		helper.succeed();
	}

	@GameTest
	public void meleeWeaponsAreScrapRepairable(GameTestHelper helper) {
		for (Item weapon : List.of(ArmsItems.PIPE_WRENCH, ArmsItems.SCRAP_MACHETE, ArmsItems.REBAR_SPEAR)) {
			ItemStack stack = new ItemStack(weapon);
			helper.assertTrue(stack.isDamageableItem(), weapon + " wears out");
			helper.assertTrue(stack.isValidRepairItem(new ItemStack(SalvageItems.SCRAP_METAL)), weapon + " is repaired with scrap metal");
			helper.assertTrue(stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
					net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
					.anyMatch(m -> m.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)), weapon + " has attack damage");
		}
		helper.assertTrue(new ItemStack(ArmsItems.SCRAP_MACHETE).getMaxDamage() < new ItemStack(ArmsItems.PIPE_WRENCH).getMaxDamage(),
				"the machete wears out fastest");
		helper.succeed();
	}

	@GameTest
	public void nailLauncherUsesNailsAndIsQuiet(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
		ItemStack launcher = new ItemStack(ArmsItems.NAIL_LAUNCHER);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, launcher);
		helper.assertValueEqual(launcher.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND),
				net.minecraft.world.InteractionResult.FAIL, "no nails, no shot");
		player.getInventory().add(new ItemStack(ArmsItems.NAILS, 3));
		float[] heard = {0f};
		xyz.toesinthesand.ashfall.arms.gun.ArmsNoise.Listener listener = (level, pos, radius) -> heard[0] = Math.max(heard[0], radius);
		xyz.toesinthesand.ashfall.arms.gun.ArmsNoise.addListener(listener);
		try {
			launcher.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
		} finally {
			xyz.toesinthesand.ashfall.arms.gun.ArmsNoise.removeListener(listener);
		}
		helper.assertValueEqual(player.getInventory().countItem(ArmsItems.NAILS), 2, "one nail per shot");
		helper.assertValueEqual(heard[0], xyz.toesinthesand.ashfall.arms.item.NailLauncherItem.NOISE_RADIUS, "barely heard");
		helper.assertFalse(helper.getLevel().getEntitiesOfClass(xyz.toesinthesand.ashfall.arms.entity.Nail.class,
				player.getBoundingBox().inflate(4)).isEmpty(), "a nail flies");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void lighterFluidFuelsAFurnace(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.FURNACE);
		FurnaceBlockEntity furnace = helper.getBlockEntity(pos, FurnaceBlockEntity.class);
		furnace.setItem(0, new ItemStack(Items.RAW_IRON));
		furnace.setItem(1, new ItemStack(SalvageItems.LIGHTER_FLUID));
		helper.succeedWhen(() -> {
			helper.assertBlockProperty(pos, AbstractFurnaceBlock.LIT, true);
			helper.assertTrue(furnace.getItem(1).isEmpty(), "the furnace did not burn the lighter fluid");
		});
	}

	@GameTest
	public void recipesLoadAndCraft(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getServer().getRecipeManager();
		for (String id : RECIPES) {
			helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, ashfall(id))).isPresent(),
					"recipe ashfall:" + id + " did not load");
		}

		// energy cell = _C_ / CBC / _C_ (10.2.2: four wire spools, was eight)
		ItemStack c = new ItemStack(ArmsItems.WIRE_SPOOL);
		ItemStack b = new ItemStack(ArmsItems.BATTERY);
		ItemStack o = ItemStack.EMPTY;
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(o, c, o, c, b, c, o, c, o)), ArmsItems.ENERGY_CELL, 1);
		assertCrafts(helper, recipes, CraftingInput.of(2, 1, List.of(new ItemStack(SalvageItems.CLOTH), new ItemStack(SalvageItems.CLOTH))),
				DirectorItems.BANDAGE, 1);
		// the light armour set is the one crafted from salvage: hood = CCC / CTC
		ItemStack cl = new ItemStack(SalvageItems.CLOTH), tape = new ItemStack(SalvageItems.DUCT_TAPE);
		assertCrafts(helper, recipes, CraftingInput.of(3, 2, List.of(cl, cl, cl, cl, tape, cl)), DirectorItems.SCAVENGER_HOOD, 1);
		ItemStack str = new ItemStack(Items.STRING), none = ItemStack.EMPTY;
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(none, str, none, cl, tape, cl, cl, cl, cl)), SalvageItems.SATCHEL, 1);
		assertCrafts(helper, recipes, CraftingInput.of(2, 1, List.of(new ItemStack(SalvageItems.SCRAP_METAL), new ItemStack(SalvageItems.SCRAP_METAL))),
				Items.IRON_NUGGET, 3);
		ItemStack ingot = new ItemStack(Items.IRON_INGOT), scrap = new ItemStack(SalvageItems.SCRAP_METAL);
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ingot, ItemStack.EMPTY, ingot,
				ItemStack.EMPTY, scrap, ItemStack.EMPTY, ItemStack.EMPTY)), ArmsItems.CROWBAR, 1);
		assertCrafts(helper, recipes, CraftingInput.of(3, 1, List.of(scrap, scrap, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"))))), DirectorItems.SPRAY_CAN, 1);
		assertCrafts(helper, recipes, CraftingInput.of(1, 1, List.of(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_wool"))))), SalvageItems.CLOTH, 3);
		ItemStack string = new ItemStack(Items.STRING);
		assertCrafts(helper, recipes, CraftingInput.of(2, 2, List.of(string, string, string, string)), SalvageItems.CLOTH, 1);

		// Havens: the Bunker Core takes an Energy Cell through #ashfall:bunker_core_power
		ItemStack s = new ItemStack(SalvageItems.SCRAP_METAL);
		ItemStack a = new ItemStack(ArmsItems.ALLOY_PLATE);
		ItemStack cell = new ItemStack(ArmsItems.ENERGY_CELL);
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(a, s, a, s, cell, s, a, s, a)), HavenBlocks.BUNKER_CORE_ITEM, 1);
		// Salvage fortification and the charger upgrade
		ItemStack planks = new ItemStack(Items.OAK_PLANKS);
		assertCrafts(helper, recipes, CraftingInput.of(2, 3, List.of(a, a, planks, planks, a, a)), DirectorBlocks.REINFORCED_DOOR_ITEM, 1);
		helper.assertTrue(DirectorBlocks.REINFORCED_DOOR.defaultBlockState().is(Fortified.FORTIFIED), "the Reinforced Door must be fortified");
		ItemStack el = new ItemStack(ArmsItems.SCRAP_ELECTRONICS), wire = new ItemStack(ArmsItems.WIRE_SPOOL);
		ItemStack field = new ItemStack(ArmsBlocks.FIELD_CHARGER_ITEM);
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(a, el, a, wire, field, wire, a, el, a)), ArmsBlocks.RAPID_CHARGER_ITEM, 1);
		// Electric lights: lanterns and lamps need scrap electronics, the torch and glowstone versions are gone
		ItemStack n = new ItemStack(Items.IRON_NUGGET);
		ItemStack e = new ItemStack(ArmsItems.SCRAP_ELECTRONICS);
		ItemStack torch = new ItemStack(Items.TORCH);
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(n, n, n, n, e, n, n, n, n)), Items.LANTERN, 1);
		CraftingInput torchLantern = CraftingInput.of(3, 3, List.of(n, n, n, n, torch, n, n, n, n));
		helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, torchLantern, level).isEmpty(), "a torch still makes a lantern");
		ItemStack x = ItemStack.EMPTY;
		ItemStack red = new ItemStack(ArmsItems.WIRE_SPOOL);
		assertCrafts(helper, recipes, CraftingInput.of(3, 3, List.of(x, red, x, red, e, red, x, new ItemStack(Items.GLASS), x)),
				Items.REDSTONE_LAMP, 1);
		helper.succeed();
	}

	private static void assertCrafts(GameTestHelper helper, RecipeManager recipes, CraftingInput input, Item expected, int count) {
		ItemStack result = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
		helper.assertTrue(result.is(expected) && result.getCount() == count,
				"expected " + count + "x " + BuiltInRegistries.ITEM.getKey(expected) + " but crafted " + result);
	}

	/** Everyday loot is survival salvage: vanilla items are a minor share of every building table's weight. */
	@GameTest
	public void buildingLootIsMostlySurvivalSalvage(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().getServer().reloadableRegistries().lookup();
		for (String id : PROFILES) {
			JsonElement json = encodeJson(registries, ashfall("buildings/" + id));
			double vanilla = 0;
			double total = 0;
			boolean survival = false;
			for (JsonElement pool : json.getAsJsonObject().getAsJsonArray("pools")) {
				for (JsonElement e : pool.getAsJsonObject().getAsJsonArray("entries")) {
					var entry = e.getAsJsonObject();
					if (!entry.has("name")) {
						continue; // empty entry
					}
					String name = entry.get("name").getAsString();
					int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
					total += weight;
					if (name.startsWith("minecraft:")) {
						vanilla += weight;
					}
					survival |= SURVIVAL_ITEMS.stream().anyMatch(s -> name.equals("ashfall:" + s));
				}
			}
			helper.assertTrue(survival, "buildings/" + id + " has no survival salvage");
			helper.assertTrue(total > 0 && vanilla / total < MAX_VANILLA_SHARE,
					"buildings/" + id + " is " + Math.round(100 * vanilla / total) + "% vanilla items (limit " + Math.round(100 * MAX_VANILLA_SHARE) + "%)");
		}
		helper.succeed();
	}

	@GameTest
	public void buildingProfilesLoaded(GameTestHelper helper) {
		Map<Identifier, BuildingProfile> all = BuildingProfiles.all();
		helper.assertTrue(!all.isEmpty(), "no building profiles loaded");
		for (String id : PROFILES) {
			helper.assertTrue(all.containsKey(ashfall(id)), "building profile ashfall:" + id + " did not load (missing or failed to parse)");
			BuildingProfile profile = all.get(ashfall(id));
			helper.assertTrue(profile.lootTable().isPresent(), "profile ashfall:" + id + " has no loot table");
			helper.assertTrue(!profile.containers().blocks().isEmpty(), "profile ashfall:" + id + " has no container blocks");
			helper.assertTrue(profile.refill().quietTicks() > 0, "profile ashfall:" + id + " refills with no quiet period");
		}
		helper.succeed();
	}

	@GameTest
	public void profileLootTablesExistAndRoll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.getServer().reloadableRegistries().lookup();
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
				.create(LootContextParamSets.CHEST);

		List<Identifier> tables = new ArrayList<>(CACHE_TABLES);
		BuildingProfiles.all().values().forEach(p -> p.lootTable().ifPresent(tables::add));
		helper.assertTrue(tables.size() > CACHE_TABLES.size(), "no profile references a loot table");

		for (Identifier id : tables) {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
			Optional<? extends net.minecraft.core.Holder<LootTable>> holder = registries.get(key);
			helper.assertTrue(holder.isPresent(), "loot table " + id + " is missing or failed to load");
			LootTable table = holder.get().value();
			int items = 0;
			for (int seed = 0; seed < ROLLS; seed++) {
				for (ItemStack stack : table.getRandomItems(params, seed)) {
					items += stack.getCount();
					helper.assertTrue(!stack.is(ArmsItems.RESONANCE_CRYSTAL), "loot table " + id + " rolled a resonance crystal");
				}
			}
			helper.assertTrue(items > 0, "loot table " + id + " never rolled anything in " + ROLLS + " tries");
		}
		helper.succeed();
	}

	/** Energy Cells come mainly from special zombies; only these building tables may hold a rare one. */
	private static final Set<String> ENERGY_CELL_BUILDINGS = Set.of("police", "electronics");

	@GameTest
	public void energyCellsStayRareInBuildings(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().getServer().reloadableRegistries().lookup();
		for (String id : PROFILES) {
			String text = encode(registries, ashfall("buildings/" + id));
			helper.assertTrue(!text.contains("ashfall:resonance_crystal"), "buildings/" + id + " contains a resonance crystal");
			helper.assertTrue(!text.contains("ashfall:antiviral"), "buildings/" + id + " references the removed antiviral item");
			helper.assertTrue(ENERGY_CELL_BUILDINGS.contains(id) || !text.contains("ashfall:energy_cell"),
					"buildings/" + id + " must not contain energy cells");
		}
		helper.succeed();
	}

	@GameTest
	public void zombieDropsAreInjected(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().getServer().reloadableRegistries().lookup();
		for (String mob : List.of("zombie", "husk")) {
			String text = encode(registries, Identifier.withDefaultNamespace("entities/" + mob));
			helper.assertTrue(text.contains("ashfall:scrap_electronics"), mob + " loot lacks scrap electronics");
			helper.assertTrue(text.contains("ashfall:bandage"), mob + " loot lacks bandages");
			helper.assertTrue(text.contains("minecraft:killed_by_player"), mob + " drops are not gated on a player kill");
			helper.assertTrue(!text.contains("ashfall:energy_cell"), "plain " + mob + "s must not drop energy cells");
		}
		helper.succeed();
	}

	private static String encode(HolderLookup.Provider registries, Identifier id) {
		return encodeJson(registries, id).toString();
	}

	private static JsonElement encodeJson(HolderLookup.Provider registries, Identifier id) {
		LootTable table = registries.get(ResourceKey.create(Registries.LOOT_TABLE, id)).orElseThrow().value();
		return LootTable.DIRECT_CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), table).getOrThrow();
	}
}
