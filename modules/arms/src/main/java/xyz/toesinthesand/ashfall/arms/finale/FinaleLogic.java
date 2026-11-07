package xyz.toesinthesand.ashfall.arms.finale;

import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.PauseReason;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Phase;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Status;

/**
 * The Black Start's rules (endgame-design section 4) as pure functions over a {@link FinaleRun}: seating the Uplink,
 * the energy buffer, the Surge clock, breaker holds, Uplink damage and repair, pausing and resuming, and phase ends.
 * No world access, so every transition is unit-tested; {@link FinaleController} supplies "who is near", "how many
 * pylons are left" and the step.
 * <p>
 * <b>Failing costs time, not the run.</b> When the Uplink is knocked out or everyone leaves, the run drops to
 * PAUSED: the Uplink and its crystals stay seated, phases already won stay won (their districts stay lit), broken
 * pylons stay broken. What it costs: {@link FinaleSettings#failSetbackSeconds} of Surge, or the Feedback breakers
 * tripping open again. Feeding a cell or patching with a coupler brings it back once the Uplink is healthy enough.
 */
public final class FinaleLogic {
	/** What a player action did, for the message the player sees. */
	public enum Result {
		/** Accepted. */
		ACCEPTED,
		/** Accepted, and the run came back from PAUSED. */
		RESUMED,
		/** Nothing to do: the buffer is full, the Uplink is at full health. */
		FULL,
		/** The run is not in a state this action belongs to (no Uplink seated, already over). */
		WRONG_STATE
	}

	private FinaleLogic() {
	}

	/** Seats the Uplink in {@code socket}: the run starts in the Surge with the Uplink's own charge and full health. */
	public static Result seat(FinaleRun run, FinaleSettings settings, long socket) {
		if (run.status != Status.IDLE) {
			return Result.WRONG_STATE;
		}
		run.reset();
		run.socket = socket;
		run.status = Status.RUNNING;
		run.phase = Phase.SURGE;
		run.bufferTicks = Math.min(settings.maxBufferTicks(), settings.buffer().startSeconds() * 20L);
		run.uplinkHp = settings.uplink().hp();
		return Result.ACCEPTED;
	}

	/**
	 * The replay reset: the run goes back to IDLE and an Uplink that was seated (running, paused or complete) stays in
	 * its socket, powered down, as {@link FinaleRun#standby}. Calling it again keeps the same standby socket.
	 *
	 * @return true if an Uplink waits in a socket afterwards
	 */
	public static boolean rearm(FinaleRun run) {
		long kept = run.status != Status.IDLE && run.socket != FinaleRun.NONE ? run.socket : run.standby;
		run.reset();
		run.standby = kept;
		return kept != FinaleRun.NONE;
	}

	/**
	 * Powers up the Uplink waiting in {@code socket} after a replay reset: the same fresh start as seating a new one,
	 * with no item used.
	 */
	public static Result restart(FinaleRun run, FinaleSettings settings, long socket) {
		if (run.status != Status.IDLE || run.standby == FinaleRun.NONE || run.standby != socket) {
			return Result.WRONG_STATE;
		}
		return seat(run, settings, socket);
	}

	/** One Energy Cell into the buffer ("each Energy Cell buys 60 seconds"). Refused when it would not fit at all. */
	public static Result feedCell(FinaleRun run, FinaleSettings settings) {
		if (!run.active()) {
			return Result.WRONG_STATE;
		}
		if (run.bufferTicks >= settings.maxBufferTicks()) {
			return Result.FULL;
		}
		run.bufferTicks = Math.min(settings.maxBufferTicks(), run.bufferTicks + settings.cellTicks());
		return tryResume(run, settings) ? Result.RESUMED : Result.ACCEPTED;
	}

	/** One Grid Coupler patched into the Uplink. */
	public static Result patch(FinaleRun run, FinaleSettings settings) {
		if (!run.active()) {
			return Result.WRONG_STATE;
		}
		int max = settings.uplink().hp();
		if (run.uplinkHp >= max) {
			return tryResume(run, settings) ? Result.RESUMED : Result.FULL;
		}
		run.uplinkHp = Math.min(max, run.uplinkHp + settings.uplink().couplerPatch());
		return tryResume(run, settings) ? Result.RESUMED : Result.ACCEPTED;
	}

