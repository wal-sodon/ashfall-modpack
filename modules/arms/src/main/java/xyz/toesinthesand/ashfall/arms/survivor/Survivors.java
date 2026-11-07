package xyz.toesinthesand.ashfall.arms.survivor;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.blade.PlasmaBladeItem;
import xyz.toesinthesand.ashfall.arms.gun.EnergyGunItem;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.haven.BunkerCoreRecord;
import xyz.toesinthesand.ashfall.director.haven.HavenState;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.horde.HordeNights;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Survivors (survivors plan, build order step 1): registration and the API other systems call.
 * <ul>
 * <li>{@link #spawn} puts a survivor in the world (radio rescues, holdouts, the admin command);
 * <li>{@link #isSurvivor} / {@link #SURVIVORS} for code that must treat survivors as people (zombie senses, friendly
 * fire, infection; the tag is for director code, which cannot see arms classes);
 * <li>{@link #mayOrder} decides who a survivor listens to: its leader, the leader's OPAC party, or anyone while it has
 * no leader (the first order makes that player its leader);
 * <li>{@link #roleChangeRefusal} is the one place role-change rules go.
 * </ul>
 * Holds no static world state; who exists where is the saved {@link SurvivorRoster}.
 */
public final class Survivors {
	/** Entity tag of every survivor type, for modules that cannot reference {@link Survivor}. */
	public static final TagKey<EntityType<?>> SURVIVORS = TagKey.create(Registries.ENTITY_TYPE, id("survivors"));
	/** How close a player must be for talk buttons and orders to work. */
	public static final double ORDER_RANGE = 16.0;

	public static final EntityType<Survivor> SURVIVOR = registerEntity("survivor",
			EntityType.Builder.<Survivor>of(Survivor::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f)
					.clientTrackingRange(10));

	public static final SupplyLockerBlock SUPPLY_LOCKER = registerBlock("supply_locker", BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops());
	public static final BlockEntityType<SupplyLockerBlockEntity> SUPPLY_LOCKER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("supply_locker"), new BlockEntityType<>(SupplyLockerBlockEntity::new, Set.of(SUPPLY_LOCKER)));
	public static final Item SUPPLY_LOCKER_ITEM = registerLockerItem();
	public static final Item SURVIVOR_SPAWN_EGG = registerEgg();

	public static final List<Item> ITEMS = List.of(SUPPLY_LOCKER_ITEM, SURVIVOR_SPAWN_EGG);

	private Survivors() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("ashfall", path);
	}

	private static <T extends Entity> EntityType<T> registerEntity(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static SupplyLockerBlock registerBlock(String path, BlockBehaviour.Properties properties) {
		ResourceKey<net.minecraft.world.level.block.Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, new SupplyLockerBlock(properties.setId(key)));
	}

	private static Item registerLockerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(SUPPLY_LOCKER));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new SupplyLockerItem(SUPPLY_LOCKER, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static Item registerEgg() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("survivor_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(SURVIVOR).setId(key)));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SURVIVOR, Survivor.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> ITEMS.forEach(output::accept));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SurvivorCommand.register(dispatcher));
		SurvivorInfection.init();
		SurvivorWork.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(SurvivorBalance.Loader.ID, new SurvivorBalance.Loader());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(TraderStock.Loader.ID, new TraderStock.Loader());
		Traders.init();
		SurvivorHoldouts.init();
	}

	// ---- API ----

	/**
	 * Spawns a survivor standing at {@code pos} with a random name and skin. With a leader it starts out following them,
	 * without one it stays where it is until someone talks to it.
	 *
	 * @return the survivor, or null if the entity could not be created
	 */
	public static @Nullable Survivor spawn(ServerLevel level, BlockPos pos, SurvivorRole role, @Nullable UUID leader) {
		Survivor survivor = SURVIVOR.create(level, EntitySpawnReason.EVENT);
		if (survivor == null) {
			return null;
		}
		survivor.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		survivor.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		survivor.startAs(role);
		survivor.setLeader(leader);
		survivor.setMode(leader == null ? SurvivorMode.STAY : SurvivorMode.FOLLOW);
		if (!level.addFreshEntity(survivor)) {
			return null;
		}
		survivor.noteRoster();
		return survivor;
	}

	public static boolean isSurvivor(Entity entity) {
		return entity instanceof Survivor;
	}

	/** Whether {@code player} may give {@code survivor} orders (see the class comment). */
	public static boolean mayOrder(ServerPlayer player, Survivor survivor) {
		UUID leader = survivor.leader();
		if (leader == null || leader.equals(player.getUUID())) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		UUID party = Claims.partyId(server, player.getUUID());
		return party != null && party.equals(Claims.partyId(server, leader));
	}

	/**
	 * Why {@code survivor} will not take {@code role} now, or empty when it will: not while out scavenging, not from the
	 * horde siren until dawn on a horde night ({@link HordeNights#sirenToDawn}; Follow, Stay and Home orders still
	 * work), and the base roles only once it has a home ({@link SurvivorRole#needsHome}). A role it does take starts
	 * with a day at half speed ({@link SurvivorWork#newInRole}). A trader never changes its role.
	 */
	public static Optional<Component> roleChangeRefusal(Survivor survivor, SurvivorRole role) {
		MinecraftServer server = survivor.level().getServer();
		long clock = server == null ? 0L : server.overworld().getOverworldClockTime();
		return roleChangeRefusal(survivor, role, clock);
	}

	/** {@link #roleChangeRefusal(Survivor, SurvivorRole)} at day-clock tick {@code clock} (gametests pick the hour). */
	public static Optional<Component> roleChangeRefusal(Survivor survivor, SurvivorRole role, long clock) {
		if (survivor.away()) {
			return Optional.of(SurvivorTalk.line(survivor, "role.away"));
		}
		if (survivor.isTrader()) {
			return Optional.of(SurvivorTalk.line(survivor, "trader.no_orders"));
		}
		if (!role.assignable()) {
			return Optional.of(SurvivorTalk.line(survivor, "role.not_yet"));
		}
		if (role == survivor.role()) {
			return Optional.of(SurvivorTalk.line(survivor, "role.same"));
		}
		MinecraftServer server = survivor.level().getServer();
		if (server != null && HordeNights.sirenToDawn(server, clock)) {
			return Optional.of(SurvivorTalk.line(survivor, "role.horde"));
		}
		if (role.needsHome() && survivor.home() == null) {
			return Optional.of(SurvivorTalk.line(survivor, "role.needs_home"));
		}
		return Optional.empty();
	}

	/**
	 * A weapon a guard can swing: anything with the weapon component or a main-hand attack bonus. Energy guns are
	 * refused in phase 1 (guards have no ranged attack yet), and so are Plasma Blades (10.2.4): the blade's charge,
	 * crystal wear and noise only apply to players, so in a guard's hands it was a free, always-powered blade.
	 */
	public static boolean isMeleeWeapon(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof EnergyGunItem || stack.getItem() instanceof PlasmaBladeItem) {
			return false;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return stack.has(DataComponents.WEAPON) || modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND) > 0.0;
	}

	/** The Bunker Core saved at exactly {@code pos} in this level, if it still exists. */
	public static Optional<BunkerCoreRecord> coreAt(ServerLevel level, BlockPos pos) {
		return Optional.ofNullable(HavenState.get(level.getServer()).coreAt(level.dimension(), pos));
	}

	/**
	 * The Bunker Core whose area holds {@code pos}. Read from the saved cores rather than the haven region snapshot, so
	 * it matches what {@link SupplyLockers#lockers} searches.
	 */
	public static Optional<BunkerCoreRecord> coreContaining(ServerLevel level, BlockPos pos) {
		for (BunkerCoreRecord core : HavenState.get(level.getServer()).cores()) {
			if (core.dimension().equals(level.dimension()) && core.area().contains(pos.getX(), pos.getZ())) {
				return Optional.of(core);
			}
		}
		return Optional.empty();
	}

	/** Inside a Bunker Core's area: the haven region (which grows with the Bind ending) or a saved core's own area. */
	public static boolean inBunker(ServerLevel level, BlockPos pos) {
		return Havens.inBunker(level, pos) || coreContaining(level, pos).isPresent();
	}
}
