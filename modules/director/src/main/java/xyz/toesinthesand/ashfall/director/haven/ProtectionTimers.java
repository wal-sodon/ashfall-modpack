package xyz.toesinthesand.ashfall.director.haven;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Respawn-protection bookkeeping: who is protected until which server tick. Pure (no world, no clock of its
 * own: every call takes "now"), so the timer rules are unit-tested and the gametest only checks the wiring.
 * <p>
 * Rules: protection lasts a fixed number of ticks from the moment it starts; starting again (a respawn-choice
 * teleport) restarts the full duration; attacking anything cancels it at once, because protection exists to
 * get a player out of the respawn spot, not to give a free opening strike.
 */
public final class ProtectionTimers {
	private final Map<UUID, Long> until = new HashMap<>();

	/** Protects {@code player} for {@code ticks} from {@code now}; restarts any running protection. */
	public void start(UUID player, long now, int ticks) {
		if (ticks <= 0) {
			until.remove(player);
			return;
		}
		until.put(player, now + ticks);
	}

	/** Whether {@code player} is protected at tick {@code now}. Expired entries are dropped lazily. */
	public boolean isProtected(UUID player, long now) {
		Long end = until.get(player);
		if (end == null) {
			return false;
		}
		if (now >= end) {
			until.remove(player);
			return false;
		}
		return true;
	}

	/** Ticks of protection left at {@code now}, 0 when none. */
	public int remaining(UUID player, long now) {
		Long end = until.get(player);
		return end == null || now >= end ? 0 : (int) (end - now);
	}

	/** Ends protection early (the player attacked or left); returns whether it was running. */
	public boolean cancel(UUID player, long now) {
		boolean was = isProtected(player, now);
		until.remove(player);
		return was;
	}

	/** Drops every expired entry; called occasionally so players who log out don't linger. */
	public void purge(long now) {
		until.values().removeIf(end -> now >= end);
	}

	public void clear() {
		until.clear();
	}

	public int size() {
		return until.size();
	}
}
