package xyz.toesinthesand.ashfall.director.infection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One player's infection, kept as a persistent player attachment. It stores progress rather than the stage,
 * so a datapack that moves the stage bands re-stages everyone consistently on the next reload, and so
 * treatments can move progress back without a second clock.
 * <p>
 * Immutable for the same reason as {@code ThirstData}: attachments only notice a change through a new value.
 *
 * @param infected   whether the player carries the infection at all
 * @param progress   ticks of infection survived, not counting paused time
 * @param pauseTicks ticks left on a treatment pause; while above 0 progress does not advance
 */
public record InfectionData(boolean infected, long progress, int pauseTicks) {
	public static final InfectionData HEALTHY = new InfectionData(false, 0L, 0);

	/** Lenient fields with defaults, so a hand-edited or older save never drops the attachment on decode. */
	public static final Codec<InfectionData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("infected", false).forGetter(InfectionData::infected),
			Codec.LONG.optionalFieldOf("progress", 0L).forGetter(InfectionData::progress),
			Codec.INT.optionalFieldOf("pause", 0).forGetter(InfectionData::pauseTicks)
	).apply(i, InfectionData::new));

	public InfectionData {
		progress = Math.max(0L, progress);
		pauseTicks = Math.max(0, pauseTicks);
		if (!infected) {
			progress = 0L;
			pauseTicks = 0;
		}
	}

	public boolean paused() {
		return infected && pauseTicks > 0;
	}
}
