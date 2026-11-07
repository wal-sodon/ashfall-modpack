package xyz.toesinthesand.ashfall.arms.item;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import team.reborn.energy.api.EnergyStorage;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.salvage.admin.OverridePack;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Adds Ashfall drops to entity loot tables, from the data-driven rules in {@code data/<ns>/ashfall/mob_drops/}.
 * <p>
 * Energy Cells are the scarce currency of the weapon economy and must be earned in combat: the shipped rules drop
 * them only from the tough zombie variants and only when a player lands the kill (no mob farms, no environment
 * kills). Ordinary zombies drip-feed a little salvage the same way.
 * <p>
 * The drops go into the real loot tables (as killed-by-player and random-chance pools), so loot viewers and
 * {@code /loot} see them. That needs the rules before the loot tables are built, but the game loads loot tables
 * before any reload listener runs; so {@code LootReloadMixin} hands this class the resource manager at the start
 * of every loot reload and the rules are read right there. The Mob Drops admin screen writes rule files into the
 * world override datapack ({@link OverridePack}) and reloads, which rebuilds the tables.
 * <p>
 * A datapack that replaces an entity's loot table outright (e.g. {@code data/minecraft/loot_table/entities/zombie.json})
 * still wins: this hook only touches built-in tables ({@code source.isBuiltin()}).
 */
public final class MobDrops {
	public static final String DIRECTORY = "ashfall/mob_drops";
	private static final FileToIdConverter FILES = FileToIdConverter.json(DIRECTORY);

	/**
	 * Other mods' entity loot tables that Ashfall empties, keeping only its own rule drops.
	 * <p>
	 * Why in code and not only as a datapack file: a mod's data pack is ordered among the others by id, and
	 * {@code mutantmonsters} sorts after {@code ashfall_arms}, so the empty override shipped at
	 * {@code data/mutantmonsters/loot_table/entities/mutant_zombie.json} lost and the Abomination dropped the
	 * Hulk Hammer again (1.0.0-rc1 server run). The hammer is a strong melee weapon that undercuts Plasma Blades, and
	 * the boss's real loot comes from its lair's own table. A world datapack replacing the table still wins, since
	 * this only touches built-in tables.
	 */
	static final Set<Identifier> EMPTIED_TABLES = Set.of(Identifier.parse("mutantmonsters:entities/mutant_zombie"));

	/** Entity id to its extra drops, from the last loot reload. */
	private static volatile Map<Identifier, List<MobDropRule.Drop>> rules = Map.of();
	/** Entities whose rule file came from the admin override pack. */
	private static volatile Set<Identifier> overridden = Set.of();

	private MobDrops() {
	}

	/** Every rule currently in effect, sorted by entity id. */
	public static Map<Identifier, List<MobDropRule.Drop>> rules() {
		return rules;
	}

	public static boolean isOverridden(Identifier entity) {
		return overridden.contains(entity);
	}

	/** Path of an entity's rule file under {@code data/}: {@code ashfall/ashfall/mob_drops/<ns>/<path>.json}. */
	public static String filePath(Identifier entity) {
		return "ashfall/" + DIRECTORY + "/" + entity.getNamespace() + "/" + entity.getPath() + ".json";
	}

	/** The default loot table of {@code entity} by the vanilla naming rule (also true for modded entities). */
	public static ResourceKey<LootTable> tableFor(Identifier entity) {
		return ResourceKey.create(Registries.LOOT_TABLE, entity.withPrefix("entities/"));
	}

	/**
	 * Reads every rule file. Called by {@code LootReloadMixin} at the start of each loot reload (world load and
	 * {@code /reload}); a broken file is logged and skipped so one typo never takes every drop away.
	 */
	public static void load(ResourceManager manager) {
		Map<Identifier, List<MobDropRule.Drop>> loaded = new TreeMap<>();
		Set<Identifier> fromOverrides = new HashSet<>();
		for (Map.Entry<Identifier, Resource> file : FILES.listMatchingResources(manager).entrySet()) {
			Identifier id = FILES.fileToId(file.getKey());
			try (Reader reader = file.getValue().openAsReader()) {
				MobDropRule rule = MobDropRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
				loaded.computeIfAbsent(rule.entity(), k -> new ArrayList<>()).addAll(rule.drops());
				if (OverridePack.PACK_ID.equals(file.getValue().sourcePackId())) {
					fromOverrides.add(rule.entity());
				}
			} catch (Exception e) {
				AshfallArms.LOGGER.error("Skipping broken mob drop file {} ({}): {}", id, file.getValue().sourcePackId(), e.getMessage());
			}
		}
		loaded.replaceAll((k, v) -> List.copyOf(v));
		rules = Collections.unmodifiableMap(loaded);
		overridden = Set.copyOf(fromOverrides);
		AshfallArms.LOGGER.info("Loaded mob drop rules for {} entities ({} overridden)", loaded.size(), fromOverrides.size());
	}

	public static void init() {
		LootTableEvents.REPLACE.register((key, original, source, registries) -> {
			if (!source.isBuiltin() || !EMPTIED_TABLES.contains(key.identifier())) {
				return null;
			}
			LootTable.Builder builder = LootTable.lootTable().setParamSet(LootContextParamSets.ENTITY)
					.setRandomSequence(key.identifier());
			addRulePools(key, builder);
			return builder.build();
		});
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			// An emptied table already got its rule pools in REPLACE.
			if (!source.isBuiltin() || EMPTIED_TABLES.contains(key.identifier())) {
				return;
			}
			addRulePools(key, tableBuilder);
		});
	}

	/** Adds the rule pools of the entity whose default loot table is {@code key}, if it has a rule. */
	private static void addRulePools(ResourceKey<LootTable> key, LootTable.Builder tableBuilder) {
		if (!key.identifier().getPath().startsWith("entities/")) {
			return;
		}
		Identifier entity = Identifier.fromNamespaceAndPath(key.identifier().getNamespace(),
				key.identifier().getPath().substring("entities/".length()));
		List<MobDropRule.Drop> drops = rules.get(entity);
		if (drops == null) {
			return;
		}
		for (MobDropRule.Drop drop : drops) {
			if (!BuiltInRegistries.ITEM.containsKey(drop.item())) {
				AshfallArms.LOGGER.warn("Mob drop for {} names unknown item {}", entity, drop.item());
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(drop.item());
			var entry = LootItem.lootTableItem(item);
			if (drop.min() != 1 || drop.max() != 1) {
				entry.apply(SetItemCountFunction.setCount(drop.min() == drop.max()
						? ContextIntProviders.exactly(drop.min()) : ContextIntProviders.between(drop.min(), drop.max())));
			}
			if (item instanceof CellPackItem energy) {
				// Dropped cells arrive full (energy items default to empty).
				entry.apply(SetComponentsFunction.setComponent(EnergyStorage.ENERGY_COMPONENT, energy.capacity()));
			}
			LootPool.Builder pool = LootPool.lootPool();
			if (drop.killedByPlayer()) {
				pool.when(LootItemKilledByPlayerCondition.killedByPlayer());
			}
			tableBuilder.withPool(pool.when(LootItemRandomChanceCondition.randomChance(drop.chance())).add(entry));
		}
	}
}