	/** Health a downed Uplink needs before the run can resume. */
	public static int resumeHp(FinaleSettings settings) {
		return Math.max(1, (int) Math.ceil(settings.uplink().hp() * settings.uplink().resumeFraction()));
	}

	/** Whether a PAUSED run may resume: the Uplink is patched up and the buffer holds some charge. */
	public static boolean canResume(FinaleRun run, FinaleSettings settings) {
		return run.status == Status.PAUSED && run.uplinkHp >= resumeHp(settings) && run.bufferTicks > 0;
	}

	static boolean tryResume(FinaleRun run, FinaleSettings settings) {
		if (!canResume(run, settings)) {
			return false;
		}
		run.status = Status.RUNNING;
		run.pause = PauseReason.NONE;
		run.clocks.reset();
		return true;
	}

	/**
	 * Damages the Uplink; at 0 the run fails into PAUSED. @return true if this damage knocked the Uplink out
	 */
	public static boolean damage(FinaleRun run, FinaleSettings settings, int amount) {
		if (run.status != Status.RUNNING || amount <= 0) {
			return false;
		}
		run.uplinkHp = Math.max(0, run.uplinkHp - amount);
		if (run.uplinkHp == 0) {
			fail(run, settings, PauseReason.UPLINK_DOWN);
			return true;
		}
		return false;
	}

	/** The failure path: PAUSED with the reason, minus the setback. Nothing else is taken. */
	public static void fail(FinaleRun run, FinaleSettings settings, PauseReason reason) {
		if (run.status != Status.RUNNING) {
			return;
		}
		run.status = Status.PAUSED;
		run.pause = reason;
		run.clocks.reset();
		switch (run.phase) {
			case SURGE -> run.phaseTicks = Math.max(0, run.phaseTicks - settings.failSetbackSeconds() * 20L);
			case FEEDBACK -> run.breakers.forEach(b -> {
				b.closed = false;
				b.holdTicks = 0;
				b.idleTicks = 0;
			});
			case HEART -> {
				// the broken pylons are the progress and they stay broken; the Choir heals on its own
			}
		}
	}

	/**
	 * Counts down to "abandoned": with nobody near for {@link FinaleSettings#abandonSeconds} the run pauses (all
	 * players died and respawned elsewhere, or left). @return true if the run paused now
	 */
	public static boolean presence(FinaleRun run, FinaleSettings settings, int playersNear, long step) {
		if (run.status != Status.RUNNING) {
			return false;
		}
		if (playersNear > 0) {
			run.clocks.idle = 0;
			return false;
		}
		run.clocks.idle += step;
		if (run.clocks.idle >= settings.abandonSeconds() * 20L) {
			fail(run, settings, PauseReason.ABANDONED);
			return true;
		}
		return false;
	}

	/**
	 * One Surge step: progress and the buffer move together, and both stop when the buffer is empty ("if the buffer runs
	 * dry, progress pauses"). @return true when the Surge is complete
	 */
	public static boolean tickSurge(FinaleRun run, FinaleSettings settings, long step) {
		if (run.status != Status.RUNNING || run.phase != Phase.SURGE) {
			return false;
		}
		long powered = Math.min(step, run.bufferTicks);
		run.bufferTicks -= powered;
		run.phaseTicks = Math.min(settings.surgeTicks(), run.phaseTicks + powered);
		return run.phaseTicks >= settings.surgeTicks();
	}

	/** Whether the Surge is starved: running, in the Surge, with an empty buffer. */
	public static boolean starved(FinaleRun run) {
		return run.status == Status.RUNNING && run.phase == Phase.SURGE && run.bufferTicks <= 0;
	}

	/**
	 * One step of one breaker. Held, it gains; left alone it keeps its progress for the grace (longer for small
	 * parties, so a solo player can run between two breakers 40 blocks apart), then slips back.
	 *
	 * @return true if this step closed the breaker
	 */
	public static boolean tickBreaker(FinaleRun.Breaker breaker, FinaleSettings settings, boolean held, int players, long step) {
		if (breaker.closed) {
			return false;
		}
		if (held) {
			breaker.idleTicks = 0;
			breaker.holdTicks = Math.min(settings.holdTicks(), breaker.holdTicks + step);
			if (breaker.holdTicks >= settings.holdTicks()) {
				breaker.closed = true;
				return true;
			}
			return false;
		}
		breaker.idleTicks += step;
		long grace = Math.round(settings.breakers().graceSeconds() * 20L * settings.scale().breakerGrace(players));
		if (breaker.idleTicks > grace) {
			breaker.holdTicks = Math.max(0, breaker.holdTicks - Math.round(step * settings.breakers().decay()));
		}
		return false;
	}

