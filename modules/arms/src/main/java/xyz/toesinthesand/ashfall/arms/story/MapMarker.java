package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A fixed point of interest on the Grid Map and for the Field Radio, from
 * {@code data/<ns>/ashfall/grid_map/*.json}: the Montford safehouse, each district's substation, the Wreck hull.
 * <p>
 * Positions are authored data rather than looked up from Lost Cities at runtime because the client needs them
 * before the chunks exist, and the landmarks are pinned to fixed chunks by the predefined cities anyway. Keep a
 * substation marker on the centre of its landmark's chunks when moving one.
 *
 * @param kind     {@code safehouse}, {@code substation} or {@code hull} (decides the icon)
 * @param district for substations, the district id whose state colours the marker
 * @param x        block x
 * @param z        block z
 */
public record MapMarker(String kind, Optional<String> district, int x, int z) {
	public static final String SAFEHOUSE = "safehouse", SUBSTATION = "substation", HULL = "hull";

	public static final Codec<MapMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(MapMarker::kind),
			Codec.STRING.optionalFieldOf("district").forGetter(MapMarker::district),
			Codec.INT.fieldOf("x").forGetter(MapMarker::x),
			Codec.INT.fieldOf("z").forGetter(MapMarker::z)
	).apply(i, MapMarker::new));

	/** One {@code grid_map} file: a list of markers. */
	public record File(java.util.List<MapMarker> markers) {
		public static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
				MapMarker.CODEC.listOf().fieldOf("markers").forGetter(File::markers)
		).apply(i, File::new));
	}
}
