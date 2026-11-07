package xyz.toesinthesand.ashfall.arms.blade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Map;

/**
 * Loads {@code data/<ns>/ashfall/plasma_blade/*.json} into {@link BladeStats}, keyed by the blade's item
 * id, so a datapack can also tune a future second blade (the Bind ending's Resonant blade) separately.
 * Every blade decision (damage, wear, drain) is made on the server. The one client read is mining prediction
 * ({@code getDestroySpeed}), so a remote client gets the server's map through {@code BladeStatsPayload}. One map,
 * not the guns' two: in single player the integrated server and the client share it, and the client copy is only
 * written when there is no integrated server, so a host never overwrites its own server's stats.
 */
public final class BladeStatsManager extends SimpleJsonResourceReloadListener<BladeStats> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "plasma_blades");
	public static final String DIRECTORY = "ashfall/plasma_blade";

	private static volatile Map<Identifier, BladeStats> stats = Map.of();

	public BladeStatsManager() {
		super(BladeStats.CODEC, FileToIdConverter.json(DIRECTORY));
	}

	@Override
	protected void apply(Map<Identifier, BladeStats> loaded, ResourceManager manager, ProfilerFiller profiler) {
		stats = Map.copyOf(loaded);
		AshfallArms.LOGGER.info("Loaded {} plasma blade stat files", loaded.size());
	}

	/** Stats for {@code item}, or the shipped defaults when no file is loaded, so a blade never stops working. */
	public static BladeStats get(Item item) {
		return stats.getOrDefault(BuiltInRegistries.ITEM.getKey(item), BladeStats.DEFAULT);
	}

	/** Every loaded entry, for the sync payload. */
	public static Map<Identifier, BladeStats> all() {
		return stats;
	}

	/** The server's stats on a remote client (from the sync payload); never called with an integrated server. */
	public static void setSynced(Map<Identifier, BladeStats> synced) {
		stats = Map.copyOf(synced);
	}

	/** Whether a datapack file was loaded for {@code item} (tests). */
	public static boolean loaded(Item item) {
		return stats.containsKey(BuiltInRegistries.ITEM.getKey(item));
	}
}