	/** Every breaker is closed (and there is at least one). */
	public static boolean breakersClosed(FinaleRun run) {
		return !run.breakers.isEmpty() && run.breakers.stream().allMatch(b -> b.closed);
	}

	/** Records how many pylons are gone; the count never goes down. @return true once every pylon is broken */
	public static boolean pylons(FinaleRun run, int total, int stillStanding) {
		run.pylonsBroken = Math.max(run.pylonsBroken, Math.max(0, total - stillStanding));
		return total > 0 && run.pylonsBroken >= total;
	}

	/**
	 * Ends the current phase: one more band lit, and the next phase starts (or the run is COMPLETE after the Heart).
	 *
	 * @return the Grid % bonus the city stands at now
	 */
	public static int endPhase(FinaleRun run, FinaleSettings settings) {
		run.bandsLit = Math.min(3, run.bandsLit + 1);
		run.clocks.reset();
		if (run.phase == Phase.HEART) {
			run.status = Status.COMPLETE;
		} else {
			run.phase = run.phase.next();
			run.phaseTicks = 0;
		}
		return settings.bonus(run.bandsLit);
	}

	/**
	 * Jumps a running run to {@code phase} (the admin command): earlier phases count as won, so their bands and bonus
	 * apply, and the new phase starts fresh. @return the Grid % bonus now
	 */
	public static int jump(FinaleRun run, FinaleSettings settings, Phase phase) {
		run.phase = phase;
		run.phaseTicks = 0;
		run.status = Status.RUNNING;
		run.pause = PauseReason.NONE;
		run.bandsLit = phase.ordinal();
		run.clocks.reset();
		if (run.uplinkHp <= 0) {
			run.uplinkHp = settings.uplink().hp();
		}
		return settings.bonus(run.bandsLit);
	}

	/** Zombies in one wave for this party size, before the mob cap. */
	public static int waveSize(FinaleSettings settings, int players) {
		return (int) Math.round(settings.waves().size() * settings.scale().waves(players));
	}

	/** Specials in one vent burst for this party size (at least one while vents are on). */
	public static int ventCount(FinaleSettings settings, int players) {
		int base = settings.vents().count();
		return base <= 0 ? 0 : Math.max(1, (int) Math.round(base * settings.scale().waves(players)));
	}

	/** Room under the finale's mob cap. */
	public static int room(FinaleSettings settings, int alive, int wanted) {
		return Math.max(0, Math.min(wanted, settings.waves().maxMobs() - alive));
	}

	/**
	 * Horde zombies the scheduler may keep alive around the Uplink: its per-player cap times the party, never above the
	 * finale's own cap. Why: the scheduler counts its cap around the place it aims at, and every finale wave aims at the
	 * Uplink, so an unscaled cap gave a party of four the same 60 as a solo player and kept {@code max_mobs} out of reach.
	 */
	public static int hordeCap(FinaleSettings settings, int perPlayer, int players) {
		return Math.min(settings.waves().maxMobs(), Math.max(0, perPlayer) * Math.max(1, players));
	}

	/** Seconds between waves in the run's phase (0: none). */
	public static int waveInterval(FinaleSettings settings, Phase phase) {
		return switch (phase) {
			case SURGE -> settings.waves().surgeInterval();
			case FEEDBACK -> settings.waves().feedbackInterval();
			case HEART -> settings.waves().heartInterval();
		};
	}

	/**
	 * Whether a periodic event is due after {@code elapsed} ticks on its clock (the caller restarts the clock when it
	 * fires). An interval of 0 is "never".
	 */
	public static boolean due(long elapsed, int intervalSeconds) {
		return intervalSeconds > 0 && elapsed >= intervalSeconds * 20L;
	}
}
