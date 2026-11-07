package xyz.toesinthesand.ashfall.salvage.building;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Radio jobs' building pick: only buildings in the ring and passing the filter, and nothing when none qualify. */
class BuildingFinderTest {
	private static Building box(long id, int minX, int minZ, int size) {
		List<int[]> rows = new ArrayList<>();
		for (int z = minZ; z < minZ + size; z++) {
			rows.add(new int[]{z, minX, minX + size - 1});
		}
		Footprint f = Footprint.of(rows);
		return new Building(id, Map.of("kind", id % 2 == 0 ? "even" : "odd"), new int[]{64}, 64, 80, f, List.of());
	}

	/** A 16-block-square building in every chunk of a 1000-block square around the origin. */
	private static StaticBuildingSource city() {
		List<Building> all = new ArrayList<>();
		long id = 0;
		for (int cx = -32; cx < 32; cx++) {
			for (int cz = -32; cz < 32; cz++) {
				all.add(box(id++, cx * 16, cz * 16, 16));
			}
		}
		return new StaticBuildingSource("city", all);
	}

	@Test
	void picksOnlyInTheRingAndPassingTheFilter() {
		StaticBuildingSource source = city();
		BlockPos centre = new BlockPos(0, 64, 0);
		RandomSource random = RandomSource.create(42);
		for (int i = 0; i < 50; i++) {
			Optional<Building> b = BuildingFinder.random(source, centre, 150, 300, x -> x.tags().get("kind").equals("even"), random, 64);
			assertTrue(b.isPresent(), "a full city always has a match");
			double d = BuildingFinder.distanceToBox(b.get().footprint(), centre);
			assertTrue(d >= 150 && d <= 300, "distance " + d + " is in the ring");
			assertEquals("even", b.get().tags().get("kind"));
		}
	}

	@Test
	void emptyWhenNothingQualifies() {
		Optional<Building> b = BuildingFinder.random(city(), BlockPos.ZERO, 150, 300, x -> false, RandomSource.create(1), 64);
		assertTrue(b.isEmpty());
	}

	@Test
	void distanceToBoxIsZeroInside() {
		Footprint f = box(0, 0, 0, 16).footprint();
		assertEquals(0, BuildingFinder.distanceToBox(f, new BlockPos(5, 0, 5)));
		assertEquals(5, BuildingFinder.distanceToBox(f, new BlockPos(20, 0, 5)));
	}
}
