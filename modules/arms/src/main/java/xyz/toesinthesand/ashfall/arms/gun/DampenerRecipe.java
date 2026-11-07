package xyz.toesinthesand.ashfall.arms.gun;

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
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;

/**
 * {@code ashfall:attach_dampener}: any energy gun plus one Dampener, shapeless, gives the same gun
 * (heat, pack and all) with the Dampener fitted. A special recipe rather than a shapeless one because
 * the result must keep the input gun's components, and it must refuse a gun that already has one.
 */
public class DampenerRecipe extends CustomRecipe {
	public static final DampenerRecipe INSTANCE = new DampenerRecipe();
	public static final MapCodec<DampenerRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, DampenerRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<DampenerRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath("ashfall", "attach_dampener"), new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

	/** The gun in the grid if the grid holds exactly one undampened gun and one Dampener, else empty. */
	private static ItemStack findGun(CraftingInput input) {
		if (input.ingredientCount() != 2) {
			return ItemStack.EMPTY;
		}
		ItemStack gun = ItemStack.EMPTY;
		boolean dampener = false;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof EnergyGunItem && gun.isEmpty()) {
				gun = stack;
			} else if (stack.is(ArmsItems.DAMPENER) && !dampener) {
				dampener = true;
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (!dampener || gun.isEmpty()
				|| gun.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE).has(GunAttachments.DAMPENER)) {
			return ItemStack.EMPTY;
		}
		return gun;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !findGun(input).isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack gun = findGun(input);
		if (gun.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack result = gun.copyWithCount(1);
		result.set(ArmsComponents.ATTACHMENTS,
				result.getOrDefault(ArmsComponents.ATTACHMENTS, GunAttachments.NONE).with(GunAttachments.DAMPENER));
		return result;
	}

	@Override
	public RecipeSerializer<DampenerRecipe> getSerializer() {
		return SERIALIZER;
	}

	/** Forces class loading, and so serializer registration. */
	public static void init() {
	}
}
