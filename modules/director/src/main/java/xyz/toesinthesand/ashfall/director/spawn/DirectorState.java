package xyz.toesinthesand.ashfall.director.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiIndex;
import xyz.toesinthesand.ashfall.director.graffiti.GraffitiMark;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * World-wide Director saved data ({@code data/ashfall/ashfall_director.dat}): whether the one-time
 * game-rule setup already ran, and every building population record by building id
 * (the packed origin chunk of a Lost Cities building instance).
 * <p>
 * The game-rule flag exists so admins can turn patrols, phantoms or traders back on afterwards: the
 * Director only writes those rules the first time it sees a world, never again.
 */
public final class DirectorState extends SavedData {
	private static final Codec<BuildingRecord.Status> STATUS_CODEC = StringRepresentable.fromEnum(StatusName::values)
			.xmap(StatusName::status, StatusName::of);

	private static final Codec<BuildingRecord> RECORD_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(BuildingRecord::id),
			STATUS_CODEC.fieldOf("status").forGetter(BuildingRecord::status),
			Codec.INT.optionalFieldOf("generation", 0).forGetter(BuildingRecord::generation),
			Codec.INT.optionalFieldOf("base", 0).forGetter(BuildingRecord::baseCount),
			Codec.LONG.optionalFieldOf("cleared_ticks", 0L).forGetter(BuildingRecord::clearedTicks),
			UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(r -> List.copyOf(r.members()))
	).apply(i, BuildingRecord::new));

	public static final Codec<DirectorState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("game_rules_applied", false).forGetter(s -> s.gameRulesApplied),
			RECORD_CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(s -> List.copyOf(s.buildings.values())),
			GraffitiMark.CODEC.listOf().optionalFieldOf("graffiti", List.of()).forGetter(s -> s.graffiti.marks())
	).apply(i, DirectorState::new));

	/** Same fixer choice as salvage's ZoneManager: a non-null, schema-less type so no vanilla fix runs. */
	public static final SavedDataType<DirectorState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_director"), DirectorState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private boolean gameRulesApplied;
	private final Map<Long, BuildingRecord> buildings = new TreeMap<>();
	/** Graffiti marks, so Cleared marks can follow their building's record (see graffiti.Graffiti). */
	private final GraffitiIndex graffiti;

	public DirectorState() {
		this.graffiti = new GraffitiIndex(List.of());
	}

	private DirectorState(boolean gameRulesApplied, List<BuildingRecord> records, List<GraffitiMark> marks) {
		this.gameRulesApplied = gameRulesApplied;
		records.forEach(r -> buildings.put(r.id(), r));
		this.graffiti = new GraffitiIndex(marks);
	}

	public static DirectorState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean gameRulesApplied() {
		return gameRulesApplied;
	}

	public void markGameRulesApplied() {
		gameRulesApplied = true;
		setDirty();
	}

	/**
	 * Test hook: forgets that the one-time game rules ran. The gametest server rebuilds its game rules on
	 * every start but keeps saved data in a reused run directory, so the test has to recreate "first
	 * start" itself instead of relying on the real one.
	 */
	public void resetGameRulesAppliedForTesting() {
		gameRulesApplied = false;
		setDirty();
	}

	public BuildingRecord building(long id) {
		return buildings.get(id);
	}

	public BuildingRecord getOrCreate(long id) {
		return buildings.computeIfAbsent(id, k -> new BuildingRecord(k, BuildingRecord.Status.READY, 0, 0, 0, List.of()));
	}

	public GraffitiIndex graffiti() {
		return graffiti;
	}

	public Collection<BuildingRecord> buildings() {
		return buildings.values();
	}

	/** Lower-case names in the file, so a hand-edited save stays readable. */
	private enum StatusName implements StringRepresentable {
		POPULATED(BuildingRecord.Status.POPULATED),
		CLEARED(BuildingRecord.Status.CLEARED),
		READY(BuildingRecord.Status.READY);

		private final BuildingRecord.Status status;

		StatusName(BuildingRecord.Status status) {
			this.status = status;
		}

		BuildingRecord.Status status() {
			return status;
		}

		static StatusName of(BuildingRecord.Status status) {
			return valueOf(status.name());
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
