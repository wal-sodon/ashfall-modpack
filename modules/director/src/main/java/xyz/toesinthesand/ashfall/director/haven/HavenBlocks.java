package xyz.toesinthesand.ashfall.director.haven;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

/**
 * Haven blocks. The Bunker Core is as hard as an anvil to mine and as blast resistant as obsidian, and it is
 * in {@code #ashfall:fortified}, so a horde cannot simply dig out the heart of a base; only siege bosses can.
 */
public final class HavenBlocks {
	public static final ResourceKey<Block> BUNKER_CORE_KEY = ResourceKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("ashfall", "bunker_core"));
	public static final Block BUNKER_CORE = Registry.register(BuiltInRegistries.BLOCK, BUNKER_CORE_KEY,
			new BunkerCoreBlock(BlockBehaviour.Properties.of().setId(BUNKER_CORE_KEY).mapColor(MapColor.METAL)
					.instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops()
					.strength(5.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK).lightLevel(state -> 4).pushReaction(PushReaction.IMMOVEABLE)));
	public static final Item BUNKER_CORE_ITEM = registerItem();

	private HavenBlocks() {
	}

	private static Item registerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BUNKER_CORE_KEY.identifier());
		return Registry.register(BuiltInRegistries.ITEM, key, new BunkerCoreItem(BUNKER_CORE,
				new Item.Properties().setId(key).useBlockDescriptionPrefix().stacksTo(1).rarity(Rarity.UNCOMMON)));
	}

	/** Forces class loading (and so registration) and adds the core to the Ashfall tab. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> output.accept(BUNKER_CORE_ITEM));
	}
}
