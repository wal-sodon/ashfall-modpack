package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Run-wide counters for the ending's field report ({@code data/ashfall_story_stats.dat}) that no other system keeps:
 * how many players died while infected and how many died at all. Boss kills are already
 * counted per lair by the Director's {@code BossState}, districts by the grid and Relay Logs per player, so they are
 * read from there instead of being counted twice.
 * <p>
 * Never reset by {@code /ashfall story reset}: the report of a replay should still remember the friends lost before.
 */
public final class StoryStats extends SavedData {
	public static final Codec<StoryStats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("risen", 0).forGetter(s -> s.risen),
			Codec.INT.optionalFieldOf("deaths", 0).forGetter(s -> s.deaths)
	).apply(i, StoryStats::new));

	public static final SavedDataType<StoryStats> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("ashfall", "ashfall_story_stats"), StoryStats::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private int risen;
	private int deaths;

	public StoryStats() {
	}

	private StoryStats(int risen, int deaths) {
		this.risen = risen;
		this.deaths = deaths;
	}

	public static StoryStats get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public int risen() {
		return risen;
	}

	public int deaths() {
		return deaths;
	}

	/** Counts one player death; {@code infected} when the player died infected (endgame "friends lost"). */
	public void countDeath(boolean infected) {
		deaths++;
		if (infected) {
			risen++;
		}
		setDirty();
	}
}
