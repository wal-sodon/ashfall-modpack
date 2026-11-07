package xyz.toesinthesand.ashfall.director.hydration;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirstDataTest {
	@Test
	void newPlayerStartsFull() {
		assertEquals(new ThirstData(20, 5.0f, 0.0f), ThirstData.DEFAULT);
	}

	@Test
	void belowFourExhaustionNothingDrains() {
		ThirstData d = new ThirstData(20, 5.0f, 3.99f);
		assertSame(d, d.drain());
	}

	@Test
	void drainTakesSaturationFirst() {
		ThirstData d = new ThirstData(20, 5.0f, 4.0f).drain();
		assertEquals(new ThirstData(20, 4.0f, 0.0f), d);
	}

	@Test
	void drainTakesThirstWhenSaturationIsGone() {
		ThirstData d = new ThirstData(12, 0.0f, 5.0f).drain();
		assertEquals(12 - 1, d.thirst());
		assertEquals(0.0f, d.saturation());
		assertEquals(1.0f, d.exhaustion(), 1e-6);
	}

	@Test
	void partialSaturationFloorsAtZero() {
		assertEquals(0.0f, new ThirstData(10, 0.4f, 4.0f).drain().saturation());
	}

	@Test
	void thirstNeverGoesNegative() {
		assertEquals(0, new ThirstData(0, 0.0f, 40.0f).drain().thirst());
	}

	@Test
	void exhaustionIsCapped() {
		assertEquals(ThirstData.MAX_EXHAUSTION, ThirstData.DEFAULT.addExhaustion(100).exhaustion());
	}

	@Test
	void drinkingCapsThirstAndSaturationAtThirst() {
		assertEquals(new ThirstData(20, 9.0f, 0.0f), new ThirstData(15, 5.0f, 0.0f).drink(8, 4.0f));
		// a sip at low thirst can not bank more saturation than the bar shows
		assertEquals(new ThirstData(5, 5.0f, 0.0f), new ThirstData(0, 0.0f, 0.0f).drink(5, 9.0f));
	}

	@Test
	void drinkValuesMatchSpec() {
		assertEquals(8, Drink.CLEAN.thirst());
		assertEquals(4.0f, Drink.CLEAN.saturation());
		assertFalse(Drink.CLEAN.dirty());
		assertEquals(5, Drink.DIRTY.thirst());
		assertEquals(1.0f, Drink.DIRTY.saturation());
		assertTrue(Drink.DIRTY.dirty());
	}

	@Test
	void constructorClamps() {
		assertEquals(new ThirstData(20, 20.0f, 40.0f), new ThirstData(99, 99.0f, 99.0f));
		assertEquals(new ThirstData(0, 0.0f, 0.0f), new ThirstData(-1, -1.0f, -1.0f));
	}

	@Test
	void withThirstKeepsSaturationWithinThirst() {
		assertEquals(new ThirstData(3, 3.0f, 0.0f), ThirstData.DEFAULT.withThirst(3));
	}

	@Test
	void effectThresholds() {
		assertTrue(ThirstData.DEFAULT.withThirst(6).blocksRegeneration());
		assertFalse(ThirstData.DEFAULT.withThirst(7).blocksRegeneration());
		assertTrue(ThirstData.DEFAULT.withThirst(0).blocksSprinting());
		assertFalse(ThirstData.DEFAULT.withThirst(1).blocksSprinting());
	}

	@Test
	void dehydrationFloorsFollowStarvation() {
		assertTrue(ThirstData.dehydrationHurts(11, Difficulty.EASY));
		assertFalse(ThirstData.dehydrationHurts(10, Difficulty.EASY));
		assertTrue(ThirstData.dehydrationHurts(2, Difficulty.NORMAL));
		assertFalse(ThirstData.dehydrationHurts(1, Difficulty.NORMAL));
		assertTrue(ThirstData.dehydrationHurts(1, Difficulty.HARD));
	}

	@Test
	void codecRoundTrips() {
		ThirstData d = new ThirstData(7, 2.5f, 3.25f);
		assertEquals(d, ThirstData.CODEC.parse(JsonOps.INSTANCE,
				ThirstData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow()).getOrThrow());
	}

	/** A bad saved value is clamped, not a decode failure that would drop the attachment. */
	@Test
	void codecClampsOutOfRangeSaves() {
		assertEquals(new ThirstData(20, 20.0f, 0.0f), ThirstData.CODEC.parse(JsonOps.INSTANCE,
				com.google.gson.JsonParser.parseString("{\"thirst\":25,\"saturation\":30.0,\"exhaustion\":-2.0}")).getOrThrow());
	}

	@Test
	void streamCodecRoundTrips() {
		ThirstData d = new ThirstData(13, 4.0f, 1.5f);
		ByteBuf buf = Unpooled.buffer();
		ThirstData.STREAM_CODEC.encode(buf, d);
		assertEquals(d, ThirstData.STREAM_CODEC.decode(buf));
	}
}
