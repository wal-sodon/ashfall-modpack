package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 10.3.1: every recipe Ashfall ships is in the vanilla recipe book. Each one (the ashfall namespace plus the vanilla
 * recipes Ashfall overrides) has a display-less unlock advancement that rewards it, and a player who picks up one of
 * its key ingredients learns it. The two special recipes (socket a crystal, fit a Dampener) have no fixed pattern, so
 * the book can't show them and they stay out. Wooden ingredients take any wood.
 */
public class RecipeBookGameTest {
	/** Vanilla recipe ids Ashfall replaces (data/minecraft/recipe): vanilla's own advancements still unlock them. */
	private static final Set<String> OVERRIDES = Set.of("lantern", "soul_lantern", "copper_lantern", "redstone_lamp");
	private static final Set<String> SPECIAL = Set.of("socket_crystal", "attach_dampener");

	private static boolean shipped(RecipeHolder<?> holder) {
		Identifier id = holder.id().identifier();
		return id.getNamespace().equals("ashfall") || id.getNamespace().equals("minecraft") && OVERRIDES.contains(id.getPath());
	}

	private static List<RecipeHolder<?>> shippedRecipes(MinecraftServer server) {
		List<RecipeHolder<?>> out = new ArrayList<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (shipped(holder)) {
				out.add(holder);
			}
		}
		return out;
	}

	private static List<AdvancementHolder> unlocks(MinecraftServer server, ResourceKey<Recipe<?>> key) {
		List<AdvancementHolder> out = new ArrayList<>();
		for (AdvancementHolder adv : server.getAdvancements().getAllAdvancements()) {
			if (adv.value().rewards().recipes().contains(key)) {
				out.add(adv);
			}
		}
		return out;
	}

	@GameTest
	public void everyShippedRecipeHasAHiddenUnlock(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<RecipeHolder<?>> recipes = shippedRecipes(server);
		helper.assertTrue(recipes.size() >= 62, "expected at least 62 shipped recipes, found " + recipes.size());
		int special = 0;
		for (RecipeHolder<?> holder : recipes) {
			String id = holder.id().identifier().toString();
			if (holder.value() instanceof CustomRecipe) {
				helper.assertTrue(SPECIAL.contains(holder.id().identifier().getPath()), id + " is special but not on the known list");
				special++;
				continue;
			}
			helper.assertTrue(!holder.value().placementInfo().isImpossibleToPlace(), id + " can't be shown in the recipe book");
			List<AdvancementHolder> unlocks = unlocks(server, holder.id());
			helper.assertTrue(!unlocks.isEmpty(), id + " has no unlock advancement");
			for (AdvancementHolder adv : unlocks) {
				helper.assertTrue(adv.value().display().isEmpty(), "unlock " + adv.id() + " has a display and would show in an advancement tab");
				helper.assertTrue(adv.value().criteria().containsKey("has_the_recipe"), "unlock " + adv.id() + " lacks has_the_recipe");
			}
			if (holder.id().identifier().getNamespace().equals("minecraft")) {
				Identifier vanilla = unlocks.stream().map(AdvancementHolder::id).filter(a -> a.getNamespace().equals("minecraft")).findFirst().orElse(null);
				helper.assertTrue(vanilla != null, "vanilla no longer unlocks the overridden " + id);
			}
		}
		helper.assertTrue(special == SPECIAL.size(), "expected " + SPECIAL.size() + " special recipes, found " + special);
		helper.succeed();
	}

	/** The item the unlock keys on: the first Ashfall ingredient, or the first ingredient if the recipe has none. */
	private static Item keyIngredient(Recipe<?> recipe) {
		Item first = null;
		for (Ingredient ing : recipe.placementInfo().ingredients()) {
			List<Holder<Item>> items = ing.items().toList();
			if (items.isEmpty()) {
				continue;
			}
			if (first == null) {
				first = items.getFirst().value();
			}
			for (Holder<Item> item : items) {
				if (BuiltInRegistries.ITEM.getKey(item.value()).getNamespace().equals("ashfall")) {
					return item.value();
				}
			}
		}
		return first;
	}

	@GameTest
	public void oneIngredientUnlocksEachRecipe(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (RecipeHolder<?> holder : shippedRecipes(server)) {
			if (holder.value() instanceof CustomRecipe) {
				continue;
			}
			// Forget the recipe and its unlocks, empty the inventory, then hand over one key ingredient.
			player.getInventory().clearContent();
			player.inventoryMenu.broadcastChanges();
			player.resetRecipes(List.of(holder));
			for (AdvancementHolder adv : unlocks(server, holder.id())) {
				for (String criterion : adv.value().criteria().keySet()) {
					player.getAdvancements().revoke(adv, criterion);
				}
			}
			helper.assertTrue(!player.getRecipeBook().contains(holder.id()), holder.id().identifier() + " still known after the reset");
			Item item = keyIngredient(holder.value());
			helper.assertTrue(item != null, holder.id().identifier() + " has no ingredient to hand over");
			player.getInventory().add(new ItemStack(item));
			player.inventoryMenu.broadcastChanges();
			helper.assertTrue(player.getRecipeBook().contains(holder.id()),
					"picking up " + BuiltInRegistries.ITEM.getKey(item) + " did not unlock " + holder.id().identifier());
		}
		// A tag ingredient unlocks too: any planks for Reinforced Planks.
		ResourceKey<Recipe<?>> planksRecipe = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("ashfall", "reinforced_planks"));
		RecipeHolder<?> planks = server.getRecipeManager().byKey(planksRecipe).orElseThrow();
		player.getInventory().clearContent();
		player.inventoryMenu.broadcastChanges();
		player.resetRecipes(List.of(planks));
		for (AdvancementHolder adv : unlocks(server, planksRecipe)) {
			for (String criterion : adv.value().criteria().keySet()) {
				player.getAdvancements().revoke(adv, criterion);
			}
		}
		player.getInventory().add(new ItemStack(Items.CRIMSON_PLANKS));
		player.inventoryMenu.broadcastChanges();
		helper.assertTrue(player.getRecipeBook().contains(planksRecipe), "crimson planks did not unlock Reinforced Planks");
		helper.succeed();
	}

	@GameTest
	public void reinforcedPlanksTakeAnyWood(GameTestHelper helper) {
		ItemStack n = new ItemStack(Items.IRON_NUGGET);
		ItemStack spruce = new ItemStack(Items.SPRUCE_PLANKS), cherry = new ItemStack(Items.CHERRY_PLANKS);
		ItemStack crimson = new ItemStack(Items.CRIMSON_PLANKS), bamboo = new ItemStack(Items.BAMBOO_PLANKS);
		CraftingInput input = CraftingInput.of(3, 3, List.of(n, spruce, n, cherry, n, crimson, n, bamboo, n));
		ItemStack out = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(h -> h.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertTrue(out.is(DirectorBlocks.REINFORCED_PLANKS_ITEM) && out.getCount() == 4, "mixed planks made " + out);
		helper.succeed();
	}
}
