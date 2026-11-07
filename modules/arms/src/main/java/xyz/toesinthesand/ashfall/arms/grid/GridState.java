package xyz.toesinthesand.ashfall.arms.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The City Grid, world-wide ({@code data/ashfall_grid.dat}): every district's loop progress and the Grid %.
 * Shared by the whole server on purpose (endgame-design section 6): any player's progress counts for everyone.
 * <p>
 * State changes go through {@link #update}, which fires {@link GridEvents#DISTRICT_CHANGED} exactly when a
 * district's state really changed, so listeners (sync, Reclaimed effects) never have to diff themselves.
 */
public final class GridState extends SavedData {
	private static final MapCodec<DistrictRecord> LOOP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.xmap(s -> {
				DistrictState st = DistrictState.byId(s);
				return st == null ? DistrictState.DARK : st;
			}, DistrictState::id).optionalFieldOf("state", DistrictState.DARK).forGetter(r -> r.state),
			Codec.INT.optionalFieldOf("couplers", 0).forGetter(r -> r.couplers),
			Codec.INT.optionalFieldOf("cells", 0).forGetter(r -> r.cells),
			Codec.LONG.optionalFieldOf("hold_ticks", 0L).forGetter(r -> r.holdTicks),
			Codec.LONG.listOf().optionalFieldOf("linked", List.of()).forGetter(r -> List.copyOf(r.linked)),
			Codec.LONG.optionalFieldOf("siege_at", DistrictRecord.UNSET).forGetter(r -> r.siegeAt),
			Codec.BOOL.optionalFieldOf("warned", false).forGetter(r -> r.warned),
			Codec.INT.optionalFieldOf("core_hp", 0).forGetter(r -> r.coreHp),
			Codec.INT.optionalFieldOf("waves_spawned", 0).forGetter(r -> r.wavesSpawned),
			Codec.LONG.optionalFieldOf("last_wave_at", 0L).forGetter(r -> r.lastWaveAt),
			Codec.LONG.optionalFieldOf("siege_ticks", 0L).forGetter(r -> r.siegeTicks),
			Codec.LONG.optionalFieldOf("presence_ticks", 0L).forGetter(r -> r.presenceTicks),
			Codec.LONG.optionalFieldOf("reclaimed_at_day", DistrictRecord.UNSET).forGetter(r -> r.reclaimedAtDay),
			Codec.LONG.optionalFieldOf("core_pos", DistrictRecord.NO_CORE).forGetter(r -> r.corePos),
			Codec.BOOL.optionalFieldOf("rewarded", false).forGetter(r -> r.rewarded),
			Codec.BOOL.optionalFieldOf("breach", false).forGetter(r -> r.breach)
	).apply(i, (state, couplers, cells, hold, linked, siegeAt, warned, hp, waves, lastWave, siegeTicks, presence, day, core, rewarded,
			breach) -> {
		DistrictRecord r = new DistrictRecord();
		r.state = state;
		r.couplers = couplers;
		r.cells = cells;
		r.holdTicks = hold;
		r.linked.addAll(linked);
		r.siegeAt = siegeAt;
		r.warned = warned;
		r.coreHp = hp;
		r.wavesSpawned = waves;
		r.lastWaveAt = lastWave;
		r.siegeTicks = siegeTicks;
		r.presenceTicks = presence;
		r.reclaimedAtDay = day;
		r.corePos = core;
		r.rewarded = rewarded;
		r.breach = breach;
		return r;
	}));

	/** Relay wear ({@link GridDecay}), its own object because a record codec takes at most 16 fields. */
	private record Decay(Map<String, Integer> conditions, long wornThroughDay, long flickerSinceDay) {
		static final Decay NONE = new Decay(Map.of(), DistrictRecord.UNSET, DistrictRecord.UNSET);
		static final Codec<Decay> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("relays", Map.of()).forGetter(Decay::conditions),
				Codec.LONG.optionalFieldOf("worn_through_day", DistrictRecord.UNSET).forGetter(Decay::wornThroughDay),
				Codec.LONG.optionalFieldOf("flicker_since_day", DistrictRecord.UNSET).forGetter(Decay::flickerSinceDay)
		).apply(i, Decay::new));

		static Decay of(DistrictRecord r) {
			Map<String, Integer> out = new TreeMap<>();
			r.relayCondition.forEach((chunk, c) -> out.put(Long.toString(chunk), c));
			return new Decay(out, r.wornThroughDay, r.flickerSinceDay);
		}

		DistrictRecord into(DistrictRecord r) {
			conditions.forEach((chunk, c) -> {
				try {
					r.setCondition(Long.parseLong(chunk), c);
				} catch (NumberFormatException ignored) {
					// a hand-edited key; that relay just counts as fully repaired
				}
			});
			r.wornThroughDay = wornThroughDay;
			r.flickerSinceDay = flickerSinceDay;
			return r;
		}
	}

	private static final Codec<DistrictRecord> RECORD_CODEC = RecordCodecBuilder.create(i -> i.group(
			LOOP_CODEC.forGetter(r -> r),
			Decay.CODEC.optionalFieldOf("decay", Decay.NONE).forGetter(Decay::of)
	).apply(i, (r, decay) -> decay.into(r)));

	public static final Codec<GridState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, RECORD_CODEC).optionalFieldOf("districts", Map.of()).forGetter(s -> s.records),
			Codec.INT.optionalFieldOf("black_start_bonus", 0).forGetter(s -> s.blackStartBonus),
			Codec.LONG.optionalFieldOf("horde_checked_day", DistrictRecord.UNSET).forGetter(s -> s.hordeCheckedDay)
	).apply(i, GridState::new));

	/** Same non-null, schema-less fixer choice as the other Ashfall saved data, so no vanilla fix runs. */
	public static final SavedDataType<GridState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_grid"), GridState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, DistrictRecord> records = new TreeMap<>();
	/** Grid % the Black Start adds on top of the districts' 80 (M9.5 sets it; 0 until then). */
	private int blackStartBonus;
	/** The last world day whose horde night {@link GridDecay} has already charged to the relays. */
	private long hordeCheckedDay = DistrictRecord.UNSET;

	public GridState() {
	}

	private GridState(Map<String, DistrictRecord> loaded, int bonus, long hordeChecked) {
		records.putAll(loaded);
		blackStartBonus = bonus;
		hordeCheckedDay = hordeChecked;
	}

	public long hordeCheckedDay() {
		return hordeCheckedDay;
	}

	public void setHordeCheckedDay(long day) {
		if (day != hordeCheckedDay) {
			hordeCheckedDay = day;
			setDirty();
		}
	}

	public static GridState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The district's state; DARK for a district that never progressed. */
	public DistrictState state(String districtId) {
		DistrictRecord r = records.get(districtId);
		return r == null ? DistrictState.DARK : r.state;
	}

	/** The scored districts: the ZoneMap's district zones (plus districts registered by gametests). */
	public Set<String> districts() {
		return GridDistricts.ids();
	}

	/** 20 per reclaimed district, capped at 80, plus the Black Start bonus. */
	public int gridPercent() {
		return DistrictLoop.gridPercent(reclaimedCount(), blackStartBonus);
	}

	public int reclaimedCount() {
		int n = 0;
		for (String id : districts()) {
			if (isReclaimed(id)) {
				n++;
			}
		}
		return n;
	}

	public boolean isReclaimed(String districtId) {
		return districtId != null && state(districtId) == DistrictState.RECLAIMED;
	}

	/** World day the district was reclaimed, or -1. */
	public long reclaimedAtDay(String id) {
		DistrictRecord r = records.get(id);
		return r == null || r.state != DistrictState.RECLAIMED ? DistrictRecord.UNSET : r.reclaimedAtDay;
	}

	public int blackStartBonus() {
		return blackStartBonus;
	}

	public void setBlackStartBonus(int bonus) {
		if (bonus != blackStartBonus) {
			blackStartBonus = bonus;
			setDirty();
		}
	}

	/** The district's record for reading (null if it never progressed). Change it only through {@link #update}. */
	public DistrictRecord record(String districtId) {
		return records.get(districtId);
	}

	/** Every stored record, by district id (read-only). */
	public Map<String, DistrictRecord> records() {
		return Collections.unmodifiableMap(records);
	}

	/**
	 * Runs {@code change} on the district's record (created on demand), marks the data dirty and fires
	 * {@link GridEvents#DISTRICT_CHANGED} if the state changed. @return the record
	 */
	public DistrictRecord update(ServerLevel level, String districtId, Consumer<DistrictRecord> change) {
		DistrictRecord r = records.computeIfAbsent(districtId, k -> new DistrictRecord());
		DistrictState from = r.state;
		change.accept(r);
		setDirty();
		if (r.state != from) {
			GridEvents.DISTRICT_CHANGED.invoker().onChange(level, districtId, from, r.state);
		}
		r.restoring = false; // only meant for the listeners of this one change
		return r;
	}

	/** Every district's state, for the sync payload and the status command. */
	public Map<String, DistrictState> snapshot() {
		Map<String, DistrictState> out = new LinkedHashMap<>();
		for (String id : districts()) {
			out.put(id, state(id));
		}
		return out;
	}
}
