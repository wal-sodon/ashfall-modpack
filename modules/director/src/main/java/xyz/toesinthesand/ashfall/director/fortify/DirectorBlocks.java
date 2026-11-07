package xyz.toesinthesand.ashfall.director.fortify;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.LandPathTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiBlock;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.traps.RattleBoxBlock;
import xyz.toesinthesand.ashfall.director.traps.RattleBoxBlockEntity;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;
import xyz.toesinthesand.ashfall.director.traps.TriplineAlarmBlock;
import xyz.toesinthesand.ashfall.director.traps.TriplineAlarmBlockEntity;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Fortification blocks: the planks, barricade and door are in {@code #ashfall:fortified}, so ZB&B zombies can't break
 * them outright (only siege bosses can); they wear them down instead ({@link FortifyWear}). The traps (Rattle Box,
 * Tripline Alarm, Spike Strip) are deliberately not: a zombie that reaches one can tear it down. Their block items join the {@code ashfall:main} tab by key; the tab itself is
 * owned by arms.
 */
public final class DirectorBlocks {
	/** Oak planks riveted with iron: tougher than wood, still an axe block, drops itself. */
	public static final Block REINFORCED_PLANKS = register("reinforced_planks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(4.0f, 9.0f).sound(SoundType.WOOD));
	/** Planks on scrap-metal straps, a partial but solid panel for doorways and windows. */
	public static final Block BARRICADE = register("barricade", BarricadeBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(3.5f, 8.0f).sound(SoundType.WOOD)
			.noOcclusion());
	/**
	 * Oak boards in an alloy frame: a hand-opened door zombies can only wear down, craftable from salvage. Why: iron doors
	 * cost 36 Scrap Metal each and copper needs mining, so early bases had no way to close a doorway for good.
	 * Copper's block set type gives hand opening and metal sounds.
	 */
	public static final Block REINFORCED_DOOR = register("reinforced_door", p -> new DoorBlock(BlockSetType.COPPER, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).requiresCorrectToolForDrops()
					.noOcclusion());

	/** Wind-up noise lure: rattles for 30 s, heard by zombies 32 blocks away. Not fortified. */
	public static final Block RATTLE_BOX = register("rattle_box", RattleBoxBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(1.5f, 3.0f).sound(SoundType.METAL).noOcclusion());
	/** Wall-hung zombie alarm: rings for its owner and nearby players, gives a redstone signal. Not fortified. */
	public static final Block TRIPLINE_ALARM = register("tripline_alarm", TriplineAlarmBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(1.0f, 2.0f).sound(SoundType.LANTERN).noCollision().noOcclusion()
			.pushReaction(PushReaction.POPPED));
	/** Carpet-thin nails that slow everything and hurt zombies, wearing out as it goes. Not fortified. */
	public static final Block SPIKE_STRIP = register("spike_strip", SpikeStripBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(1.0f, 2.0f).sound(SoundType.CHAIN).noCollision().noOcclusion()
			.pushReaction(PushReaction.POPPED));

	/** A sprayed Graffiti mark: paint on a wall, no collision, no drop, no item (the Spray Can places it). Not fortified. */
	public static final Block GRAFFITI = register("graffiti", GraffitiBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.NONE).instabreak().sound(SoundType.WOOL).noCollision().noOcclusion().noLootTable()
			.pushReaction(PushReaction.POPPED));

	public static final BlockEntityType<RattleBoxBlockEntity> RATTLE_BOX_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("rattle_box"), new BlockEntityType<>(RattleBoxBlockEntity::new, Set.of(RATTLE_BOX)));
	public static final BlockEntityType<TriplineAlarmBlockEntity> TRIPLINE_ALARM_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("tripline_alarm"), new BlockEntityType<>(TriplineAlarmBlockEntity::new, Set.of(TRIPLINE_ALARM)));

	public static final Item REINFORCED_PLANKS_ITEM = registerItem(REINFORCED_PLANKS);
	public static final Item BARRICADE_ITEM = registerItem(BARRICADE);
	public static final Item REINFORCED_DOOR_ITEM = registerDoubleHigh(REINFORCED_DOOR);
	public static final Item RATTLE_BOX_ITEM = registerItem(RATTLE_BOX);
	public static final Item TRIPLINE_ALARM_ITEM = registerItem(TRIPLINE_ALARM);
	public static final Item SPIKE_STRIP_ITEM = registerItem(SPIKE_STRIP);

	/** Block items, in creative-tab order. */
	public static final List<Item> ITEMS = List.of(REINFORCED_PLANKS_ITEM, BARRICADE_ITEM, REINFORCED_DOOR_ITEM,
			RATTLE_BOX_ITEM, TRIPLINE_ALARM_ITEM, SPIKE_STRIP_ITEM);

	private DirectorBlocks() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static Block register(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerItem(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static Item registerDoubleHigh(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new DoubleHighBlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/**
	 * Forces class loading (and so registration), adds the block items to the Ashfall tab and lets mobs path over
	 * the Spike Strip as open ground.
	 */
	public static void init() {
		LandPathTypeRegistry.register(SPIKE_STRIP, PathType.OPEN, null);
		SpikeStripBlock.init();
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> ITEMS.forEach(output::accept));
	}
}
