package xyz.toesinthesand.ashfall.arms.bench;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

/** The Weaponsmith Bench block, its item and its menu type. */
public final class ArmsBench {
	/** Not a full cube (legs, shelf, a vise on top), so it must not hide its neighbours' faces. */
	public static final Block WEAPONSMITH_BENCH = registerBlock("weaponsmith_bench", BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.5f, 6.0f).noOcclusion());
	public static final Item WEAPONSMITH_BENCH_ITEM = registerItem(WEAPONSMITH_BENCH);
	public static final MenuType<WeaponsmithBenchMenu> MENU = Registry.register(BuiltInRegistries.MENU, id("weaponsmith_bench"),
			new MenuType<>(WeaponsmithBenchMenu::new, FeatureFlags.VANILLA_SET));

	private ArmsBench() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static Block registerBlock(String path, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, new WeaponsmithBenchBlock(properties.setId(key)));
	}

	private static Item registerItem(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** Forces class loading (and so registration) and adds the bench to the Ashfall tab. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> output.accept(WEAPONSMITH_BENCH_ITEM));
	}
}
