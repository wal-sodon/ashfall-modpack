package xyz.toesinthesand.ashfall.director.special;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Entity types, default attributes and spawn eggs of the special infected, plus the spitter's acid.
 * <p>
 * Default attributes repeat the numbers in {@code special_infected/*.json} because an entity type must have a
 * complete attribute set at registration, before any datapack loads; the JSON is applied over them on every spawn
 * and is what admins should edit. Sizes are fixed here because hitboxes are part of the entity type: the crawler
 * is under a block tall, the bloated wider, the screamer thinner. The brute's 1.6x is the {@code minecraft:scale}
 * attribute instead, so its file can change it.
 */
public final class SpecialEntities {
	public static final EntityType<Crawler> CRAWLER = register("crawler",
			EntityType.Builder.<Crawler>of(Crawler::new, MobCategory.MONSTER).sized(0.7f, 0.8f).eyeHeight(0.55f)
					.clientTrackingRange(8).notInPeaceful());
	public static final EntityType<Bloated> BLOATED = register("bloated",
			EntityType.Builder.<Bloated>of(Bloated::new, MobCategory.MONSTER).sized(0.85f, 1.95f).eyeHeight(1.74f)
					.clientTrackingRange(8).notInPeaceful());
	public static final EntityType<Screamer> SCREAMER = register("screamer",
			EntityType.Builder.<Screamer>of(Screamer::new, MobCategory.MONSTER).sized(0.5f, 2.05f).eyeHeight(1.84f)
					.clientTrackingRange(10).notInPeaceful());
	public static final EntityType<Spitter> SPITTER = register("spitter",
			EntityType.Builder.<Spitter>of(Spitter::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
					.clientTrackingRange(8).notInPeaceful());
	public static final EntityType<Brute> BRUTE = register("brute",
			EntityType.Builder.<Brute>of(Brute::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
					.clientTrackingRange(10).notInPeaceful());
	public static final EntityType<Stalker> STALKER = register("stalker",
			EntityType.Builder.<Stalker>of(Stalker::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
					.clientTrackingRange(8).notInPeaceful());

	/** Small and short-lived like llama spit; not saved (a reload mid-flight just drops the shot). */
	public static final EntityType<AcidSpit> ACID_SPIT = register("acid_spit",
			EntityType.Builder.<AcidSpit>of(AcidSpit::new, MobCategory.MISC).sized(0.25f, 0.25f)
					.clientTrackingRange(4).updateInterval(10).noSave().noLootTable());

	private static final Map<SpecialKind, EntityType<? extends SpecialInfectedEntity>> BY_KIND = new EnumMap<>(Map.of(
			SpecialKind.CRAWLER, CRAWLER, SpecialKind.BLOATED, BLOATED, SpecialKind.SCREAMER, SCREAMER,
			SpecialKind.SPITTER, SPITTER, SpecialKind.BRUTE, BRUTE, SpecialKind.STALKER, STALKER));

	public static final Item CRAWLER_SPAWN_EGG = egg("crawler", CRAWLER);
	public static final Item BLOATED_SPAWN_EGG = egg("bloated", BLOATED);
	public static final Item SCREAMER_SPAWN_EGG = egg("screamer", SCREAMER);
	public static final Item SPITTER_SPAWN_EGG = egg("spitter", SPITTER);
	public static final Item BRUTE_SPAWN_EGG = egg("brute", BRUTE);
	public static final Item STALKER_SPAWN_EGG = egg("stalker", STALKER);

	public static final List<Item> EGGS = List.of(CRAWLER_SPAWN_EGG, BLOATED_SPAWN_EGG, SCREAMER_SPAWN_EGG,
			SPITTER_SPAWN_EGG, BRUTE_SPAWN_EGG, STALKER_SPAWN_EGG);

	private SpecialEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("ashfall", path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String kind, EntityType<?> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ashfall", kind + "_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
	}

	public static EntityType<? extends SpecialInfectedEntity> type(SpecialKind kind) {
		return BY_KIND.get(kind);
	}

	/** Code defaults, identical to the shipped JSON (see the class comment for why both exist). */
	static AttributeSupplier.Builder attributes(SpecialKind kind) {
		AttributeSupplier.Builder b = Zombie.createAttributes().add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
		return switch (kind) {
			case CRAWLER -> b.add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.32)
					.add(Attributes.ATTACK_DAMAGE, 2.5).add(Attributes.FOLLOW_RANGE, 35.0);
			case BLOATED -> b.add(Attributes.MAX_HEALTH, 26.0).add(Attributes.MOVEMENT_SPEED, 0.18)
					.add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
			case SCREAMER -> b.add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.26)
					.add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 48.0);
			case SPITTER -> b.add(Attributes.MAX_HEALTH, 18.0).add(Attributes.MOVEMENT_SPEED, 0.22)
					.add(Attributes.ATTACK_DAMAGE, 2.5).add(Attributes.FOLLOW_RANGE, 40.0);
			case BRUTE -> b.add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.2)
					.add(Attributes.ATTACK_DAMAGE, 8.0).add(Attributes.ATTACK_KNOCKBACK, 2.0)
					.add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ARMOR, 8.0)
					.add(Attributes.ARMOR_TOUGHNESS, 2.0).add(Attributes.SCALE, 1.6);
			case STALKER -> b.add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.27)
					.add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FOLLOW_RANGE, 40.0);
		};
	}

	/** Forces class loading (and so registration), registers default attributes and fills the creative tab. */
	static void init() {
		for (SpecialKind kind : SpecialKind.values()) {
			FabricDefaultAttributeRegistry.register(type(kind), attributes(kind));
		}
		CreativeModeTabEvents.modifyOutputEvent(DirectorItems.ASHFALL_TAB).register(output -> EGGS.forEach(output::accept));
	}
}
