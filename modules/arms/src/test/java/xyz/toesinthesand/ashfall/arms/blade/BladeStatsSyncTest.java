package xyz.toesinthesand.ashfall.arms.blade;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import xyz.toesinthesand.ashfall.arms.network.BladeStatsPayload;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The blade stats sync carries every field intact, so a remote co-op client predicts pylon mining with the server's
 * {@code pylon_mining_speed} instead of the built-in default.
 */
class BladeStatsSyncTest {
	@Test
	void payloadRoundTripsEveryField() {
		BladeStats resonant = new BladeStats(11f, 3f, 2, 900, 1, 4, 0.7f, 1.2f, 5f, 14, 2.5f, 16f);
		Identifier id = Identifier.fromNamespaceAndPath("ashfall", "resonant_plasma_blade");
		ByteBuf buf = Unpooled.buffer();
		BladeStatsPayload.CODEC.encode(buf, new BladeStatsPayload(Map.of(id, resonant)));
		BladeStatsPayload back = BladeStatsPayload.CODEC.decode(buf);
		assertEquals(Map.of(id, resonant), back.stats());
		assertEquals(16f, back.stats().get(id).pylonMiningSpeed());
	}
}
