package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The story's outcome, world-wide ({@code data/ashfall_story_state.dat}): which ending the world is in, when it was
 * chosen, who chose it and the field report numbers frozen at that moment.
 * <p>
 * Separate from the Director's {@code StoryFlags} on purpose: the flags are the cheap facts other rules read
 * ({@code ashfall:sever}), this is the record of the ending itself, which only arms needs. The ending writes both.
 * {@link #reset} (from {@code /ashfall story reset}) forgets the ending so the finale can be played again.
 */
public final class StoryState extends SavedData {
	public static final long UNSET = -1L;

	public static final Codec<StoryState> CODEC = RecordCodecBuilder.create(i -> i.group(
			StoryEnding.CODEC.optionalFieldOf("ending", StoryEnding.NONE).forGetter(s -> s.ending),
			Codec.LONG.optionalFieldOf("ending_day", UNSET).forGetter(s -> s.endingDay),
			Codec.LONG.optionalFieldOf("ending_tick", UNSET).forGetter(s -> s.endingTick),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("participants", List.of()).forGetter(s -> List.copyOf(s.participants)),
			FieldReport.Stats.CODEC.optionalFieldOf("report", FieldReport.Stats.EMPTY).forGetter(s -> s.report),
			Codec.INT.optionalFieldOf("replays", 0).forGetter(s -> s.replays),
			Codec.INT.optionalFieldOf("aftershocks_announced", 0).forGetter(s -> s.aftershocksAnnounced)
	).apply(i, StoryState::new));

	/** Same non-null, schema-less fixer choice as the other Ashfall saved data, so no vanilla fix runs. */
	public static final SavedDataType<StoryState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_story_state"), StoryState::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private StoryEnding ending = StoryEnding.NONE;
	/** World day of the ending (the horde day clock), for "N days after the ending" rules. */
	private long endingDay = UNSET;
	/** Game time of the ending, for the boss lairs' respawn rule (game time so {@code /time set} cannot skip it). */
	private long endingTick = UNSET;
	private final List<UUID> participants = new ArrayList<>();
	private FieldReport.Stats report = FieldReport.Stats.EMPTY;
	/** How many times the story was reset after an ending (shown in the status line; never read by rules). */
	private int replays;
	/** Aftershock crash-site passes already announced, so each one is announced once. */
	private int aftershocksAnnounced;

	public StoryState() {
	}

	private StoryState(StoryEnding ending, long endingDay, long endingTick, List<UUID> participants, FieldReport.Stats report,
			int replays, int aftershocksAnnounced) {
		this.aftershocksAnnounced = aftershocksAnnounced;
		this.ending = ending;
		this.endingDay = endingDay;
		this.endingTick = endingTick;
		this.participants.addAll(participants);
		this.report = report;
		this.replays = replays;
	}

	public static StoryState get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public StoryEnding ending() {
		return ending;
	}

	public long endingDay() {
		return endingDay;
	}

	public long endingTick() {
		return endingTick;
	}

	public List<UUID> participants() {
		return List.copyOf(participants);
	}

	public FieldReport.Stats report() {
		return report;
	}

	public int replays() {
		return replays;
	}

	public int aftershocksAnnounced() {
		return aftershocksAnnounced;
	}

	public void setAftershocksAnnounced(int generations) {
		if (generations != aftershocksAnnounced) {
			aftershocksAnnounced = generations;
			setDirty();
		}
	}

	/** Days since the ending on world day {@code today}; 0 before it or on the day itself. */
	public long daysSinceEnding(long today) {
		return ending.chosen() && endingDay != UNSET ? Math.max(0, today - endingDay) : 0;
	}

	/** Records the ending. */
	public void choose(StoryEnding chosen, long day, long tick, List<UUID> who, FieldReport.Stats stats) {
		ending = chosen;
		endingDay = day;
		endingTick = tick;
		participants.clear();
		participants.addAll(who);
		report = stats;
		aftershocksAnnounced = 0;
		setDirty();
	}

	/** Forgets the ending (the replay command). @return whether there was one */
	public boolean reset() {
		boolean had = ending.chosen();
		if (had) {
			replays++;
		}
		ending = StoryEnding.NONE;
		endingDay = UNSET;
		endingTick = UNSET;
		participants.clear();
		report = FieldReport.Stats.EMPTY;
		aftershocksAnnounced = 0;
		setDirty();
		return had;
	}
}
