package xyz.toesinthesand.ashfall.director.graffiti;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every overworld Graffiti mark by position, saved inside {@code DirectorState}: what lets a Cleared mark turn stale
 * when its building refills, and what the Grid Map is built from. Every mark is kept for as long as its paint is on
 * the wall, so an old Cleared mark still turns stale when its building refills; only what the map shows is capped
 * ({@link #newestOnMap}). One small record per mark, so even thousands stay a few hundred KB of saved data.
 * <p>
 * Plain Java so the replacing and "newest first" rules can be unit tested.
 */
public final class GraffitiIndex {
	private final Map<Long, GraffitiMark> marks = new LinkedHashMap<>();

	public GraffitiIndex(Collection<GraffitiMark> saved) {
		saved.forEach(m -> marks.put(m.pos(), m));
	}

	public List<GraffitiMark> marks() {
		return List.copyOf(marks.values());
	}

	public GraffitiMark get(long pos) {
		return marks.get(pos);
	}

	/** Adds or replaces the mark at its position (spraying over a mark replaces it). */
	public void put(GraffitiMark mark) {
		marks.remove(mark.pos());
		marks.put(mark.pos(), mark);
	}

	public boolean remove(long pos) {
		return marks.remove(pos) != null;
	}

	/** Marks sprayed on this building. */
	public List<GraffitiMark> onBuilding(long building) {
		List<GraffitiMark> out = new ArrayList<>();
		for (GraffitiMark mark : marks.values()) {
			if (mark.building().isPresent() && mark.building().get() == building) {
				out.add(mark);
			}
		}
		return out;
	}

	/** Players with marks, in the order they first sprayed one (stable, so synced lists compare equal). */
	public Set<UUID> owners() {
		Set<UUID> out = new LinkedHashSet<>();
		marks.values().forEach(m -> out.add(m.owner()));
		return out;
	}

	/** The owner's newest {@code limit} marks whose symbol shows on the Grid Map, newest first. */
	public List<GraffitiMark> newestOnMap(UUID owner, int limit) {
		List<GraffitiMark> out = new ArrayList<>();
		for (GraffitiMark mark : byOwner(owner)) {
			if (out.size() >= limit) {
				break;
			}
			if (mark.symbol().onMap()) {
				out.add(mark);
			}
		}
		return out;
	}

	/** The owner's marks, newest first; ties (same tick) keep the later insertion first. */
	private List<GraffitiMark> byOwner(UUID owner) {
		List<GraffitiMark> own = new ArrayList<>();
		for (GraffitiMark mark : marks.values()) {
			if (mark.owner().equals(owner)) {
				own.add(mark);
			}
		}
		Collections.reverse(own);
		own.sort(Comparator.comparingLong(GraffitiMark::placed).reversed());
		return own;
	}
}
