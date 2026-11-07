package xyz.toesinthesand.ashfall.salvage.building;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The fixed source gametests use in place of a generated city: chunk index, point lookup, chunk list. */
class StaticBuildingSourceTest {
	private static Building box(long id, int minX, int minZ, int maxX, int maxZ, int baseY, int topY) {
		List<int[]> rows = new java.util.ArrayList<>();
		for (int z = minZ; z <= maxZ; z++) {
			rows.add(new int[]{z, minX, maxX});
		}
		Footprint f = Footprint.of(rows);
		return new Building(id, Map.of(), new int[]{baseY}, baseY, topY, f, List.of(new Unit(0, baseY, topY - 1, "Floor", f)));
	}

	@Test
	void indexesByChunkAndPoint() {
		Building a = box(1, 10, 10, 20, 20, 64, 70);
		Building b = box(2, 100, 100, 101, 101, 64, 70);
		StaticBuildingSource source = new StaticBuildingSource("test", List.of(a, b));
		assertEquals(2, source.size());
		assertSame(a, source.get(1));
		assertEquals(List.of(a), source.inChunk(1, 1), "a straddles chunks 0 and 1 on both axes");
		assertEquals(List.of(a), source.inChunk(0, 0));
		assertSame(a, source.at(new BlockPos(15, 66, 15)));
		assertNull(source.at(new BlockPos(15, 80, 15)), "above the roof");
		assertNull(source.at(new BlockPos(30, 66, 30)), "outside the footprint");
		assertEquals(5, source.chunks(0).size(), "4 chunks for a, 1 for b");
	}
}
