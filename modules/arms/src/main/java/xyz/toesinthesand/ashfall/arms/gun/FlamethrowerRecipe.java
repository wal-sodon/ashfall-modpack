package xyz.toesinthesand.ashfall.arms.gun;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/**
 * {@code ashfall:flamethrower}: an ordinary shaped recipe (same JSON as {@code minecraft:crafting_shaped}, so the
 * recipe book shows it) whose result starts with the fuel left in the Lighter Fluid used to build it,
 * {@link FlamethrowerLogic#TICKS_PER_USE} per remaining use.
 */
public class FlamethrowerRecipe extends ShapedRecipe {
	public static final MapCodec<FlamethrowerRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
			CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
			ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result)
	).apply(i, FlamethrowerRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlamethrowerRecipe> STREAM_CODEC = StreamCodec.composite(
			Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
			CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
			ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
			ItemStackTemplate.STREAM_CODEC, r -> r.result,
			FlamethrowerRecipe::new);
	public static final RecipeSerializer<FlamethrowerRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			Identifier.fromNamespaceAndPath("ashfall", "flamethrower"), new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

	// Our own copies: ShapedRecipe keeps these private, and the codecs above need them back.
	private final ShapedRecipePattern pattern;
	private final ItemStackTemplate result;

	public FlamethrowerRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
							  ItemStackTemplate result) {
		super(commonInfo, bookInfo, pattern, result);
		this.pattern = pattern;
		this.result = result;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack out = super.assemble(input);
		int uses = 0;
		for (ItemStack stack : input.items()) {
			if (stack.is(SalvageItems.LIGHTER_FLUID)) {
				uses += stack.getMaxDamage() - stack.getDamageValue();
			}
		}
		out.set(ArmsComponents.FUEL, Math.min(FlamethrowerLogic.TANK_TICKS, uses * FlamethrowerLogic.TICKS_PER_USE));
		return out;
	}

	@Override
	@SuppressWarnings("unchecked")
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) SERIALIZER;
	}

	/** Forces class loading, and so serializer registration. */
	public static void init() {
	}
}
