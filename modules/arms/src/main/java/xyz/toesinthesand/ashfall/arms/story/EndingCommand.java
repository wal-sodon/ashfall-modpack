package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ashfall ending ...}: the vote's buttons and the ending's tools.
 * <ul>
 * <li>{@code vote sever|bind}, {@code confirm} (anyone): what the chat buttons run; only participants of a running
 * vote are counted.</li>
 * <li>{@code status} (anyone): the world's ending and its day.</li>
 * <li>{@code epilogue} (anyone, after the ending): a fresh epilogue from the logs heard so far, since missed Relay
 * Logs stay collectible and "fill in the epilogue" (endgame-design section 5).</li>
 * <li>{@code begin} and {@code set sever|bind} (permission level 2): start the vote with everyone online, or record an
 * ending outright, for testing and for servers that skip the finale. {@code set} during a vote decides it for the
 * vote's online participants, which is also the console's way to close a vote.</li>
 * </ul>
 * The replay reset is {@code /ashfall story reset}, which the Director owns and which reaches {@link Ending#reset}.
 */
public final class EndingCommand {
	private EndingCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// No permission on "ending" itself: the vote buttons must work for every player.
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("ending")
						.then(Commands.literal("vote")
								.then(Commands.literal("sever").executes(ctx -> vote(ctx, StoryEnding.SEVER)))
								.then(Commands.literal("bind").executes(ctx -> vote(ctx, StoryEnding.BIND))))
						.then(Commands.literal("confirm").executes(EndingCommand::confirm))
						.then(Commands.literal("status").executes(EndingCommand::status))
						.then(Commands.literal("epilogue").executes(EndingCommand::epilogue))
						.then(Commands.literal("begin")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(EndingCommand::begin))
						.then(Commands.literal("set")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("sever").executes(ctx -> set(ctx, StoryEnding.SEVER)))
								.then(Commands.literal("bind").executes(ctx -> set(ctx, StoryEnding.BIND))))));
	}

	private static int vote(CommandContext<CommandSourceStack> ctx, StoryEnding choice) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return report(ctx, EndingVote.vote(player, choice), choice);
	}

	private static int confirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return report(ctx, EndingVote.confirm(ctx.getSource().getPlayerOrException()), StoryEnding.NONE);
	}

	private static int report(CommandContext<CommandSourceStack> ctx, EndingVote.Result result, StoryEnding choice) {
		switch (result) {
			case NO_VOTE -> ctx.getSource().sendFailure(Component.translatable("commands.ashfall.ending.no_vote"));
			case NOT_PARTICIPANT -> ctx.getSource().sendFailure(Component.translatable("commands.ashfall.ending.not_participant"));
			case NOTHING_TO_CONFIRM -> ctx.getSource().sendFailure(Component.translatable("commands.ashfall.ending.nothing_to_confirm"));
			case CONFIRM_NEEDED -> {
				return 1;
			}
			case RECORDED -> {
				return 1;
			}
		}
		return 0;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		StoryState state = StoryState.get(server);
		if (!state.ending().chosen()) {
			ctx.getSource().sendSuccess(() -> Component.translatable(EndingVote.active()
					? "commands.ashfall.ending.status_voting" : "commands.ashfall.ending.status_none"), false);
			return 0;
		}
		long since = state.daysSinceEnding(StoryEffects.today(server));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.ending.status",
				Component.translatable(state.ending().translationKey()), state.endingDay(), since, state.participants().size(),
				StoryRules.objective(server)), false);
		return state.ending().ordinal();
	}

	private static int epilogue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		StoryEnding ending = StoryState.get(ctx.getSource().getServer()).ending();
		if (!ending.chosen()) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.ending.no_ending"));
			return 0;
		}
		if (!player.getInventory().add(Epilogue.book(player, ending))) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.ending.inventory_full"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.ending.epilogue",
				RelayLogs.collected(RadioProgress.get(player)), RelayLogs.pool().size()), false);
		return 1;
	}

	private static int begin(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		if (StoryState.get(server).ending().chosen()) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.ending.already"));
			return 0;
		}
		EndingVote.start(server, Ending.voters(server, java.util.List.of()));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.ending.begun"), true);
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx, StoryEnding ending) {
		MinecraftServer server = ctx.getSource().getServer();
		// During a vote the override decides it for the voters, so the console (which cannot click a button) can close
		// a vote and the party at the Heart still gets its books and rewards; otherwise only the caller is rewarded.
		java.util.Set<ServerPlayer> who = new java.util.LinkedHashSet<>(EndingVote.online(server));
		if (ctx.getSource().getPlayer() != null) {
			who.add(ctx.getSource().getPlayer());
		}
		Ending.apply(server, ending, who);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.ending.set",
				Component.translatable(ending.translationKey())), true);
		return 1;
	}
}
