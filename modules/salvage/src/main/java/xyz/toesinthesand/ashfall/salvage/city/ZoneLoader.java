package xyz.toesinthesand.ashfall.salvage.city;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<ns>/ashfall/zones/*.json} into {@link ZoneMap}. Data-driven so the layout can be
 * tuned (or replaced by a modpack) without code, and so one file feeds Lost Cities, the Director and the
 * Grid Map alike. Data packs load before any level exists, so worldgen always sees the final layout.
 */
public final class ZoneLoader extends SimpleJsonResourceReloadListener<Zone> {
	public static final Identifier ID = AshfallSalvage.id("zones");

	public ZoneLoader() {
		super(Zone.CODEC, FileToIdConverter.json("ashfall/zones"));
	}

	@Override
	protected void apply(Map<Identifier, Zone> loaded, ResourceManager manager, ProfilerFiller profiler) {
		List<Zone> zones = new ArrayList<>();
		loaded.forEach((id, zone) -> zones.add(zone.withId(id.getPath())));
		ZoneMap.setCurrent(new ZoneMap(zones));
		AshfallSalvage.LOGGER.info("Loaded {} city zones", zones.size());
	}
}
