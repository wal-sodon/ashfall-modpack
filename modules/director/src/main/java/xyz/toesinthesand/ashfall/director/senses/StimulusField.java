package xyz.toesinthesand.ashfall.director.senses;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The stimuli of one level, indexed for cheap "what reaches me?" queries.
 * <p>
 * Why two stores: almost every stimulus is small (footsteps, doors, a held torch), and those live in a
 * hash of 16x16x16 chunk sections keyed like {@code SectionPos.asLong}, so a query only visits the
 * 5x5x5 sections around the asker. A handful are huge (explosions at 96, gunshots) and would otherwise
 * force every query to scan dozens of sections, so anything wider than {@link #SECTION_INDEX_MAX_RADIUS}
 * goes into a short "broad" list that every query scans linearly. Both stay tiny in practice.
 * <p>
 * Expired entries are filtered out of every query and physically removed by {@link #sweep}, which the
 * level tick calls a few times a second, so nothing ever has to be "un-emitted".
 * <p>
 * Not thread safe: only the server thread touches it.
 */
public final class StimulusField {
	/**
	 * Largest radius kept in the section index. Queries scan two sections either way (at least 32 blocks
	 * of reach), so a stimulus up to this radius, stretched by at most {@link #MAX_REACH_SCALE}, is always
	 * found: 24 x 1.3 = 31.2 &lt; 32.
	 */
	public static final double SECTION_INDEX_MAX_RADIUS = 24.0;
	/** Upper bound for the per-mob hearing multiplier a query may apply (see {@link #query}). */
	public static final double MAX_REACH_SCALE = 1.3;
	/** Hard cap per level; beyond it new stimuli are dropped until something expires. */
	public static final int MAX_STIMULI = 1024;
	/** A source re-emitting within this distance of its previous, larger stimulus just refreshes it. */
	private static final double SAME_PLACE_SQR = 4.0 * 4.0;
	private static final int SCAN_SECTIONS = 2;

	private final Long2ObjectMap<List<Stimulus>> sections = new Long2ObjectOpenHashMap<>();
	private final List<Stimulus> broad = new ArrayList<>();
	/** Latest stimulus per (source entity, type), so a refreshing source replaces rather than piles up. */
	private final Long2ObjectMap<Stimulus> bySource = new Long2ObjectOpenHashMap<>();
	private long nextId = 1;
	private int size;

	/**
	 * Adds a stimulus at a point; returns the stimulus now standing for it (possibly an older one that was
	 * refreshed instead), or null when the field is full or the radius is not positive.
	 */
	@Nullable
	public Stimulus emit(StimulusType type, double x, double y, double z, double radius, int sourceId, long now) {
		if (!(radius > 0.0)) {
			return null;
		}
		long expiresAt = now + type.lifetimeTicks();
		if (sourceId != Stimulus.NO_SOURCE) {
			long key = sourceKey(sourceId, type);
			Stimulus previous = bySource.get(key);
			if (previous != null && !previous.isExpired(now)) {
				if (previous.radius() >= radius && previous.distanceSqr(x, y, z) <= SAME_PLACE_SQR) {
					previous.extendTo(expiresAt);
					return previous;
				}
				remove(previous);
			}
		} else {
			// sourceless repeats (automatic fire, a humming relay) refresh what is already there
			Stimulus same = findCovering(type, x, y, z, radius, now);
			if (same != null) {
				same.extendTo(expiresAt);
				return same;
			}
		}
		if (size >= MAX_STIMULI) {
			sweep(now);
			if (size >= MAX_STIMULI) {
				return null;
			}
		}
		Stimulus stimulus = new Stimulus(nextId++, type, x, y, z, radius, sourceId, expiresAt);
		if (radius > SECTION_INDEX_MAX_RADIUS) {
			broad.add(stimulus);
		} else {
			sections.computeIfAbsent(sectionKey(x, y, z), k -> new ArrayList<>(4)).add(stimulus);
		}
		if (sourceId != Stimulus.NO_SOURCE) {
			bySource.put(sourceKey(sourceId, type), stimulus);
		}
		size++;
		return stimulus;
	}

	/**
	 * Every live stimulus that reaches the point: its distance is at most its radius times
	 * {@code reachScale} (a mob's hearing multiplier, clamped to {@link #MAX_REACH_SCALE}).
	 */
	public void query(double x, double y, double z, double reachScale, long now, Consumer<Stimulus> out) {
		double scale = Math.min(Math.max(reachScale, 0.0), MAX_REACH_SCALE);
		for (Stimulus s : broad) {
			reachTest(s, x, y, z, scale, now, out);
		}
		if (sections.isEmpty()) {
			return;
		}
		int sx = section(x);
		int sy = section(y);
		int sz = section(z);
		for (int dx = -SCAN_SECTIONS; dx <= SCAN_SECTIONS; dx++) {
			for (int dz = -SCAN_SECTIONS; dz <= SCAN_SECTIONS; dz++) {
				for (int dy = -SCAN_SECTIONS; dy <= SCAN_SECTIONS; dy++) {
					List<Stimulus> list = sections.get(asLong(sx + dx, sy + dy, sz + dz));
					if (list != null) {
						for (Stimulus s : list) {
							reachTest(s, x, y, z, scale, now, out);
						}
					}
				}
			}
		}
	}

	/**
	 * Like {@link #query} but with a reach scale above {@link #MAX_REACH_SCALE}, for hordes: a group hears and sees
	 * further than one zombie (plan M8). The usual two-section scan only finds stimuli whose stretched radius fits in
	 * 32 blocks, so this one widens the section scan to cover {@link #SECTION_INDEX_MAX_RADIUS} times the scale. It is
	 * called once per horde per update, never per zombie, so the wider scan is cheap.
	 */
	public void queryWide(double x, double y, double z, double reachScale, long now, Consumer<Stimulus> out) {
		double scale = Math.max(reachScale, 0.0);
		for (Stimulus s : broad) {
			reachTest(s, x, y, z, scale, now, out);
		}
		if (sections.isEmpty()) {
			return;
		}
		int scan = Math.max(SCAN_SECTIONS, (int) Math.ceil(SECTION_INDEX_MAX_RADIUS * scale / 16.0));
		int sx = section(x);
		int sy = section(y);
		int sz = section(z);
		for (int dx = -scan; dx <= scan; dx++) {
			for (int dz = -scan; dz <= scan; dz++) {
				for (int dy = -scan; dy <= scan; dy++) {
					List<Stimulus> list = sections.get(asLong(sx + dx, sy + dy, sz + dz));
					if (list != null) {
						for (Stimulus s : list) {
							reachTest(s, x, y, z, scale, now, out);
						}
					}
				}
			}
		}
	}

	/** Convenience form of {@link #query} that collects into a list. */
	public List<Stimulus> query(double x, double y, double z, double reachScale, long now) {
		List<Stimulus> out = new ArrayList<>();
		query(x, y, z, reachScale, now, out::add);
		return out;
	}

	/** Every live stimulus, for the debug renderer and tests. */
	public void forEachActive(long now, Consumer<Stimulus> out) {
		for (Stimulus s : broad) {
			if (!s.isExpired(now)) {
				out.accept(s);
			}
		}
		for (List<Stimulus> list : sections.values()) {
			for (Stimulus s : list) {
				if (!s.isExpired(now)) {
					out.accept(s);
				}
			}
		}
	}

	/** Drops expired stimuli. Cheap when there is nothing to drop. */
	public void sweep(long now) {
		broad.removeIf(s -> expire(s, now));
		Iterator<List<Stimulus>> it = sections.values().iterator();
		while (it.hasNext()) {
			List<Stimulus> list = it.next();
			list.removeIf(s -> expire(s, now));
			if (list.isEmpty()) {
				it.remove();
			}
		}
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public void clear() {
		sections.clear();
		broad.clear();
		bySource.clear();
		size = 0;
	}

	private boolean expire(Stimulus s, long now) {
		if (!s.isExpired(now)) {
			return false;
		}
		size--;
		if (s.sourceId() != Stimulus.NO_SOURCE) {
			bySource.remove(sourceKey(s.sourceId(), s.type()), s);
		}
		return true;
	}

	private void remove(Stimulus s) {
		boolean removed;
		if (s.radius() > SECTION_INDEX_MAX_RADIUS) {
			removed = broad.remove(s);
		} else {
			long key = sectionKey(s.x(), s.y(), s.z());
			List<Stimulus> list = sections.get(key);
			removed = list != null && list.remove(s);
			if (list != null && list.isEmpty()) {
				sections.remove(key);
			}
		}
		if (removed) {
			size--;
		}
		if (s.sourceId() != Stimulus.NO_SOURCE) {
			bySource.remove(sourceKey(s.sourceId(), s.type()), s);
		}
	}

	/** A live sourceless stimulus of this type, at least as large, within a few blocks; or null. */
	@Nullable
	private Stimulus findCovering(StimulusType type, double x, double y, double z, double radius, long now) {
		List<Stimulus> candidates = radius > SECTION_INDEX_MAX_RADIUS ? broad : sections.get(sectionKey(x, y, z));
		if (candidates == null) {
			return null;
		}
		for (Stimulus s : candidates) {
			if (s.type() == type && s.sourceId() == Stimulus.NO_SOURCE && s.radius() >= radius && !s.isExpired(now)
					&& s.distanceSqr(x, y, z) <= SAME_PLACE_SQR) {
				return s;
			}
		}
		return null;
	}

	private static void reachTest(Stimulus s, double x, double y, double z, double scale, long now, Consumer<Stimulus> out) {
		if (s.isExpired(now)) {
			return;
		}
		double reach = s.radius() * scale;
		if (s.distanceSqr(x, y, z) <= reach * reach) {
			out.accept(s);
		}
	}

	private static long sourceKey(int sourceId, StimulusType type) {
		return ((long) sourceId << 8) | type.ordinal();
	}

	private static int section(double coordinate) {
		return ((int) Math.floor(coordinate)) >> 4;
	}

	private static long sectionKey(double x, double y, double z) {
		return asLong(section(x), section(y), section(z));
	}

	/** Same packing as {@code SectionPos.asLong}: 22 bits x, 20 bits y, 22 bits z. */
	private static long asLong(int x, int y, int z) {
		return ((long) x & 0x3FFFFFL) << 42 | ((long) y & 0xFFFFFL) | ((long) z & 0x3FFFFFL) << 20;
	}
}
