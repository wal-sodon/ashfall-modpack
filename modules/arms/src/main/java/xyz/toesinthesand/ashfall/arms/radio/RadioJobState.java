package xyz.toesinthesand.ashfall.arms.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** What the radio jobs must remember across restarts ({@code data/ashfall_radio_jobs.dat}). Shared by the server. */
public final class RadioJobState extends SavedData {
	public static final Codec<RadioJobState> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.listOf().optionalFieldOf("sets", List.of()).forGetter(s -> s.sets),
			RadioJob.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(s -> s.offers),
			Codec.INT.optionalFieldOf("offer_day", 0).forGetter(s -> s.offerDay),
			RadioJob.CODEC.optionalFieldOf("active").forGetter(s -> s.active),
			BlockPos.CODEC.listOf().optionalFieldOf("cleanups", List.of()).forGetter(s -> s.cleanups)
	).apply(i, RadioJobState::new));

	public static final SavedDataType<RadioJobState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_radio_jobs"), RadioJobState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final List<BlockPos> sets = new ArrayList<>();
	private List<RadioJob> offers = List.of();
	private int offerDay;
	private Optional<RadioJob> active = Optional.empty();
	/** Spots of lost jobs whose chunk was unloaded at the deadline: their beacon or unopened chest goes on next load. */
	private final List<BlockPos> cleanups = new ArrayList<>();

	public RadioJobState() {
	}

	private RadioJobState(List<BlockPos> sets, List<RadioJob> offers, int offerDay, Optional<RadioJob> active, List<BlockPos> cleanups) {
		this.sets.addAll(sets);
		this.offers = List.copyOf(offers);
		this.offerDay = offerDay;
		this.active = active;
		this.cleanups.addAll(cleanups);
	}

	public static RadioJobState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Every Shortwave Set placed in the overworld; offers come in only while there is one. */
	public List<BlockPos> sets() {
		return List.copyOf(sets);
	}

	public void addSet(BlockPos pos) {
		if (!sets.contains(pos)) {
			sets.add(pos.immutable());
			setDirty();
		}
	}

	public void removeSet(BlockPos pos) {
		if (sets.remove(pos)) {
			setDirty();
		}
	}

	public List<RadioJob> offers() {
		return offers;
	}

	/** World day the current offers came in on (they go stale the next morning). */
	public int offerDay() {
		return offerDay;
	}

	public void setOffers(List<RadioJob> value, int day) {
		offers = List.copyOf(value);
		offerDay = day;
		setDirty();
	}

	public Optional<RadioJob> active() {
		return active;
	}

	public void setActive(Optional<RadioJob> value) {
		active = value;
		setDirty();
	}

	/** Spots still waiting to be cleared (see {@link RadioJobs#tick}). */
	public List<BlockPos> cleanups() {
		return List.copyOf(cleanups);
	}

	public void addCleanup(BlockPos pos) {
		if (!cleanups.contains(pos)) {
			cleanups.add(pos.immutable());
			setDirty();
		}
	}

	public void removeCleanup(BlockPos pos) {
		if (cleanups.remove(pos)) {
			setDirty();
		}
	}
}
