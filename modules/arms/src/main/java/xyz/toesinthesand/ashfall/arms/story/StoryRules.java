package xyz.toesinthesand.ashfall.arms.story;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * {@code /gamerule ashfall:objective false}: the pure sandbox of endgame-design section 5. With it off the story never
 * prompts anyone: no ending vote when the Heart falls (the world stays without an ending, and an op can still pick
 * one with {@code /ashfall ending set}), no ending titles and no Aftershock announcements. What players use as tools
 * keeps working: the Field Radio, Relay Logs, the Grid HUD and Grid Map, and the grid itself.
 */
public final class StoryRules {
	public static final GameRule<Boolean> OBJECTIVE = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(Identifier.fromNamespaceAndPath("ashfall", "objective"));

	private StoryRules() {
	}

	/** Forces class loading (and so the rule's registration) during mod init. */
	static void init() {
	}

	/** Whether the story may prompt players (the rule, read from the overworld). */
	public static boolean objective(MinecraftServer server) {
		return server == null || server.overworld().getGameRules().get(OBJECTIVE);
	}
}
