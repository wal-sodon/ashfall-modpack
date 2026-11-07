package xyz.toesinthesand.ashfall.arms.charger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeRateTest {
	private static long total(long from, long ticks, int quarters) {
		long sum = 0;
		for (long t = from; t < from + ticks; t++) {
			sum += ChargeRate.unitsThisTick(t, quarters);
		}
		return sum;
	}

	@Test
	void fieldChargerMovesOneUnitEveryFourTicks() {
		assertEquals(25, total(0, 100, ChargerKind.FIELD_CHARGER.quartersPerSlotTick()));
		assertEquals(25, total(12_345, 100, ChargerKind.FIELD_CHARGER.quartersPerSlotTick()));
		for (long t = 0; t < 40; t++) {
			long units = ChargeRate.unitsThisTick(t, 1);
			assertTrue(units == 0 || units == 1, "never more than one unit in a tick");
		}
	}

	@Test
	void relayMovesTwoUnitsEveryTick() {
		for (long t = 0; t < 40; t++) {
			assertEquals(2, ChargeRate.unitsThisTick(t, ChargerKind.POWER_RELAY.quartersPerSlotTick()));
		}
	}

	@Test
	void anyWindowIsWithinOneUnitOfTheExactRate() {
		for (int q = 1; q <= 9; q++) {
			for (long from = 0; from < 8; from++) {
				for (long ticks = 1; ticks < 30; ticks++) {
					long exact = q * ticks;
					long got = 4 * total(from, ticks, q);
					assertTrue(Math.abs(got - exact) < 4, "q=" + q + " from=" + from + " ticks=" + ticks);
				}
			}
		}
	}

	@Test
	void zeroOrNegativeRateMovesNothing() {
		assertEquals(0, ChargeRate.unitsThisTick(10, 0));
		assertEquals(0, ChargeRate.unitsThisTick(10, -4));
		assertEquals(0, ChargeRate.unitsThisTick(-1, 4));
	}

	@Test
	void cellPullRules() {
		assertTrue(ChargerKind.FIELD_CHARGER.shouldPullFromCell(0));
		assertTrue(ChargerKind.FIELD_CHARGER.shouldPullFromCell(ChargerKind.BUFFER - 1));
		assertTrue(!ChargerKind.FIELD_CHARGER.shouldPullFromCell(ChargerKind.BUFFER));
		// A relay generates enough for every slot, so a full-rate tick never touches the cell.
		long drainPerTick = ChargerKind.CHARGE_SLOTS * ChargerKind.POWER_RELAY.quartersPerSlotTick() / 4;
		assertTrue(ChargerKind.POWER_RELAY.generationPerTick() >= drainPerTick);
		assertTrue(ChargerKind.POWER_RELAY.maxDrainPerTick() == drainPerTick);
		assertTrue(!ChargerKind.POWER_RELAY.shouldPullFromCell(ChargerKind.BUFFER - drainPerTick));
		// After its own generation the relay's buffer always covers a tick, so a stored cell is left alone...
		assertTrue(!ChargerKind.POWER_RELAY.shouldPullFromCell(ChargerKind.POWER_RELAY.generationPerTick()));
		assertTrue(!ChargerKind.POWER_RELAY.shouldPullFromCell(ChargerKind.BUFFER / 2));
		// ...and is only drawn on if the buffer ever can't.
		assertTrue(ChargerKind.POWER_RELAY.shouldPullFromCell(0));
		assertTrue(ChargerKind.POWER_RELAY.shouldPullFromCell(drainPerTick - 1));
	}
}
