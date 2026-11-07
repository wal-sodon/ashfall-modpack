package xyz.toesinthesand.ashfall.director.spawn;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The user's rule: only zombies and their variants spawn as hostile mobs in the overworld.
 * <ol>
 * <li>Every overworld biome loses its whole {@link MobCategory#MONSTER} spawn list, so new or modded
 * monster types are covered without a list to maintain. Nether and End biomes are untouched.</li>
 * <li>A safety net on entity load refuses any non-zombie overworld monster that still spawns
 * naturally (structure spawn overrides like witch huts and monuments, other mods' spawners that use
 * the natural reason). Spawn eggs, commands, spawners and patrols use other reasons and pass.</li>
 * </ol>
 */
public final class ZombiesOnly {
	/** Monster types the safety net lets through anyway (phantoms are governed by their game rule instead). */
	public static final TagKey<EntityType<?>> EXEMPT = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath("ashfall", "natural_spawn_exempt"));

	private static final Set<EntityType<?>> LOGGED = ConcurrentHashMap.newKeySet();

	private ZombiesOnly() {
	}

	public static void init() {
		BiomeModifications.create(Identifier.fromNamespaceAndPath("ashfall", "zombies_only"))
				.add(ModificationPhase.REMOVALS, overworldBiomes(),
						context -> context.getMobSpawnSettings().clearSpawns(MobCategory.MONSTER));
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> !shouldRefuse(entity, level, reason));
	}

	/**
	 * Every biome that is not a Nether or End biome. {@code foundInOverworld()} alone is not enough:
	 * it only matches biomes the overworld's biome source can generate, and an Arnis world (flat
	 * generator, biomes written per chunk by the map pipeline) or a world whose chunks came from another
	 * generator can hold biomes that source never lists. Those biomes would keep their skeletons and
	 * creepers, so the selector instead removes monsters from anything not known to be Nether or End.
	 */
	static Predicate<BiomeSelectionContext> overworldBiomes() {
		Predicate<BiomeSelectionContext> netherOrEnd = BiomeSelectors.foundInTheNether()
				.or(BiomeSelectors.foundInTheEnd())
				.or(BiomeSelectors.tag(BiomeTags.IS_NETHER))
				.or(BiomeSelectors.tag(BiomeTags.IS_END));
		return BiomeSelectors.foundInOverworld().or(netherOrEnd.negate());
	}

	/** True for zombies and everything in {@code #minecraft:zombies} (other mods add their zombies there, and so do our special infected). */
	public static boolean isZombieFamily(Entity entity) {
		return entity instanceof Zombie || entity.getType().builtInRegistryHolder().is(EntityTypeTags.ZOMBIES);
	}

	/**
	 * The safety-net decision. Only fresh natural or chunk-generation spawns of overworld monsters are
	 * candidates; a null reason means the entity came from disk, which is never touched.
	 */
	public static boolean shouldRefuse(Entity entity, ServerLevel level, EntitySpawnReason reason) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return false;
		}
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		EntityType<?> type = entity.getType();
		if (type.getCategory() != MobCategory.MONSTER || isZombieFamily(entity) || type.builtInRegistryHolder().is(EXEMPT)) {
			return false;
		}
		if (LOGGED.add(type)) {
			AshfallDirector.LOGGER.info("Director: refusing natural overworld spawn of {} (only zombies spawn; logged once per type)",
					EntityType.getKey(type));
		}
		return true;
	}
}
