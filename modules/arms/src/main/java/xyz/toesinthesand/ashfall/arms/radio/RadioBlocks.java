package xyz.toesinthesand.ashfall.arms.radio;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.function.Function;

/** The radio-job blocks: the craftable Shortwave Set and the Distress Beacon a distress call leaves on a top floor. */
public final class RadioBlocks {
	public static final ShortwaveSetBlock SHORTWAVE_SET = register("shortwave_set", ShortwaveSetBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY).sound(SoundType.METAL).strength(2.0f, 4.0f).noOcclusion()
			.lightLevel(state -> 3));
	/**
	 * Not craftable and unbreakable in survival (like a Power Relay), so neither players nor zombies end the call early:
	 * it turns into the reward chest when the call is answered and vanishes if the job runs out. A light so it can be
	 * found on a dark top floor.
	 */
	public static final Block DISTRESS_BEACON = register("distress_beacon", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED).sound(SoundType.METAL).strength(-1.0f, 3_600_000.0f).noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE).lightLevel(state -> 12));

	public static final Item SHORTWAVE_SET_ITEM = registerItem(SHORTWAVE_SET);

	private RadioBlocks() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static <B extends Block> B register(String path, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerItem(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	static void init() {
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> output.accept(SHORTWAVE_SET_ITEM));
	}
}
