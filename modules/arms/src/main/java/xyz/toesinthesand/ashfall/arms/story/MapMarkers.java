package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The loaded {@link MapMarker}s, merged from every {@code ashfall/grid_map} file. */
public final class MapMarkers {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "grid_map");
	private static volatile List<MapMarker> markers = List.of();

	private MapMarkers() {
	}

	public static List<MapMarker> all() {
		return markers;
	}

	/** The substation marker of {@code district}, if one is authored. */
	public static Optional<MapMarker> substation(String district) {
		return markers.stream()
				.filter(m -> MapMarker.SUBSTATION.equals(m.kind()) && m.district().map(district::equals).orElse(false))
				.findFirst();
	}

	public static final class Loader extends SimpleJsonResourceReloadListener<MapMarker.File> {
		public Loader() {
			super(MapMarker.File.CODEC, FileToIdConverter.json("ashfall/grid_map"));
		}

		@Override
		protected void apply(Map<Identifier, MapMarker.File> loaded, ResourceManager manager, ProfilerFiller profiler) {
			List<MapMarker> all = new ArrayList<>();
			new TreeMap<>(loaded).values().forEach(f -> all.addAll(f.markers()));
			markers = List.copyOf(all);
			AshfallArms.LOGGER.info("Loaded {} Grid Map markers", all.size());
		}
	}
}
