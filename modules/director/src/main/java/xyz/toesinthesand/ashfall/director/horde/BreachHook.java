package xyz.toesinthesand.ashfall.director.horde;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * What the horde scheduler needs from the City Grid for Breach events (endgame-design section 5): which
 * substations a horde may hit, and a way to start the attack. Defined here and implemented in arms, because the
 * grid lives in arms and the Director must not depend on it; arms registers its implementation through
 * {@link HordeHooks#setBreach}. Without arms there is nothing to breach and {@link #NONE} stays in place.
 * <p>
 * The scheduler only decides <em>when</em> and <em>which</em>; how the core is attacked, what a destroyed core
 * costs (the district goes BROKEN) and how it is repaired again all stay in arms.
 */
public interface BreachHook {
	BreachHook NONE = new BreachHook() {
		@Override
		public List<String> targets(MinecraftServer server) {
			return List.of();
		}

		@Override
		public Component name(String id) {
			return Component.literal(id);
		}

		@Override
		public boolean begin(ServerLevel level, String id) {
			return false;
		}
	};

	/** The ids of every substation a Breach may hit right now (reclaimed districts with a known core). */
	List<String> targets(MinecraftServer server);

	/** Display name for the siren and chat. */
	Component name(String id);

	/**
	 * Called after each Breach siren (the warning before nightfall, and the attack itself) so the grid can offer
	 * players a way to the substation. Does nothing by default.
	 */
	default void announce(MinecraftServer server, String id) {
	}

	/** Starts a Breach at {@code id} now. @return false if it could not start (no longer a valid target) */
	boolean begin(ServerLevel level, String id);

	/** Whether a Breach must wait tonight (the grid has a District Siege set or running). */
	default boolean busy(MinecraftServer server) {
		return false;
	}
}
