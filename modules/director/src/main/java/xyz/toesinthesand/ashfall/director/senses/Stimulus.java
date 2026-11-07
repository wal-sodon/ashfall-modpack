package xyz.toesinthesand.ashfall.director.senses;

/**
 * One active stimulus in a {@link StimulusField}. Plain doubles rather than {@code Vec3} so the field
 * stays a pure data structure that unit tests can drive without a level.
 * <p>
 * Only the expiry is mutable: refreshing a light that is still burning in the same place extends it in
 * place instead of churning the spatial index every 40 ticks.
 */
public final class Stimulus {
	/** {@link #sourceId()} of a stimulus nobody in particular caused (an explosion, a door). */
	public static final int NO_SOURCE = -1;

	private final long id;
	private final StimulusType type;
	private final double x;
	private final double y;
	private final double z;
	private final double radius;
	private final int sourceId;
	private long expiresAt;

	Stimulus(long id, StimulusType type, double x, double y, double z, double radius, int sourceId, long expiresAt) {
		this.id = id;
		this.type = type;
		this.x = x;
		this.y = y;
		this.z = z;
		this.radius = radius;
		this.sourceId = sourceId;
		this.expiresAt = expiresAt;
	}

	/** Unique within its field, so a zombie can remember which stimulus it already investigated. */
	public long id() {
		return id;
	}

	public StimulusType type() {
		return type;
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

	/** How far it carries, in blocks: a zombie further away than this does not perceive it at all. */
	public double radius() {
		return radius;
	}

	/** Entity id of whatever caused it (the player holding a torch), or {@link #NO_SOURCE}. */
	public int sourceId() {
		return sourceId;
	}

	/** Game time after which it is gone. */
	public long expiresAt() {
		return expiresAt;
	}

	public boolean isExpired(long now) {
		return now >= expiresAt;
	}

	void extendTo(long newExpiry) {
		expiresAt = Math.max(expiresAt, newExpiry);
	}

	public double distanceSqr(double px, double py, double pz) {
		double dx = px - x;
		double dy = py - y;
		double dz = pz - z;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public String toString() {
		return String.format("%s#%d(%.1f, %.1f, %.1f r=%.1f until %d)", type, id, x, y, z, radius, expiresAt);
	}
}
