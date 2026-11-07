package xyz.toesinthesand.ashfall.salvage.atlas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArnisProjectionTest {
	// The Asheville test tile, as written to metadata.json by Arnis v3.3.0.
	private static final String TEST_TILE_METADATA = """
			{"minMcX":0,"maxMcX":994,"minMcZ":0,"maxMcZ":1000,"minGeoLat":35.59,"maxGeoLat":35.599,\
			"minGeoLon":-82.557,"maxGeoLon":-82.546,"projection":"local","scale":1.0}""";

	@Test
	void extentMatchesArnisOutput(@TempDir Path dir) throws IOException {
		ArnisProjection projection = ArnisProjection.fromMetadata(write(dir, TEST_TILE_METADATA));
		assertEquals(994.0, projection.scaleFactorX());
		assertEquals(1000.0, projection.scaleFactorZ());
	}

	@Test
	void cornersMapToBlockBounds() {
		ArnisProjection projection = ArnisProjection.fromBounds(35.59, -82.557, 35.599, -82.546, 1.0);
		// North-west corner is the origin; south-east corner is the far extent.
		assertEquals(0, projection.blockX(-82.557));
		assertEquals(0, projection.blockZ(35.599));
		assertEquals(994, projection.blockX(-82.546));
		assertEquals(1000, projection.blockZ(35.59));
	}

	@Test
	void truncatesTowardZeroLikeRust() {
		ArnisProjection projection = ArnisProjection.fromBounds(35.59, -82.557, 35.599, -82.546, 1.0);
		// Slightly west of the bbox gives a small negative relX; Rust's `as i32` truncates it to 0, not -1.
		assertEquals(0, projection.blockX(-82.557 - 1e-7));
		assertEquals(-9, projection.blockX(-82.557 - 0.0001));
	}

	@Test
	void rejectsRotatedOrOffsetWorlds(@TempDir Path dir) throws IOException {
		Path metadata = write(dir, TEST_TILE_METADATA.replace("\"minMcX\":0", "\"minMcX\":5"));
		assertThrows(IOException.class, () -> ArnisProjection.fromMetadata(metadata));
	}

	private static Path write(Path dir, String json) throws IOException {
		Path file = dir.resolve("metadata.json");
		Files.writeString(file, json);
		return file;
	}
}
