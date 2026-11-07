package xyz.toesinthesand.ashfall.director.spawn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creates, dresses and tags Director zombies, and is the public hook for other Director features.
 * <p>
 * Every zombie the Director spawns goes through {@link #spawn}: it gets its variant's attributes and
 * gear, the {@link #TAG} attachment (source, variant, building), the {@code ashfall_variant=<id>}
 * entity tag, and then {@link #SPAWNED} fires. The senses/targeting feature listens there to add its
 * goal; goals are not saved, so it must also handle zombies reloaded from disk (ENTITY_LOAD).
 */
public final class DirectorSpawns {
	/** Persistent so counts, caps and building ownership survive restarts; copied when a zombie converts (e.g. to drowned). */
	public static final AttachmentType<DirectorZombie> TAG = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("ashfall", "director_zombie"),
			builder -> builder.persistent(DirectorZombie.CODEC).copyOnDeath());

	/** Entity tag prefix the spec asks for: {@code ashfall_variant=<id>}. */
	public static final String VARIANT_TAG_PREFIX = "ashfall_variant=";

	@FunctionalInterface
	public interface Spawned {
		void onSpawned(Zombie zombie, DirectorZombie tag);
	}

	/** Fired once for every zombie the Director adds to the world, after it is tagged and in the level. */
	public static final Event<Spawned> SPAWNED = EventFactory.createArrayBacked(Spawned.class, listeners -> (zombie, tag) -> {
		for (Spawned listener : listeners) {
			listener.onSpawned(zombie, tag);
		}
	});

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private DirectorSpawns() {
	}

	/** The hook named in the spec: fires {@link #SPAWNED} for a Director zombie. */
	public static void onSpawned(Zombie zombie) {
		DirectorZombie tag = zombie.getAttached(TAG);
		if (tag != null) {
			SPAWNED.invoker().onSpawned(zombie, tag);
		}
	}

	public static DirectorZombie tagOf(Entity entity) {
		return entity.getAttached(TAG);
	}

	/**
	 * Spawns one Director zombie at the block {@code pos} (feet position, centred).
	 *
	 * @param type       entity to create; refused unless it is a {@link Zombie} (zombies-only rule)
	 * @param variantId  variant name for the tags, e.g. {@code runner}
	 * @param variant    stats and gear to apply; null applies nothing (plain entity of {@code type})
	 * @return the zombie, already in the level, or null if it could not be created or added
	 */
	public static Zombie spawn(ServerLevel level, BlockPos pos, EntityType<?> type, String variantId, ZombieVariant variant,
							   DirectorZombie.Source source, long buildingId) {
		Entity entity = type.create(level, EntitySpawnReason.EVENT);
		if (!(entity instanceof Zombie zombie)) {
			if (entity != null) {
				entity.discard();
			}
			return null;
		}
		RandomSource random = level.getRandom();
		zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
		// Group data "adult, no jockey": the variant decides babies, and a chicken passenger would escape our tagging.
		zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, new Zombie.ZombieGroupData(false, false));
		if (variant != null) {
			applyVariant(zombie, variant, random);
		}
		DirectorZombie tag = new DirectorZombie(source, variantId, buildingId);
		zombie.setAttached(TAG, tag);
		zombie.addTag(VARIANT_TAG_PREFIX + variantId);
		if (!level.addFreshEntity(zombie)) {
			return null;
		}
		onSpawned(zombie);
		return zombie;
	}

	/** Spawns a variant looked up by entry (id path becomes the tag name). */
	public static Zombie spawn(ServerLevel level, BlockPos pos, Map.Entry<Identifier, ZombieVariant> variant,
							   DirectorZombie.Source source, long buildingId) {
		if (variant == null) {
			return spawn(level, pos, EntityTypes.ZOMBIE, ZombieVariant.FALLBACK_ID, ZombieVariant.FALLBACK, source, buildingId);
		}
		Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(variant.getValue().entity());
		if (type.isEmpty()) {
			return null;
		}
		return spawn(level, pos, type.get(), variant.getKey().getPath(), variant.getValue(), source, buildingId);
	}

	/**
	 * Attributes first (vanilla's random spawn bonuses on those attributes are stripped so the file's
	 * numbers are the real numbers), then baby state (which re-adds the baby speed bonus), then gear.
	 */
	public static void applyVariant(Zombie zombie, ZombieVariant variant, RandomSource random) {
		for (Map.Entry<Identifier, Double> e : variant.attributes().entrySet()) {
			Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(e.getKey());
			AttributeInstance instance = holder.map(zombie::getAttribute).orElse(null);
			if (instance == null) {
				AshfallDirector.LOGGER.warn("Zombie variant attribute {} is unknown or not on {}", e.getKey(), zombie.getType());
				continue;
			}
			instance.removeModifiers();
			instance.setBaseValue(e.getValue());
		}
		zombie.setBaby(variant.babyChance() > 0 && random.nextFloat() < variant.babyChance());
		zombie.setHealth(zombie.getMaxHealth());

		for (EquipmentSlot slot : ARMOUR) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		// Vanilla's finalizeSpawn also hands 1-5 % of zombies an iron sword or shovel. Left in place, that weapon came on
		// top of the variant's gear (an "armored" zombie with 3 pieces where the file says 0-2, a runner with a sword).
		// Drowned keep theirs: the trident is their vanilla ranged attack, not gear the variant files describe.
		if (!(zombie instanceof Drowned)) {
			zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		equip(zombie, variant.equipment(), random);
	}

	private static void equip(Zombie zombie, ZombieVariant.Equipment equipment, RandomSource random) {
		if (equipment.sets().isEmpty() || equipment.max() <= 0) {
			return;
		}
		int total = 0;
		for (ZombieVariant.GearSet set : equipment.sets()) {
			total += set.weight();
		}
		int roll = random.nextInt(total);
		ZombieVariant.GearSet chosen = equipment.sets().getFirst();
		for (ZombieVariant.GearSet set : equipment.sets()) {
			roll -= set.weight();
			if (roll < 0) {
				chosen = set;
				break;
			}
		}
		int min = Math.min(equipment.min(), equipment.max());
		int pieces = min + random.nextInt(equipment.max() - min + 1);
		List<String> slots = new ArrayList<>(chosen.slots().keySet());
		for (int i = 0; i < pieces && !slots.isEmpty(); i++) {
			String slotName = slots.remove(random.nextInt(slots.size()));
			List<Identifier> items = chosen.slots().get(slotName);
			if (items.isEmpty()) {
				continue;
			}
			EquipmentSlot slot;
			try {
				slot = EquipmentSlot.byName(slotName);
			} catch (IllegalArgumentException e) {
				AshfallDirector.LOGGER.warn("Zombie variant gear slot {} is unknown", slotName);
				continue;
			}
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(items.get(random.nextInt(items.size())));
			if (item.isPresent()) {
				zombie.setItemSlot(slot, new ItemStack(item.get()));
				zombie.setDropChance(slot, equipment.dropChance());
			}
		}
	}
}
