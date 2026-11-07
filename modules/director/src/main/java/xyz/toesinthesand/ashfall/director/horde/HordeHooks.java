package xyz.toesinthesand.ashfall.director.horde;

import net.minecraft.server.MinecraftServer;

import java.util.function.IntSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * The places the horde scheduler reads from arms, kept as hooks because the grid and the story are in arms and the
 * Director must not depend on arms (arms depends on the Director). Arms fills them in at start-up; without arms the
 * Grid tier is 0 (the day tier always wins), there are no Breach targets and the story changes nothing.
 * <p>
 * The story hooks carry the two endings' horde rules (endgame-design section 4): Sever makes horde nights "rare
 * Aftershocks" (an interval multiplier), Bind lets them "grow weekly up to a hard cap" (a tier floor and a wave size
 * multiplier). Both are clamped here, so no datapack can push past {@link #STORY_TIER_HARD_CAP} or
 * {@link #WAVE_SIZE_HARD_CAP}: those caps are what keeps a LAN host stable (design decision 5).
 */
public final class HordeHooks {
	/** Siege tier VI: the ceiling of the Bind ending's escalation (design decision 5). */
	public static final int STORY_TIER_HARD_CAP = 6;
	/** Waves never grow past twice their table size; the per-player and world caps still apply on top. */
	public static final double WAVE_SIZE_HARD_CAP = 2.0;
	/** Horde nights at most this many times rarer than the schedule. */
	public static final int INTERVAL_HARD_CAP = 10;

	private static volatile IntSupplier gridTier = () -> 0;
	private static volatile BreachHook breach = BreachHook.NONE;
	private static volatile ToIntFunction<MinecraftServer> storyTier = server -> 0;
	private static volatile ToDoubleFunction<MinecraftServer> waveSize = server -> 1.0;
	private static volatile ToIntFunction<MinecraftServer> interval = server -> 1;

	private HordeHooks() {
	}

	/**
	 * Registers the Grid tier: horde tier = max(day tier, Grid tier) (endgame-design section 3), so relighting the
	 * city makes horde nights harder too and a lit city is never a free safe zone.
	 */
	public static void setGridTier(IntSupplier supplier) {
		gridTier = supplier == null ? () -> 0 : supplier;
	}

	/** The current Grid tier, 0 when no grid is installed. Guarded: a failing hook never stops a horde night. */
	public static int gridTier() {
		try {
			return Math.max(0, gridTier.getAsInt());
		} catch (RuntimeException e) {
			return 0;
		}
	}

	public static void setBreach(BreachHook hook) {
		breach = hook == null ? BreachHook.NONE : hook;
	}

	public static BreachHook breach() {
		return breach;
	}

	/** Registers the story's horde rules: a tier floor, a wave size multiplier and an interval multiplier. */
	public static void setStory(ToIntFunction<MinecraftServer> tierFloor, ToDoubleFunction<MinecraftServer> sizeMultiplier,
			ToIntFunction<MinecraftServer> intervalMultiplier) {
		storyTier = tierFloor == null ? server -> 0 : tierFloor;
		waveSize = sizeMultiplier == null ? server -> 1.0 : sizeMultiplier;
		interval = intervalMultiplier == null ? server -> 1 : intervalMultiplier;
	}

	/** The story's tier floor (Bind's weekly growth), clamped to [0, {@link #STORY_TIER_HARD_CAP}]. */
	public static int storyTier(MinecraftServer server) {
		try {
			return server == null ? 0 : Math.clamp(storyTier.applyAsInt(server), 0, STORY_TIER_HARD_CAP);
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** The story's wave size multiplier, clamped to [1, {@link #WAVE_SIZE_HARD_CAP}]: an ending never shrinks waves. */
	public static double waveSizeMultiplier(MinecraftServer server) {
		try {
			double m = server == null ? 1.0 : waveSize.applyAsDouble(server);
			return Double.isFinite(m) ? Math.clamp(m, 1.0, WAVE_SIZE_HARD_CAP) : 1.0;
		} catch (RuntimeException e) {
			return 1.0;
		}
	}

	/** The story's horde-night interval multiplier, clamped to [1, {@link #INTERVAL_HARD_CAP}]. */
	public static int intervalMultiplier(MinecraftServer server) {
		try {
			return server == null ? 1 : Math.clamp(interval.applyAsInt(server), 1, INTERVAL_HARD_CAP);
		} catch (RuntimeException e) {
			return 1;
		}
	}

	/**
	 * The horde tier on world day {@code day}: max(day tier, Grid tier, story tier). The story tier is the Bind
	 * ending's floor; it raises easy days but never lifts the result past what the day and Grid tiers already reach
	 * together with it, and it is itself capped at {@link #STORY_TIER_HARD_CAP}.
	 */
	public static int tier(HordeSchedule schedule, int day, MinecraftServer server) {
		return schedule.tier(day, Math.max(gridTier(), storyTier(server)));
	}
}
