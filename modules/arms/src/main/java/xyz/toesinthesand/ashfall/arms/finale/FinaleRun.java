package xyz.toesinthesand.ashfall.arms.finale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One Black Start run: where it stands and everything it needs to pick up again after a restart. Mutable on
 * purpose, like {@code DistrictRecord}: {@link FinaleLogic} changes it in place and the owner marks its saved data
 * dirty. Positions are packed block positions ({@code BlockPos#asLong}); {@link #NONE} means "not placed yet".
 */
public final class FinaleRun {
	public static final long NONE = Long.MIN_VALUE;

	/** Where the run is in its life. PAUSED is the repairable failure state: nothing is lost but time. */
	public enum Status implements StringRepresentable {
		IDLE, RUNNING, PAUSED, COMPLETE;

		public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The three phases of endgame-design section 4, in order. */
	public enum Phase implements StringRepresentable {
		SURGE, FEEDBACK, HEART;

		public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Phase next() {
			return this == SURGE ? FEEDBACK : HEART;
		}

		public static Phase byId(String id) {
			for (Phase p : values()) {
				if (p.getSerializedName().equals(id)) {
					return p;
				}
			}
			return null;
		}
	}

	/** Why a run is PAUSED, so the status line can say what brings it back. */
	public enum PauseReason implements StringRepresentable {
		NONE, UPLINK_DOWN, ABANDONED;

		public static final Codec<PauseReason> CODEC = StringRepresentable.fromEnum(PauseReason::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A Relay Breaker: where it stands, how long it has been held and whether it is closed again. */
	public static final class Breaker {
		public long pos = NONE;
		public long holdTicks;
		/** Ticks since a player last held it (the grace before progress slips). */
		public long idleTicks;
		public boolean closed;

		static final Codec<Breaker> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("pos", NONE).forGetter(b -> b.pos),
				Codec.LONG.optionalFieldOf("hold_ticks", 0L).forGetter(b -> b.holdTicks),
				Codec.LONG.optionalFieldOf("idle_ticks", 0L).forGetter(b -> b.idleTicks),
				Codec.BOOL.optionalFieldOf("closed", false).forGetter(b -> b.closed)
		).apply(i, (pos, hold, idle, closed) -> {
			Breaker b = new Breaker();
			b.pos = pos;
			b.holdTicks = hold;
			b.idleTicks = idle;
			b.closed = closed;
			return b;
		}));
	}

	/** Counters (ticks since the last of each periodic event), saved so a restart does not reset their rhythm. */
	public static final class Clocks {
		public long wave;
		public long vent;
		public long arc;
		public long stimulus;
		/** Ticks with no living player near the socket. */
		public long idle;

		static final Codec<Clocks> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("wave", 0L).forGetter(c -> c.wave),
				Codec.LONG.optionalFieldOf("vent", 0L).forGetter(c -> c.vent),
				Codec.LONG.optionalFieldOf("arc", 0L).forGetter(c -> c.arc),
				Codec.LONG.optionalFieldOf("stimulus", 0L).forGetter(c -> c.stimulus),
				Codec.LONG.optionalFieldOf("idle", 0L).forGetter(c -> c.idle)
		).apply(i, (wave, vent, arc, stimulus, idle) -> {
			Clocks c = new Clocks();
			c.wave = wave;
			c.vent = vent;
			c.arc = arc;
			c.stimulus = stimulus;
			c.idle = idle;
			return c;
		}));

		void reset() {
			wave = vent = arc = stimulus = idle = 0;
		}
	}

	public Status status = Status.IDLE;
	public Phase phase = Phase.SURGE;
	public PauseReason pause = PauseReason.NONE;
	/** The socket the Uplink is seated in. */
	public long socket = NONE;
	/** Progress within the phase: powered Surge ticks; unused by the other phases, whose goals are blocks. */
	public long phaseTicks;
	/** Charge left in the Uplink's buffer, in ticks of Surge. */
	public long bufferTicks;
	public int uplinkHp;
	public Clocks clocks = new Clocks();
	public List<Breaker> breakers = new ArrayList<>();
	/** Where the Heart pylons were raised (empty until the Heart phase places them). */
	public List<Long> pylons = new ArrayList<>();
	/** Pylons broken so far; never goes down, so broken crystal stays broken through a pause. */
	public int pylonsBroken;
	public Optional<UUID> choir = Optional.empty();
	/** Everyone who stood near the socket while the run was going: they vote on the ending and share the win. */
	public Set<UUID> participants = new LinkedHashSet<>();
	/** Phases finished (0-3); each lit one band of districts. */
	public int bandsLit;
	/**
	 * The socket that holds a powered-down Uplink while the run is IDLE ({@link #NONE}: none). The replay reset
	 * ({@code /ashfall story reset}) leaves the spent Uplink here instead of destroying it, so a group replaying for
	 * the other ending does not have to farm three boss crystals again (black-start spec section 9). It stays in the
	 * socket rather than going to a player because a socket cannot be offline, lose it in a death or a despawning item
	 * stack, or hand out one copy per participant.
	 */
	public long standby = NONE;

	private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

	public static final Codec<FinaleRun> CODEC = RecordCodecBuilder.create(i -> i.group(
			Status.CODEC.optionalFieldOf("status", Status.IDLE).forGetter(r -> r.status),
			Phase.CODEC.optionalFieldOf("phase", Phase.SURGE).forGetter(r -> r.phase),
			PauseReason.CODEC.optionalFieldOf("pause", PauseReason.NONE).forGetter(r -> r.pause),
			Codec.LONG.optionalFieldOf("socket", NONE).forGetter(r -> r.socket),
			Codec.LONG.optionalFieldOf("phase_ticks", 0L).forGetter(r -> r.phaseTicks),
			Codec.LONG.optionalFieldOf("buffer_ticks", 0L).forGetter(r -> r.bufferTicks),
			Codec.INT.optionalFieldOf("uplink_hp", 0).forGetter(r -> r.uplinkHp),
			// No shared default instance: optionalFieldOf(name, default) hands that one object to every run decoded without
			// the field, and omits the field on save while a run still holds it, so a mutable default would leak between
			// runs (and worlds) and its counters would never be written. A fresh Clocks per decode instead.
			Clocks.CODEC.optionalFieldOf("clocks").forGetter(r -> Optional.of(r.clocks)),
			Breaker.CODEC.listOf().optionalFieldOf("breakers", List.of()).forGetter(r -> r.breakers),
			Codec.LONG.listOf().optionalFieldOf("pylons", List.of()).forGetter(r -> r.pylons),
			Codec.INT.optionalFieldOf("pylons_broken", 0).forGetter(r -> r.pylonsBroken),
			UUID_CODEC.optionalFieldOf("choir").forGetter(r -> r.choir),
			UUID_CODEC.listOf().optionalFieldOf("participants", List.of()).forGetter(r -> List.copyOf(r.participants)),
			Codec.INT.optionalFieldOf("bands_lit", 0).forGetter(r -> r.bandsLit),
			Codec.LONG.optionalFieldOf("standby", NONE).forGetter(r -> r.standby)
	).apply(i, (status, phase, pause, socket, phaseTicks, buffer, hp, clocks, breakers, pylons, broken, choir, participants, bands,
			standby) -> {
		FinaleRun r = new FinaleRun();
		r.status = status;
		r.phase = phase;
		r.pause = pause;
		r.socket = socket;
		r.phaseTicks = phaseTicks;
		r.bufferTicks = buffer;
		r.uplinkHp = hp;
		r.clocks = clocks.orElseGet(Clocks::new);
		r.breakers = new ArrayList<>(breakers);
		r.pylons = new ArrayList<>(pylons);
		r.pylonsBroken = broken;
		r.choir = choir;
		r.participants.addAll(participants);
		r.bandsLit = bands;
		r.standby = standby;
		return r;
	}));

	/** Running or paused: the Uplink is seated and the run is not over. */
	public boolean active() {
		return status == Status.RUNNING || status == Status.PAUSED;
	}

	/** Back to a fresh, unseated run (the Uplink is out of the socket again, standby included). */
	public void reset() {
		status = Status.IDLE;
		phase = Phase.SURGE;
		pause = PauseReason.NONE;
		socket = NONE;
		phaseTicks = 0;
		bufferTicks = 0;
		uplinkHp = 0;
		clocks = new Clocks();
		breakers = new ArrayList<>();
		pylons = new ArrayList<>();
		pylonsBroken = 0;
		choir = Optional.empty();
		participants = new LinkedHashSet<>();
		bandsLit = 0;
		standby = NONE;
	}
}
