package xyz.toesinthesand.ashfall.director.spawn;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;

/**
 * The one day number every difficulty rule reads: today on the overworld day clock, counted from 1.
 *
 * <p>Why one clock: until the phase 7 balance pass the ambient caps, the ambient specials' {@code min_day},
 * building populations and {@code /ashfall director status} counted elapsed game time, while horde nights, Breaches
 * and sieges counted the day clock. Sleeping (or {@code /time set}) moves the clock but not game time, so in a world
 * where players sleep the ambient layer fell behind the horde schedule: the phase 4.5 and 6 playtests read "Director:
 * day 1" on clock day 11. The day clock is the right one for difficulty because it is what players experience as
 * "nights survived": every night slept through still counts as a night the city got worse. Game time stays in use
 * only for things that measure real elapsed ticks (timers, cooldowns), never for "what day is it".
 */
public final class DirectorDay {
	private DirectorDay() {
	}

	/** Today for {@code level}'s server: the overworld day clock, the same day {@link HordeSchedule#day} gives. */
	public static int today(ServerLevel level) {
		return fromClock(level.getOverworldClockTime());
	}

	/** Today for the server (always the overworld clock, so every dimension agrees on the day). */
	public static int today(MinecraftServer server) {
		return today(server.overworld());
	}

	/** The day of a day-clock tick, 1 on the first day. Pure, for JUnit. */
	public static int fromClock(long clock) {
		return HordeSchedule.day(clock);
	}
}
