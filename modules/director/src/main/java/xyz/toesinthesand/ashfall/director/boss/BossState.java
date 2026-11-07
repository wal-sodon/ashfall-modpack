package xyz.toesinthesand.ashfall.director.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Per-world lair records ({@code data/ashfall/ashfall_bosses.dat}): whether each lair's boss has spawned, is alive
 * or was killed, whether its seal is open, and where its boss stands once that was found. Keyed by lair id, so a
 * datapack can add lairs to an existing world and they start out READY.
 */
public final class BossState extends SavedData {
	private static final Codec<LairRules.Status> STATUS_CODEC = StringRepresentable.fromEnum(StatusName::values)
			.xmap(StatusName::status, StatusName::of);

	/** One lair's record; mutable because the runtime updates it in place and then marks the state dirty. */
	public static final class Lair {
		final String id;
		LairRules.Status status;
		Optional<UUID> boss;
		long spawnedTick;
		long defeatedTick;
		int kills;
		boolean unsealed;
		Optional<BlockPos> spawnPos;

		Lair(String id, LairRules.Status status, Optional<UUID> boss, long spawnedTick, long defeatedTick, int kills,
				boolean unsealed, Optional<BlockPos> spawnPos) {
			this.id = id;
			this.status = status;
			this.boss = boss;
			this.spawnedTick = spawnedTick;
			this.defeatedTick = defeatedTick;
			this.kills = kills;
			this.unsealed = unsealed;
			this.spawnPos = spawnPos;
		}

		public String id() {
			return id;
		}

		public LairRules.Status status() {
			return status;
		}

		public Optional<UUID> boss() {
			return boss;
		}

		public long defeatedTick() {
			return defeatedTick;
		}

		public int kills() {
			return kills;
		}

		public boolean unsealed() {
			return unsealed;
		}

		public Optional<BlockPos> spawnPos() {
			return spawnPos;
		}

		static final Codec<Lair> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(l -> l.id),
				STATUS_CODEC.fieldOf("status").forGetter(l -> l.status),
				UUIDUtil.CODEC.optionalFieldOf("boss").forGetter(l -> l.boss),
				Codec.LONG.optionalFieldOf("spawned_tick", 0L).forGetter(l -> l.spawnedTick),
				Codec.LONG.optionalFieldOf("defeated_tick", 0L).forGetter(l -> l.defeatedTick),
				Codec.INT.optionalFieldOf("kills", 0).forGetter(l -> l.kills),
				Codec.BOOL.optionalFieldOf("unsealed", false).forGetter(l -> l.unsealed),
				BlockPos.CODEC.optionalFieldOf("spawn_pos").forGetter(l -> l.spawnPos)
		).apply(i, Lair::new));
	}

	public static final Codec<BossState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Lair.CODEC.listOf().optionalFieldOf("lairs", List.of()).forGetter(s -> List.copyOf(s.lairs.values()))
	).apply(i, BossState::new));

	/** Same fixer choice as the other Ashfall saved data: a schema-less type so no vanilla fix runs. */
	public static final SavedDataType<BossState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_bosses"), BossState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, Lair> lairs = new TreeMap<>();

	public BossState() {
	}

	private BossState(List<Lair> list) {
		list.forEach(l -> lairs.put(l.id, l));
	}

	public static BossState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Lair lair(String id) {
		return lairs.computeIfAbsent(id, k -> new Lair(k, LairRules.Status.READY, Optional.empty(), 0, 0, 0, false, Optional.empty()));
	}

	public Optional<Lair> existing(String id) {
		return Optional.ofNullable(lairs.get(id));
	}

	public Collection<Lair> lairs() {
		return lairs.values();
	}

	void spawned(Lair lair, UUID boss, BlockPos at, long now) {
		lair.status = LairRules.Status.ACTIVE;
		lair.boss = Optional.of(boss);
		lair.spawnedTick = now;
		lair.spawnPos = Optional.of(at.immutable());
		setDirty();
	}

	void defeated(Lair lair, long now) {
		lair.status = LairRules.Status.DEFEATED;
		lair.boss = Optional.empty();
		lair.defeatedTick = now;
		lair.kills++;
		setDirty();
	}

	void unseal(Lair lair) {
		lair.unsealed = true;
		setDirty();
	}

	void resolved(Lair lair, BlockPos at) {
		lair.spawnPos = Optional.of(at.immutable());
		setDirty();
	}

	/** Lower-case names in the file, so a hand-edited save stays readable. */
	private enum StatusName implements StringRepresentable {
		READY(LairRules.Status.READY),
		ACTIVE(LairRules.Status.ACTIVE),
		DEFEATED(LairRules.Status.DEFEATED);

		private final LairRules.Status status;

		StatusName(LairRules.Status status) {
			this.status = status;
		}

		LairRules.Status status() {
			return status;
		}

		static StatusName of(LairRules.Status status) {
			return valueOf(status.name());
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}
}
