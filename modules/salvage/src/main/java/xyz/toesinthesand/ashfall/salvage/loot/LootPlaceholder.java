package xyz.toesinthesand.ashfall.salvage.loot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

/**
 * The loot table Lost Cities containers get while the city is generated (plan section 1.7, loot row).
 * <p>
 * Lost Cities fills chests from its loot conditions during worldgen: {@code lostcities:chestloot} hands out
 * {@code lostcities:} and {@code minecraft:} tables (village, temple, end city...), Modern Tweaks its own
 * {@code lcmt:} tables. Lootr turns any container with a loot table into a per-player chest a few ticks after
 * its chunk loads, which can be before our first-load {@code ContainerPass} gets to it, and a Lootr chest would
 * then never refill on our timer and hand every player fresh vanilla loot. So the {@code createLoot} hook swaps
 * every foreign table for {@link #TABLE}, an {@code ashfall:} table that Lootr's config blacklists, and the
 * ContainerPass later replaces it with the building profile's table once it knows the building instance and zone.
 * <p>
 * We chose this over generating {@code data/lostcities/lostcities/conditions/chestloot.json}: overriding another
 * mod's data file depends on data pack order between mod jars, which Fabric does not fix, and a conditions file
 * cannot see our zones anyway, so the ContainerPass would still have to rewrite most chests.
 */
public final class LootPlaceholder {
	/** Generic building loot: harmless if a chunk is never processed, and blacklisted from Lootr. */
	public static final Identifier TABLE = Identifier.fromNamespaceAndPath(AshfallSalvage.NAMESPACE, "buildings/generic");
	/** Namespaces whose tables are ours and are left alone (shared building loot and per-player caches). */
	public static final String CACHE_NAMESPACE = "ashfall_cache";

	private LootPlaceholder() {
	}

	/** The table a Lost Cities container should get instead of {@code table}; ours stay as they are. */
	public static ResourceKey<LootTable> replace(ResourceKey<LootTable> table) {
		if (table == null || isOurs(table.identifier())) {
			return table;
		}
		return ResourceKey.create(Registries.LOOT_TABLE, TABLE);
	}

	public static boolean isOurs(Identifier table) {
		return AshfallSalvage.NAMESPACE.equals(table.getNamespace()) || CACHE_NAMESPACE.equals(table.getNamespace());
	}
}
