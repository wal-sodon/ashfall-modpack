package xyz.toesinthesand.ashfall.salvage.atlas;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaAllocatorTest {
	@Test
	void targetRoundsAndClamps() {
		assertEquals(2, QuotaAllocator.target(1.5f, 0, 4, 120));   // 1.8 -> 2
		assertEquals(1, QuotaAllocator.target(0.2f, 1, 4, 100));   // 0.2 -> 0 -> min 1
		assertEquals(4, QuotaAllocator.target(10f, 0, 4, 500));    // 50 -> max 4
		assertEquals(3, QuotaAllocator.target(0f, 3, 1, 36));      // max below min: min wins
	}

	@Test
	void quotasSumToTargetAndFollowShares() {
		Random random = new Random(42);
		for (int trial = 0; trial < 500; trial++) {
			Long2IntMap cells = new Long2IntOpenHashMap();
			int chunks = 1 + random.nextInt(6);
			for (int i = 0; i < chunks; i++) {
				cells.put(ChunkPos.pack(random.nextInt(20) - 10, random.nextInt(20) - 10), 1 + random.nextInt(256));
			}
			int target = random.nextInt(12);
			long total = cells.values().intStream().sum();
			Long2IntMap quota = QuotaAllocator.allocate(target, cells, trial);
			assertEquals(target, quota.values().intStream().sum(), "trial " + trial);
			for (Long2IntMap.Entry e : quota.long2IntEntrySet()) {
				double exact = (double) target * cells.get(e.getLongKey()) / total;
				assertTrue(Math.abs(e.getIntValue() - exact) < 1.0, "share off by a whole container in trial " + trial);
			}
		}
	}

	@Test
	void deterministicRegardlessOfInsertionOrder() {
		long a = ChunkPos.pack(0, 0), b = ChunkPos.pack(1, 0), c = ChunkPos.pack(0, 1);
		Long2IntMap first = new Long2IntOpenHashMap();
		first.put(a, 10);
		first.put(b, 10);
		first.put(c, 10);
		Long2IntMap second = new Long2IntOpenHashMap();
		second.put(c, 10);
		second.put(a, 10);
		second.put(b, 10);
		for (long salt = 0; salt < 50; salt++) {
			assertEquals(QuotaAllocator.allocate(2, first, salt), QuotaAllocator.allocate(2, second, salt));
		}
	}

	@Test
	void tieBreakVariesWithSalt() {
		// Three equal shares of 1 container: who gets it must depend on the unit, not always the same chunk.
		Long2IntMap cells = new Long2IntOpenHashMap();
		long[] keys = {ChunkPos.pack(0, 0), ChunkPos.pack(1, 0), ChunkPos.pack(2, 0)};
		for (long k : keys) {
			cells.put(k, 5);
		}
		int[] wins = new int[3];
		for (long salt = 0; salt < 300; salt++) {
			Long2IntMap quota = QuotaAllocator.allocate(1, cells, salt);
			for (int i = 0; i < 3; i++) {
				wins[i] += quota.get(keys[i]);
			}
		}
		for (int w : wins) {
			assertTrue(w > 50, "tie-break is biased: " + java.util.Arrays.toString(wins));
		}
	}

	@Test
	void emptyAndZeroTargets() {
		assertTrue(QuotaAllocator.allocate(3, new Long2IntOpenHashMap(), 1).isEmpty());
		Long2IntMap cells = new Long2IntOpenHashMap();
		cells.put(ChunkPos.pack(0, 0), 7);
		assertEquals(0, QuotaAllocator.allocate(0, cells, 1).get(ChunkPos.pack(0, 0)));
	}
}
