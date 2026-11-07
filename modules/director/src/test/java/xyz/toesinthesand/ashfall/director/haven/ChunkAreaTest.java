package xyz.toesinthesand.ashfall.director.haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkAreaTest {
	@Test
	void montfordSafehouseCoversItsChunkAndNeighboursOnly() {
		ChunkArea safehouse = new ChunkArea(-10, -50, 1);
		assertTrue(safehouse.contains(-160, -800), "north-west corner of the safehouse chunk");
		assertTrue(safehouse.contains(-154, -788), "the spawn hall");
		assertTrue(safehouse.contains(-129, -769), "far corner of the 3x3");
		assertFalse(safehouse.contains(-128, -800), "pharmacy chunk (-8, -50) stays outside");
		assertFalse(safehouse.contains(-177, -800), "chunk -12 is outside");
	}

	@Test
	void negativeCoordinatesUseFloorChunks() {
		ChunkArea area = ChunkArea.around(-1, -1, 0);
		assertEquals(-1, area.chunkX());
		assertTrue(area.contains(-16, -16));
		assertFalse(area.contains(0, 0));
	}

	@Test
	void chunksListsTheWholeSquare() {
		ChunkArea area = new ChunkArea(3, -2, 2);
		assertEquals(25, area.chunks().size());
		assertTrue(area.chunks().contains(ChunkArea.pack(5, 0)));
		assertFalse(area.chunks().contains(ChunkArea.pack(6, 0)));
	}

	@Test
	void packMatchesChunkPosLayout() {
		// ChunkPos.pack: x in the low 32 bits, z in the high 32 bits
		assertEquals(0xFFFFFFFFL, ChunkArea.pack(-1, 0));
		assertEquals(0xFFFFFFFF00000000L, ChunkArea.pack(0, -1));
	}

	@Test
	void overlapAndDistance() {
		assertTrue(new ChunkArea(0, 0, 1).overlaps(new ChunkArea(2, 0, 1)));
		assertFalse(new ChunkArea(0, 0, 1).overlaps(new ChunkArea(3, 0, 1)));
		assertEquals(0, new ChunkArea(0, 0, 1).distanceSqTo(8, 8), 1e-9);
		assertEquals(100, new ChunkArea(0, 0, 1).distanceSqTo(18, 8), 1e-9);
	}

	@Test
	void negativeRadiusRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ChunkArea(0, 0, -1));
	}
}
