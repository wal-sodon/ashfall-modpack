package xyz.toesinthesand.ashfall.director.haven;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.Map;
import java.util.TreeMap;

/**
 * The safe-zone list: datapack files under {@code data/<ns>/ashfall/safe_zone/} (id = the file's identifier,
 * e.g. {@code ashfall:montford_safehouse}) merged with this world's in-game changes from {@link HavenState}
 * (zones added by command, datapack zones disabled by command).
 */
public final class SafeZones extends SimpleJsonResourceReloadListener<SafeZone> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "safe_zones");

	private static volatile Map<String, SafeZone> datapack = Map.of();

	public SafeZones() {
		super(SafeZone.CODEC, FileToIdConverter.json("ashfall/safe_zone"));
	}

	@Override
	protected void apply(Map<Identifier, SafeZone> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<String, SafeZone> byId = new TreeMap<>();
		loaded.forEach((id, zone) -> byId.put(id.toString(), zone));
		datapack = byId;
		AshfallDirector.LOGGER.info("Loaded {} safe zones", byId.size());
	}

	/** Datapack zones only, before this world's changes. */
	public static Map<String, SafeZone> datapack() {
		return datapack;
	}

	/** The zones in force on this world: datapack minus disabled, plus zones added in game (which win on a clash). */
	public static Map<String, SafeZone> active(MinecraftServer server) {
		HavenState state = HavenState.get(server);
		Map<String, SafeZone> out = new TreeMap<>();
		datapack.forEach((id, zone) -> {
			if (!state.disabledZones().contains(id)) {
				out.put(id, zone);
			}
		});
		out.putAll(state.customZones());
		return out;
	}

	/** Test hook: replaces the datapack list until the next reload. */
	public static void setDatapackForTesting(Map<String, SafeZone> zones) {
		datapack = new TreeMap<>(zones);
	}
}
