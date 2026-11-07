package xyz.toesinthesand.ashfall.director.story;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import xyz.toesinthesand.ashfall.director.infection.InfectionRules;

/**
 * {@code /ashfall story sever|reset} (permission level 2). {@code sever} flips the Sever flag by hand so the
 * Sever-ending infection rule (Antibiotics cure at any stage) can be tested on its own. {@code reset} is the replay
 * command of endgame-design section 5: it clears every story flag and then runs {@link StoryHooks#fireReset}, through
 * which arms resets the ending, the finale and the Black Start bonus, so a group can play the finale again and pick
 * the other ending. Reclaimed districts are kept on purpose: the replay is of the ending, not of the whole arc.
 */
public final class StoryCommand {
	private StoryCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Permission on "story", not the shared root: see DirectorCommand for why.
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("story")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("sever").executes(StoryCommand::sever))
						.then(Commands.literal("reset").executes(StoryCommand::reset))));
	}

	private static int sever(CommandContext<CommandSourceStack> ctx) {
		StoryFlags.get(ctx.getSource().getServer()).set(InfectionRules.SEVER_FLAG, true);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.story.sever"), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		int cleared = reset(ctx.getSource().getServer());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.story.reset", cleared), true);
		return Math.max(1, cleared);
	}

	/** The reset itself, public for gametests: clears the flags, then the listeners' state. @return flags cleared */
	public static int reset(net.minecraft.server.MinecraftServer server) {
		int cleared = StoryFlags.get(server).clear();
		StoryHooks.fireReset(server);
		return cleared;
	}
}
