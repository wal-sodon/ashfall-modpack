package xyz.toesinthesand.ashfall.director.horde;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.director.haven.HordeSnapshot;
import xyz.toesinthesand.ashfall.director.haven.HordeSource;

/**
 * The siren's view of our own horde schedule ({@link HordeSchedule}), replacing Undead Nights. The schedule is a pure
 * function of the world seed and the day clock, so the siren can say "tonight" with certainty (no last-minute roll
 * cancels it any more) and nothing has to be polled from another mod.
 * <p>
 * The snapshot is phrased in the siren's existing terms: no grace counter (the first night is in the schedule), and
 * a day counter of 1 on a horde day, which {@link HordeSnapshot#hordeTonight()} reads as "tonight".
 */
public final class ScheduledHordeSource implements HordeSource {
	@Override
	public HordeSnapshot read(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		return snapshot(HordeSchedule.get(server), overworld.getOverworldClockTime(), overworld.getSeed());
	}

	/** The snapshot at day-clock tick {@code clock}. Public for tests. */
	public static HordeSnapshot snapshot(HordeSchedule schedule, long clock, long seed) {
		if (!schedule.enabled()) {
			return HordeSnapshot.NONE;
		}
		int day = HordeSchedule.day(clock);
		int next = schedule.nextHordeDay(day, seed);
		int counter = next == Integer.MAX_VALUE ? Integer.MAX_VALUE : next - day + 1;
		return new HordeSnapshot(schedule.hordeNightAt(clock, seed), counter, 0);
	}
}
