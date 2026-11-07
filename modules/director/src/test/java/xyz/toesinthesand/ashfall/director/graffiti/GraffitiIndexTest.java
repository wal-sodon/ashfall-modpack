package xyz.toesinthesand.ashfall.director.graffiti;

import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.director.spawn.BuildingRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The Graffiti index rules (every mark kept, newest map marks first) and what a Cleared mark should show. */
class GraffitiIndexTest {
	private static GraffitiMark mark(long pos, UUID owner, GraffitiSymbol symbol, long placed) {
		return new GraffitiMark(pos, owner, symbol, Optional.of(7L), 0, placed);
	}

	@Test
	void keepsEveryMarkAndCapsOnlyTheMap() {
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		GraffitiIndex index = new GraffitiIndex(List.of(mark(-1, b, GraffitiSymbol.LOOT, 0)));
		int sprayed = Graffiti.MAP_MARKS * 3;
		for (int i = 0; i < sprayed; i++) {
			index.put(mark(i, a, GraffitiSymbol.CLEARED, 100 + i));
		}
		assertNotNull(index.get(0), "the owner's oldest mark is still kept, so it can still turn stale");
		assertEquals(sprayed + 1, index.marks().size(), "every mark is kept");
		List<GraffitiMark> shown = index.newestOnMap(a, Graffiti.MAP_MARKS);
		assertEquals(Graffiti.MAP_MARKS, shown.size(), "the map shows only the newest");
		assertEquals(sprayed - 1, shown.getFirst().pos(), "newest first");
		assertEquals(1, index.newestOnMap(b, Graffiti.MAP_MARKS).size(), "the other player's mark");
	}

	@Test
	void newestMapMarksFirstSkippingArrows() {
		UUID a = UUID.randomUUID();
		GraffitiIndex index = new GraffitiIndex(List.of());
		index.put(mark(1, a, GraffitiSymbol.CLEARED, 10));
		index.put(mark(2, a, GraffitiSymbol.ARROW_UP, 30));
		index.put(mark(3, a, GraffitiSymbol.DANGER, 20));
		index.put(mark(4, a, GraffitiSymbol.SAFEHOUSE, 5));
		List<GraffitiMark> shown = index.newestOnMap(a, 2);
		assertEquals(List.of(3L, 1L), shown.stream().map(GraffitiMark::pos).toList());
		index.put(mark(1, a, GraffitiSymbol.LOOT, 40));
		assertEquals(4, index.marks().size(), "spraying over a mark replaces it");
		assertEquals(GraffitiSymbol.LOOT, index.newestOnMap(a, 1).getFirst().symbol());
	}

	@Test
	void clearedMarkTruth() {
		BuildingRecord record = new BuildingRecord(7, BuildingRecord.Status.READY, 0, 0, 0, List.of());
		assertEquals(MarkState.FRESH, Graffiti.truth(null, 0), "no record: the building has no population");
		UUID member = UUID.randomUUID();
		record.populated(3, List.of(member));
		assertEquals(MarkState.WARN, Graffiti.truth(record, 0), "populated: movement inside");
		record.memberGone(member);
		assertEquals(MarkState.FRESH, Graffiti.truth(record, 0), "cleared: true");
		record.tickCleared(false, 10, 10);
		assertEquals(MarkState.STALE, Graffiti.truth(record, 0), "refilled since it was sprayed");
		assertEquals(MarkState.WARN, Graffiti.truth(record, 1), "a mark sprayed on the READY building warns");
	}
}
