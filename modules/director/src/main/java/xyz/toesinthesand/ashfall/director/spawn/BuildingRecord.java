package xyz.toesinthesand.ashfall.director.spawn;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Saved population state of one building, keyed by building id in {@link DirectorState}
 * (for Lost Cities, the packed origin chunk of the building instance, so a multibuilding has one record). A building has no
 * record until a player first comes near it; from then on it cycles
 * {@code POPULATED -> CLEARED -> READY -> POPULATED ...}.
 * <p>
 * Plain Java (no Minecraft types) so the cleared-timer rules can be unit tested.
 */
public final class BuildingRecord {
	public enum Status {
		/** Population spawned; {@link #members()} still has living zombies. */
		POPULATED,
		/** Every member is gone; {@link #clearedTicks()} counts up while no player is near. */
		CLEARED,
		/** The cleared period is over; the next player to come near gets a fresh (smaller) population. */
		READY
	}

	private final long id;
	private Status status;
	private int generation;
	private int baseCount;
	private long clearedTicks;
	private final Set<UUID> members = new LinkedHashSet<>();

	public BuildingRecord(long id, Status status, int generation, int baseCount, long clearedTicks, Collection<UUID> members) {
		this.id = id;
		this.status = status;
		this.generation = generation;
		this.baseCount = baseCount;
		this.clearedTicks = clearedTicks;
		this.members.addAll(members);
	}

	public long id() {
		return id;
	}

	public Status status() {
		return status;
	}

	public int generation() {
		return generation;
	}

	public int baseCount() {
		return baseCount;
	}

	public long clearedTicks() {
		return clearedTicks;
	}

	public Set<UUID> members() {
		return members;
	}

	/** Size of the population this record would spawn next. */
	public int nextCount() {
		return SpawnRules.populationSize(baseCount, generation);
	}

	/**
	 * Records a freshly spawned population. An empty spawn (no valid cell found) goes straight to
	 * CLEARED so the building is not re-tried every second; it gets another chance after the timer.
	 */
	public void populated(int base, Collection<UUID> spawned) {
		this.baseCount = base;
		this.members.clear();
		this.members.addAll(spawned);
		this.clearedTicks = 0;
		this.status = spawned.isEmpty() ? Status.CLEARED : Status.POPULATED;
	}

	/**
	 * A member died, despawned, converted or left the dimension. Returns true if that cleared the
	 * building (the timer starts at zero).
	 */
	public boolean memberGone(UUID uuid) {
		if (!members.remove(uuid) || !members.isEmpty() || status != Status.POPULATED) {
			return false;
		}
		status = Status.CLEARED;
		clearedTicks = 0;
		return true;
	}

	/**
	 * Advances the cleared timer by {@code dt} ticks unless a player is near (the timer pauses, it does
	 * not reset). Returns true when this call ended the cleared period: the record becomes READY and
	 * the next population is one generation (half the size) further.
	 */
	public boolean tickCleared(boolean playerNear, long dt, long clearedDuration) {
		if (status != Status.CLEARED || playerNear) {
			return false;
		}
		clearedTicks += dt;
		if (clearedTicks < clearedDuration) {
			return false;
		}
		clearedTicks = 0;
		generation++;
		status = Status.READY;
		return true;
	}

	/** Admin clear: forget members and start the cleared timer now. */
	public void forceCleared() {
		members.clear();
		clearedTicks = 0;
		status = Status.CLEARED;
	}

	/** Admin repopulate: back to a full-size first population on the next spawn. */
	public void forceReady() {
		members.clear();
		clearedTicks = 0;
		generation = 0;
		status = Status.READY;
	}
}
