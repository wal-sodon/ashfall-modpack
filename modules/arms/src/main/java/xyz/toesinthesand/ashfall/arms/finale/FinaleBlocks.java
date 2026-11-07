package xyz.toesinthesand.ashfall.arms.finale;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.List;
import java.util.function.Function;

/**
 * The Black Start's blocks, items and the Choir's entity type.
 * <p>
 * <b>The Grid Uplink takes three crafts</b> because its cost (endgame-design section 4: 3 Resonance Crystals, 4
 * Focusing Lenses, 8 Grid Couplers, 16 Energy Cells, 31 items) cannot fit one 3x3 grid, where every slot takes one
 * item:
 * <ol>
 *   <li><b>Uplink Frame</b>: 3 crystals, 4 lenses, 2 couplers (9 slots).</li>
 *   <li><b>Capacitor Bank</b>, made twice: 8 Energy Cells around 1 coupler.</li>
 *   <li><b>Grid Uplink</b>: the frame, both banks and 4 more couplers.</li>
 * </ol>
 * The totals are exactly the design's. The Uplink is fire resistant (its crystals are), so it survives lava.
 */
public final class FinaleBlocks {
	/** Unbreakable and immovable: the finale is fought around it, and a socket carried home would end the arc. */
	public static final UplinkSocketBlock UPLINK_SOCKET = registerBlock("uplink_socket", UplinkSocketBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).sound(SoundType.HEAVY_CORE).strength(-1.0f, 3_600_000.0f)
					.noLootTable().pushReaction(PushReaction.IMMOVEABLE)
					.lightLevel(state -> UplinkSocketBlock.light(state.getValue(UplinkSocketBlock.POWER))));
	/** Unbreakable for the same reason: a breaker is closed by holding it, not by mining it. */
	public static final RelayBreakerBlock RELAY_BREAKER = registerBlock("relay_breaker", RelayBreakerBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(-1.0f, 3_600_000.0f)
					.noLootTable().pushReaction(PushReaction.IMMOVEABLE).lightLevel(RelayBreakerBlock::light));
	/**
	 * Registered under {@code ashfall:heart_pylon}, the id {@code #ashfall:crystal_pylons} has listed since M5, so the
	 * Plasma Blade's pylon bonus applies with no code change. Hardness 80 in no mineable tag (see the block class);
	 * explosion resistant so a bloated's burst or a creeper does not do the blade's job.
	 */
	public static final HeartPylonBlock HEART_PYLON = registerBlock("heart_pylon", HeartPylonBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).sound(SoundType.AMETHYST).strength(80.0f, 1200.0f)
					.noLootTable().pushReaction(PushReaction.IMMOVEABLE).lightLevel(state -> 10));
	/**
	 * The Wreck hull's hatch until the city reaches Grid 80%. Unbreakable: the iron doors it replaces could be opened
	 * by any button a player placed beside them.
	 */
	public static final Block HULL_SEAL = registerBlock("hull_seal", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK).strength(-1.0f, 3_600_000.0f)
					.noLootTable().pushReaction(PushReaction.IMMOVEABLE));

	public static final Item UPLINK_FRAME = registerItem("uplink_frame", FinaleItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
	public static final Item CAPACITOR_BANK = registerItem("capacitor_bank", FinaleItem::new,
			new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON));
	public static final Item GRID_UPLINK = registerItem("grid_uplink", FinaleItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	public static final Item UPLINK_SOCKET_ITEM = registerBlockItem(UPLINK_SOCKET, new Item.Properties().rarity(Rarity.EPIC));
	public static final Item RELAY_BREAKER_ITEM = registerBlockItem(RELAY_BREAKER, new Item.Properties().rarity(Rarity.RARE));
	public static final Item HEART_PYLON_ITEM = registerBlockItem(HEART_PYLON, new Item.Properties().rarity(Rarity.EPIC));
	public static final Item HULL_SEAL_ITEM = registerBlockItem(HULL_SEAL, new Item.Properties().rarity(Rarity.RARE));

	/** Big enough to read as a boss (its 1.8x scale attribute grows hitbox and model together). */
	public static final EntityType<ChoirEntity> CHOIR = registerEntity("choir",
			EntityType.Builder.<ChoirEntity>of(ChoirEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
					.clientTrackingRange(10).notInPeaceful());

	/** Creative-tab order: the craft chain first, then the world fixtures for admins. */
	public static final List<Item> ITEMS = List.of(UPLINK_FRAME, CAPACITOR_BANK, GRID_UPLINK, UPLINK_SOCKET_ITEM,
			RELAY_BREAKER_ITEM, HEART_PYLON_ITEM, HULL_SEAL_ITEM);

	private FinaleBlocks() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static <B extends Block> B registerBlock(String path, Function<BlockBehaviour.Properties, B> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerBlockItem(Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix()));
	}

	private static Item registerItem(String path, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> registerEntity(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Forces class loading (and so registration), registers the Choir's attributes and fills the creative tab. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(CHOIR, ChoirEntity.createChoirAttributes());
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> ITEMS.forEach(output::accept));
	}
}
