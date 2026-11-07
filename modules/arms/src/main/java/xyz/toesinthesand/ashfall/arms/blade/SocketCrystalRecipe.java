package xyz.toesinthesand.ashfall.arms.blade;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

/**
 * {@code ashfall:socket_crystal}: a worn or dull Plasma Blade plus one Resonance Crystal, shapeless,
 * gives the same blade (charge and all) with a fresh crystal. A special recipe because the result must
 * keep the input blade's components, and because a blade with a full crystal must not eat a crystal
 * (they are among the rarest items in the pack).
 */
public class SocketCrystalRecipe extends CustomRecipe {
	public static final SocketCrystalRecipe INSTANCE = new SocketCrystalRecipe();
	public static final MapCodec<SocketCrystalRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, SocketCrystalRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<SocketCrystalRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath("ashfall", "socket_crystal"), new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

	/** The blade in the grid if it holds exactly one worn blade and one Resonance Crystal, else empty. */
	private static ItemStack findBlade(CraftingInput input) {
		if (input.ingredientCount() != 2) {
			return ItemStack.EMPTY;
		}
		ItemStack blade = ItemStack.EMPTY;
		boolean crystal = false;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof PlasmaBladeItem && blade.isEmpty()) {
				blade = stack;
			} else if (stack.is(ArmsItems.RESONANCE_CRYSTAL) && !crystal) {
				crystal = true;
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (!crystal || blade.isEmpty() || !PlasmaBladeItem.crystal(blade).worn()) {
			return ItemStack.EMPTY;
		}
		return blade;
	}

	/** {@code blade} with a freshly socketed crystal at the current datapack integrity. */
	public static ItemStack socket(ItemStack blade) {
		ItemStack result = blade.copyWithCount(1);
		result.set(ArmsComponents.BLADE_CRYSTAL, BladeCrystal.fresh(BladeStatsManager.get(blade.getItem()).crystalIntegrity()));
		PlasmaBladeItem.refreshLit(result);
		return result;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !findBlade(input).isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack blade = findBlade(input);
		return blade.isEmpty() ? ItemStack.EMPTY : socket(blade);
	}

	@Override
	public RecipeSerializer<SocketCrystalRecipe> getSerializer() {
		return SERIALIZER;
	}
}
