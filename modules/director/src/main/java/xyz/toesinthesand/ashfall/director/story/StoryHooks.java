package xyz.toesinthesand.ashfall.director.story;

import net.minecraft.server.MinecraftServer;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * The places the Director's rules bend after the story's ending (endgame-design section 4, "the choice"). The ending
 * and its data-driven profiles live in arms, and the Director must not depend on arms, so arms fills these in at
 * start-up and the Director only ever sees numbers: how much to spawn, how much bigger a Bunker Core's haven is.
 * Without arms (or before an ending) every hook is neutral.
 * <p>
 * Each hook is guarded: a failing hook falls back to the neutral value instead of stopping the spawn loop, the same
 * policy as {@code HordeHooks}.
 */
public final class StoryHooks {
	/** Spawning never drops below this share, so a datapack typo of 0 does not silently turn the Director off. */
	public static final double MIN_SPAWN_MULTIPLIER = 0.05;
	public static final double MAX_SPAWN_MULTIPLIER = 4.0;
	/** A Bunker Core's haven never grows past this many extra chunks on each side (HavenSettings caps the base at 8). */
	public static final int MAX_BUNKER_RADIUS_BONUS = 4;

	private static volatile ToDoubleFunction<MinecraftServer> spawnMultiplier = server -> 1.0;
	private static volatile ToIntFunction<MinecraftServer> bunkerRadiusBonus = server -> 0;
	private static final List<Consumer<MinecraftServer>> RESET_LISTENERS = new CopyOnWriteArrayList<>();

	private StoryHooks() {
	}

	/**
	 * Registers the ending's Director spawn multiplier (Sever: about 0.4, "spawning falls to about 40%"). It scales the
	 * ambient cap and building populations, not the hard caps or horde nights: hordes have their own schedule hook.
	 */
	public static void setSpawnMultiplier(ToDoubleFunction<MinecraftServer> hook) {
		spawnMultiplier = hook == null ? server -> 1.0 : hook;
	}

	/** The current spawn multiplier, clamped to [{@link #MIN_SPAWN_MULTIPLIER}, {@link #MAX_SPAWN_MULTIPLIER}]. */
	public static double spawnMultiplier(MinecraftServer server) {
		if (server == null) {
			return 1.0;
		}
		try {
			double m = spawnMultiplier.applyAsDouble(server);
			return Double.isFinite(m) ? Math.clamp(m, MIN_SPAWN_MULTIPLIER, MAX_SPAWN_MULTIPLIER) : 1.0;
		} catch (RuntimeException e) {
			return 1.0;
		}
	}

	/** Registers the extra chunks every Bunker Core's haven covers (Bind: cores "powered by the Heart"). */
	public static void setBunkerRadiusBonus(ToIntFunction<MinecraftServer> hook) {
		bunkerRadiusBonus = hook == null ? server -> 0 : hook;
	}

	public static int bunkerRadiusBonus(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		try {
			return Math.clamp(bunkerRadiusBonus.applyAsInt(server), 0, MAX_BUNKER_RADIUS_BONUS);
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/**
	 * Adds a listener for {@code /ashfall story reset}. The command lives here (it predates the ending), so the story
	 * state that arms owns is reset through this list rather than by a second command with the same name.
	 */
	public static void onReset(Consumer<MinecraftServer> listener) {
		RESET_LISTENERS.add(listener);
	}

	/** Runs every reset listener; one failing listener does not stop the others. */
	public static void fireReset(MinecraftServer server) {
		for (Consumer<MinecraftServer> listener : RESET_LISTENERS) {
			try {
				listener.accept(server);
			} catch (RuntimeException e) {
				AshfallDirector.LOGGER.error("A story reset listener failed", e);
			}
		}
	}
}
