package xyz.toesinthesand.ashfall.arms.survivor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Each district's trader ({@code data/ashfall_traders.dat}): who it is while it lives, and from when a new one may come
 * once it is gone (a day-clock time, so nights slept through count). Kept apart from the {@link SurvivorRoster} on purpose, so traders never count toward any cap.
 */
public final class TraderRoster extends SavedData {
	/**
	 * One district's entry: the living trader, or empty and the day-clock time a replacement is due. Saved as
	 * {@code due_clock}; an older save's {@code due_at} (game time) is dropped, so a replacement owed then comes at once.
	 */
	public record Entry(String district, Optional<UUID> trader, long dueAt) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("district").forGetter(Entry::district),
				UUIDUtil.CODEC.optionalFieldOf("trader").forGetter(Entry::trader),
				Codec.LONG.optionalFieldOf("due_clock", 0L).forGetter(Entry::dueAt)
		).apply(i, Entry::new));
	}

	public static final Codec<TraderRoster> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().optionalFieldOf("traders", List.of()).forGetter(r -> List.copyOf(r.entries.values()))
	).apply(i, TraderRoster::new));

	public static final SavedDataType<TraderRoster> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_traders"), TraderRoster::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final TreeMap<String, Entry> entries = new TreeMap<>();

	public TraderRoster() {
	}

	private TraderRoster(List<Entry> list) {
		for (Entry e : list) {
			entries.put(e.district(), e);
		}
	}

	public static TraderRoster get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The living trader of {@code district}, if there is one (it may sit in an unloaded chunk). */
	public Optional<UUID> trader(String district) {
		Entry e = entries.get(district);
		return e == null ? Optional.empty() : e.trader();
	}

	/** Day-clock time from which a new trader may come to {@code district} (0: at once). */
	public long dueAt(String district) {
		Entry e = entries.get(district);
		return e == null ? 0L : e.dueAt();
	}

	/** Makes {@code id} the trader of {@code district}. */
	public void set(String district, UUID id) {
		entries.put(district, new Entry(district, Optional.of(id), 0L));
		setDirty();
	}

	/** {@code id} was killed, turned or removed: a new one is due at {@code dueAt}. Ignored when {@code id} was replaced already. */
	public void lost(String district, UUID id, long dueAt) {
		if (trader(district).filter(id::equals).isPresent()) {
			entries.put(district, new Entry(district, Optional.empty(), dueAt));
			setDirty();
		}
	}

	/** Forgets {@code district} entirely (gametests start from scratch: their world is saved between runs). */
	public void clear(String district) {
		if (entries.remove(district) != null) {
			setDirty();
		}
	}
}
