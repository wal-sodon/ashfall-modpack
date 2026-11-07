package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.Map;

/**
 * Loads {@code data/<ns>/ashfall/energy_gun/*.json} into {@link GunStats}, keyed by the gun's item id
 * (the file's namespaced path), and keeps the client's copy.
 * <p>
 * Two maps, not one: in single player the integrated server and the client share this JVM, and the
 * client's copy must only ever come from the sync packet so it behaves the same as on a dedicated
 * server. Callers pick the right map through {@link #get(Level, Item)}.
 */
public final class GunStatsManager extends SimpleJsonResourceReloadListener<GunStats> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "energy_guns");
	public static final String DIRECTORY = "ashfall/energy_gun";

	private static volatile Map<Identifier, GunStats> server = Map.of();
	private static volatile Map<Identifier, GunStats> client = Map.of();

	public GunStatsManager() {
		super(GunStats.CODEC, FileToIdConverter.json(DIRECTORY));
	}

	@Override
	protected void apply(Map<Identifier, GunStats> loaded, ResourceManager manager, ProfilerFiller profiler) {
		server = Map.copyOf(loaded);
		AshfallArms.LOGGER.info("Loaded {} energy gun stat files", loaded.size());
	}

	/** Every server-side stat entry (sent to clients on join and after {@code /reload}). */
	public static Map<Identifier, GunStats> serverStats() {
		return server;
	}

	/** Replaces the client copy; called from the sync packet handler. */
	public static void setClientStats(Map<Identifier, GunStats> stats) {
		client = Map.copyOf(stats);
	}

	/** Stats for a gun item on the given side, or null when none are loaded (gun can't fire). */
	public static GunStats get(Level level, Item item) {
		return get(level.isClientSide(), item);
	}

	public static GunStats get(boolean clientSide, Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return (clientSide ? client : server).get(id);
	}

	/** Test hook: replaces the server stats. */
	public static void setServerStatsForTesting(Map<Identifier, GunStats> stats) {
		server = Map.copyOf(stats);
	}
}
