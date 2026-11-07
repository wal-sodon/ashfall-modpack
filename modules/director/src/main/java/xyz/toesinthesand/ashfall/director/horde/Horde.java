package xyz.toesinthesand.ashfall.director.horde;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One horde: a leader, its followers and where it is heading. Kept in memory only ({@link Hordes}): after a restart
 * the zombies are still standing together and simply form their hordes again on the first update, which is cheaper
 * and safer than saving entity ids that may no longer exist.
 */
public final class Horde {
	private final int id;
	private UUID leader;
	private final Set<UUID> members = new LinkedHashSet<>();
	private @Nullable Vec3 destination;
	private long destinationUntil;
	/** A horde-night horde is sent after this player (their haven or themselves); null for a horde that formed by itself. */
	private @Nullable UUID chase;

	Horde(int id, UUID leader) {
		this.id = id;
		this.leader = leader;
		members.add(leader);
	}

	public int id() {
		return id;
	}

	public UUID leader() {
		return leader;
	}

	void setLeader(UUID leader) {
		this.leader = leader;
		members.add(leader);
	}

	/** Every member, the leader included (read-only view). */
	public Set<UUID> members() {
		return java.util.Collections.unmodifiableSet(members);
	}

	Set<UUID> mutableMembers() {
		return members;
	}

	public int size() {
		return members.size();
	}

	public boolean isLeader(UUID uuid) {
		return leader.equals(uuid);
	}

	/** Where the leader is walking, or null when the horde idles (its leader then wanders and investigates alone). */
	public @Nullable Vec3 destination() {
		return destination;
	}

	/** Game time the destination is dropped, unless something renews it. */
	public long destinationUntil() {
		return destinationUntil;
	}

	public void setDestination(@Nullable Vec3 destination, long until) {
		this.destination = destination;
		this.destinationUntil = until;
	}

	public @Nullable UUID chase() {
		return chase;
	}

	public void setChase(@Nullable UUID chase) {
		this.chase = chase;
	}
}
