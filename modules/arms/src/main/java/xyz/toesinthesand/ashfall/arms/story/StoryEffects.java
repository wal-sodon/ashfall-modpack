package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.server.MinecraftServer;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.horde.HordeHooks;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;
import xyz.toesinthesand.ashfall.director.story.StoryHooks;
import xyz.toesinthesand.ashfall.salvage.site.Sites;

import java.util.OptionalLong;

/**
 * Applies the world's ending profile ({@link EndingProfile}) to the rest of the game. Nothing is copied at the moment
 * of the choice: every hook reads {@link StoryState} and the loaded profile live, so a {@code /reload} that rebalances
 * an ending takes effect at once, and {@code /ashfall story reset} undoes everything by forgetting the ending.
 * <ul>
 * <li>Director spawning: {@link StoryHooks#setSpawnMultiplier} (Sever x0.4).</li>
 * <li>Horde nights: {@link HordeHooks#setStory} (Sever: rarer Aftershocks; Bind: weekly growth to the hard cap).</li>
 * <li>Bunker Cores: {@link StoryHooks#setBunkerRadiusBonus} (Bind: powered by the Heart, a bigger haven).</li>
 * <li>Boss lairs: {@link BossLairs#setEndingTickSource}, which starts every lair's post-ending respawn clock and the
 * Bind-only Ascendant's.</li>
 * <li>Aftershock crash sites: {@link Sites#setAftershocks}.</li>
 * <li>In arms itself: free relay charging ({@link #freeRelays}, read by the grid's power check) and attachment unlocks
 * ({@link #unlockAttachments}, read by the Weaponsmith Bench).</li>
 * </ul>
 */
public final class StoryEffects {
	private StoryEffects() {
	}

	static void init() {
		StoryHooks.setSpawnMultiplier(server -> profile(server).spawnMultiplier());
		StoryHooks.setBunkerRadiusBonus(server -> profile(server).bunkerRadiusBonus());
		HordeHooks.setStory(StoryEffects::hordeTier, StoryEffects::waveSize, server -> profile(server).hordeInterval());
		BossLairs.setEndingTickSource(StoryEffects::endingTick);
		Sites.setAftershocks(StoryEffects::aftershocks);
	}

	/** The world's ending. */
	public static StoryEnding ending(MinecraftServer server) {
		return server == null ? StoryEnding.NONE : StoryState.get(server).ending();
	}

	/** The world's ending profile; NEUTRAL before an ending. */
	public static EndingProfile profile(MinecraftServer server) {
		return EndingProfiles.get(ending(server));
	}

	/** Today's world day on the horde day clock. */
	public static long today(MinecraftServer server) {
		return HordeSchedule.day(server.overworld().getOverworldClockTime());
	}

	/** Days since the ending (0 without one). */
	public static long daysSinceEnding(MinecraftServer server) {
		return StoryState.get(server).daysSinceEnding(today(server));
	}

	/** Every Power Relay charges for free (Sever: "every relay becomes a free Grid Point"). */
	public static boolean freeRelays(MinecraftServer server) {
		return server != null && profile(server).freeRelays();
	}

	/** District-gated bench attachments fit anywhere (Bind: Overcharge mods unlocked). */
	public static boolean unlockAttachments(MinecraftServer server) {
		return server != null && profile(server).unlockAttachments();
	}

	/** Bind's horde tier floor today (0 without growth); the Director caps it at tier VI. */
	static int hordeTier(MinecraftServer server) {
		return profile(server).hordeGrowth().map(g -> g.tier(daysSinceEnding(server))).orElse(0);
	}

	/** Bind's wave size multiplier today (1 without growth); the Director caps it. */
	static double waveSize(MinecraftServer server) {
		return profile(server).hordeGrowth().map(g -> g.size(daysSinceEnding(server))).orElse(1.0);
	}

	static OptionalLong endingTick(MinecraftServer server) {
		StoryState state = StoryState.get(server);
		return state.ending().chosen() && state.endingTick() != StoryState.UNSET ? OptionalLong.of(state.endingTick()) : OptionalLong.empty();
	}

	static Sites.Aftershocks aftershocks(MinecraftServer server) {
		EndingProfile.Aftershocks a = profile(server).aftershocks();
		int generations = a.generations(daysSinceEnding(server));
		return generations <= 0 ? Sites.Aftershocks.NONE : new Sites.Aftershocks(generations, a.chanceScale());
	}
}
