package xyz.toesinthesand.ashfall.salvage.building;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingIndexTest {
	// One building split across two Arnis tiles, with a shop on the ground floor and a home above.
	private static final String SIDECAR = """
			{"format":1,"arnis_version":"3.3.0",
			 "shells":[
			  {"id":7,"tags":{"building":"yes","name":"Test"},"floors":[10,14],"base_y":10,"top_y":18,"rows":[[0,0,9],[1,0,9]]},
			  {"id":7,"tags":{"building":"yes","name":"Test"},"floors":[10,14],"base_y":10,"top_y":18,"rows":[[1,8,12],[2,0,12]]}],
			 "units":[
			  {"id":7,"floor":0,"floor_y":10,"top_y":13,"use":"Shop(Pharmacy)","rows":[[0,0,5],[1,0,5]]},
			  {"id":7,"floor":0,"floor_y":10,"top_y":13,"use":"Shop(Pharmacy)","rows":[[2,0,5]]},
			  {"id":7,"floor":1,"floor_y":14,"top_y":17,"use":"Home","rows":[[0,0,9]]}]}""";

	@Test
	void mergesTilesById() {
		BuildingIndex index = BuildingIndex.parse(JsonParser.parseString(SIDECAR).getAsJsonObject());
		assertEquals(1, index.size());
		Building building = index.get(7);
		// Rows 0: 0..9, 1: 0..12 (merged 0..9 + 8..12), 2: 0..12
		assertEquals(10 + 13 + 13, building.footprint().area());
		assertEquals(2, building.units().size());
		Unit pharmacy = building.units().stream().filter(u -> u.arnisUse().equals("Shop(Pharmacy)")).findFirst().orElseThrow();
		assertEquals(18, pharmacy.footprint().area(), "the pharmacy's two tile records are merged");
	}

	@Test
	void findsBuildingAndUnitByPosition() {
		BuildingIndex index = BuildingIndex.parse(JsonParser.parseString(SIDECAR).getAsJsonObject());
		Building building = index.at(new BlockPos(3, 11, 1));
		assertNotNull(building);
		assertEquals("Shop(Pharmacy)", building.unitAt(3, 11, 1).arnisUse());
		assertEquals("Home", building.unitAt(3, 15, 0).arnisUse());
		assertNull(building.unitAt(8, 11, 0), "no unit there on the ground floor");
		assertNull(index.at(new BlockPos(20, 11, 1)));
		assertNull(index.at(new BlockPos(3, 40, 1)), "above the roof");
	}

	@Test
	void footprintMergesOverlappingRuns() {
		Footprint f = Footprint.of(List.of(new int[]{0, 5, 9}, new int[]{0, 0, 4}, new int[]{0, 3, 6}));
		assertEquals(10, f.area());
		assertTrue(f.contains(0, 0));
		assertTrue(f.contains(9, 0));
		assertFalse(f.contains(10, 0));
		assertFalse(f.contains(5, 1));
	}
}
