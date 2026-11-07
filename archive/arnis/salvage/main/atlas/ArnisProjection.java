package xyz.toesinthesand.ashfall.salvage.atlas;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exact port of Arnis's "local" projection (src/coordinate_system/transformation.rs, v3.3.0), so the
 * post-processor maps OSM coordinates to the same blocks Arnis built them on.
 * <p>
 * Arnis also runs map_transformation::transform_map, but its built-in operator list translates by
 * (+2000, +2000) and then drags (2000, 2000) back to (0, 0), so it nets out to zero. We don't support
 * --rotation or --projection web_mercator; {@link #fromMetadata} rejects worlds generated with them.
 */
public record ArnisProjection(double minLat, double maxLat, double minLon, double maxLon,
							  double scaleFactorX, double scaleFactorZ) {
	private static final double EARTH_RADIUS_M = 6_371_000.0;

	/** Builds the projection the way Arnis does in CoordTransformer::llbbox_to_xzbbox. */
	public static ArnisProjection fromBounds(double minLat, double minLon, double maxLat, double maxLon, double scale) {
		double zMeters = latDistance(minLat, maxLat);
		double xMeters = lonDistance((minLat + maxLat) / 2.0, minLon, maxLon);
		return new ArnisProjection(minLat, maxLat, minLon, maxLon,
				Math.floor(xMeters) * scale, Math.floor(zMeters) * scale);
	}

	/** Reads the metadata.json Arnis writes next to level.dat. */
	public static ArnisProjection fromMetadata(Path metadataJson) throws IOException {
		try (Reader reader = Files.newBufferedReader(metadataJson)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			String projection = json.has("projection") ? json.get("projection").getAsString() : "local";
			if (!projection.equals("local")) {
				throw new IOException("Unsupported Arnis projection '" + projection + "' in " + metadataJson);
			}
			if (json.get("minMcX").getAsInt() != 0 || json.get("minMcZ").getAsInt() != 0) {
				throw new IOException("Arnis world is rotated or offset (min corner is not 0,0): " + metadataJson);
			}
			ArnisProjection projectionFromBounds = fromBounds(
					json.get("minGeoLat").getAsDouble(), json.get("minGeoLon").getAsDouble(),
					json.get("maxGeoLat").getAsDouble(), json.get("maxGeoLon").getAsDouble(),
					json.get("scale").getAsDouble());
			int maxX = json.get("maxMcX").getAsInt();
			int maxZ = json.get("maxMcZ").getAsInt();
			if ((int) projectionFromBounds.scaleFactorX() != maxX || (int) projectionFromBounds.scaleFactorZ() != maxZ) {
				throw new IOException("metadata.json extent " + maxX + "x" + maxZ + " doesn't match the computed "
						+ projectionFromBounds.scaleFactorX() + "x" + projectionFromBounds.scaleFactorZ());
			}
			return projectionFromBounds;
		}
	}

	/** Block X for a longitude. Truncates toward zero like Rust's {@code as i32}. */
	public int blockX(double lon) {
		double relX = (lon - minLon) / (maxLon - minLon);
		return (int) (relX * scaleFactorX);
	}

	/** Block Z for a latitude. North is low Z. Truncates toward zero like Rust's {@code as i32}. */
	public int blockZ(double lat) {
		double relZ = 1.0 - (lat - minLat) / (maxLat - minLat);
		return (int) (relZ * scaleFactorZ);
	}

	/** Haversine optimised for a latitude delta of 0, as in Arnis. */
	static double lonDistance(double lat, double lon1, double lon2) {
		double dLon = Math.toRadians(lon2 - lon1);
		double cosLat = Math.cos(Math.toRadians(lat));
		double a = cosLat * cosLat * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
		return EARTH_RADIUS_M * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
	}

	/** Haversine optimised for a longitude delta of 0, as in Arnis. */
	static double latDistance(double lat1, double lat2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0);
		return EARTH_RADIUS_M * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
	}
}
