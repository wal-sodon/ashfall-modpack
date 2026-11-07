package xyz.toesinthesand.ashfall.arms.finale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.finale.FinaleRun.Phase;
import xyz.toesinthesand.ashfall.arms.grid.GridState;

import java.util.Arrays;
import java.util.Optional;

/**
 * {@code /ashfall finale status | start [<pos>] | phase <surge|feedback|heart> | abort | open | feed [<cells>] | patch [<couplers>]
 * | unattended <on|off|players>}
 * (permission level 2).
 * <ul>
 *   <li>{@code start} seats an Uplink in the hull's socket (or a socket at {@code pos}, placed if missing) with no
 *   item and no Grid gate, so an admin or a headless test can run the finale on any world.</li>
 *   <li>{@code phase} jumps a running (or paused) run to a phase; earlier phases count as won, so their Grid bonus
 *   applies, their bands relight, and the new phase's blocks and the Choir appear.</li>
 *   <li>{@code abort} takes the finale out of the world and back to IDLE, and the Grid bonus back to 0.</li>
 *   <li>{@code open} opens the hull hatch now (or when its chunk next loads), whatever the Grid says.</li>
 *   <li>{@code feed [<cells>]} and {@code patch [<couplers>]} do at the console what a player does at the socket with
 *   Energy Cells and Grid Couplers, through the same rules (buffer cap, Uplink cap, resuming a paused run), and use no
 *   items. Why: with {@code unattended} stand-ins nobody can feed the buffer or patch a downed Uplink, so a headless
 *   run used to stall for good the first time a wave knocked the Uplink out (1.0.0-rc1 server run), and an admin
 *   helping a stuck party had no way short of {@code phase}.</li>
 *   <li>{@code unattended} lets the world's run go on with nobody there: it counts as one player (or {@code players}) at the Uplink and at
 *   each Relay Breaker ({@link FinaleController#setUnattended}). For headless servers and soak tests; it lasts until
 *   turned off or the server stops.</li>
 * </ul>
 */
public final class FinaleCommand {
	private FinaleCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("finale")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("status").executes(FinaleCommand::status))
						.then(Commands.literal("start").executes(ctx -> start(ctx, Optional.empty()))
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.executes(ctx -> start(ctx, Optional.of(BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
						.then(Commands.literal("phase")
								.then(Commands.argument("phase", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(
												Arrays.stream(Phase.values()).map(Phase::getSerializedName), b))
										.executes(FinaleCommand::phase)))
						.then(Commands.literal("abort").executes(FinaleCommand::abort))
						.then(Commands.literal("open").executes(FinaleCommand::open))
						.then(Commands.literal("feed").executes(ctx -> supply(ctx, false, 1))
								.then(Commands.argument("cells", IntegerArgumentType.integer(1, 20))
										.executes(ctx -> supply(ctx, false, IntegerArgumentType.getInteger(ctx, "cells")))))
						.then(Commands.literal("patch").executes(ctx -> supply(ctx, true, 1))
								.then(Commands.argument("couplers", IntegerArgumentType.integer(1, 20))
										.executes(ctx -> supply(ctx, true, IntegerArgumentType.getInteger(ctx, "couplers")))))
						.then(Commands.literal("unattended")
								.then(Commands.literal("on").executes(ctx -> unattended(ctx, 1)))
								.then(Commands.literal("off").executes(ctx -> unattended(ctx, 0)))
								.then(Commands.argument("players", IntegerArgumentType.integer(1, 8))
										.executes(ctx -> unattended(ctx, IntegerArgumentType.getInteger(ctx, "players")))))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		FinaleInstance main = FinaleController.main(level.getServer());
		FinaleState state = FinaleState.get(level.getServer());
		FinaleRun run = main.run();
		ctx.getSource().sendSuccess(() -> FinaleActions.statusLine(main), false);
		Optional<BlockPos> socket = run.socket != FinaleRun.NONE ? Optional.of(BlockPos.of(run.socket)) : HullSite.socket(level.getServer());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.finale.status_hull",
				socket.map(BlockPos::toShortString).orElse("-"),
				Component.translatable(state.hullOpen() ? "commands.ashfall.finale.hull_open"
						: state.openRequested() ? "commands.ashfall.finale.hull_opening" : "commands.ashfall.finale.hull_sealed"),
				GridState.get(level.getServer()).gridPercent(), run.participants.size()), false);
		return run.status.ordinal();
	}

	private static int start(CommandContext<CommandSourceStack> ctx, Optional<BlockPos> at) throws CommandSyntaxException {
		ServerLevel level = ctx.getSource().getServer().overworld();
		FinaleInstance main = FinaleController.main(level.getServer());
		if (main.run().status != FinaleRun.Status.IDLE) {
			// A finished run is not "running": say how to replay it instead of asking for an abort.
			ctx.getSource().sendFailure(Component.translatable(main.run().status == FinaleRun.Status.COMPLETE
					? "commands.ashfall.finale.already_complete" : "commands.ashfall.finale.already_running"));
			return 0;
		}
		Optional<BlockPos> socket = at.or(() -> HullSite.socket(level.getServer()));
		if (socket.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.finale.no_socket"));
			return 0;
		}
		BlockPos pos = socket.get();
		level.getChunk(pos);
		if (!level.getBlockState(pos).is(FinaleBlocks.UPLINK_SOCKET)) {
			level.setBlock(pos, FinaleBlocks.UPLINK_SOCKET.defaultBlockState(), Block.UPDATE_ALL);
		}
		FinaleLogic.seat(main.run(), main.s(), pos.asLong());
		FinaleController.enterPhase(level, main);
		FinaleController.showSocket(level, pos, main.run());
		main.save().run();
		AshfallArms.LOGGER.info("Finale: started by command at {}", pos.toShortString());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.finale.started", pos.toShortString()), true);
		return 1;
	}

	private static int phase(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		FinaleInstance main = FinaleController.main(level.getServer());
		Phase phase = Phase.byId(StringArgumentType.getString(ctx, "phase"));
		if (phase == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.finale.unknown_phase", StringArgumentType.getString(ctx, "phase")));
			return 0;
		}
		if (!main.run().active()) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.finale.not_running"));
			return 0;
		}
		FinaleController.jumpTo(level, main, phase);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.finale.phase",
				Component.translatable("phase.ashfall.finale." + phase.getSerializedName()), GridState.get(level.getServer()).gridPercent()), true);
		return 1;
	}

	private static int abort(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		FinaleController.abort(level, FinaleController.main(level.getServer()));
		AshfallArms.LOGGER.info("Finale: aborted by command");
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.finale.aborted"), true);
		return 1;
	}

	private static int open(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		FinaleState state = FinaleState.get(level.getServer());
		if (HullSite.placed(level.getServer()).isEmpty()) {
			state.requestOpen(true);
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.finale.open_later"), true);
			return 0;
		}
		state.requestOpen(true);
		boolean open = HullSite.open(level);
		ctx.getSource().sendSuccess(() -> Component.translatable(open ? "commands.ashfall.finale.opened" : "commands.ashfall.finale.open_later"),
				true);
		return open ? 1 : 0;
	}

	/**
	 * Feeds {@code count} Energy Cells into the buffer, or patches {@code count} Grid Couplers into the Uplink, one at a
	 * time through {@link FinaleLogic#feedCell} / {@link FinaleLogic#patch}, stopping when the next one would not fit.
	 * @return how many were used
	 */
	private static int supply(CommandContext<CommandSourceStack> ctx, boolean coupler, int count) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		FinaleInstance main = FinaleController.main(level.getServer());
		FinaleRun run = main.run();
		if (!run.active()) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.finale.not_running"));
			return 0;
		}
		int used = 0;
		boolean resumed = false;
		for (int i = 0; i < count; i++) {
			int hp = run.uplinkHp;
			long buffer = run.bufferTicks;
			FinaleLogic.Result result = coupler ? FinaleLogic.patch(run, main.s()) : FinaleLogic.feedCell(run, main.s());
			resumed |= result == FinaleLogic.Result.RESUMED;
			if (run.uplinkHp == hp && run.bufferTicks == buffer) {
				break;
			}
			used++;
		}
		if (resumed && run.socket != FinaleRun.NONE) {
			FinaleActions.resumed(level, main, BlockPos.of(run.socket));
		}
		main.save().run();
		int n = used;
		ctx.getSource().sendSuccess(() -> coupler
				? Component.translatable("commands.ashfall.finale.patched", n, run.uplinkHp, main.s().uplink().hp())
				: Component.translatable("commands.ashfall.finale.fed", n, run.bufferTicks / 20), true);
		return used;
	}

	private static int unattended(CommandContext<CommandSourceStack> ctx, int players) {
		FinaleController.setUnattended(players);
		AshfallArms.LOGGER.info("Finale: unattended mode {}", players > 0 ? "on, " + players + " stand-in player(s)" : "off");
		ctx.getSource().sendSuccess(() -> players > 0 ? Component.translatable("commands.ashfall.finale.unattended_on", players)
				: Component.translatable("commands.ashfall.finale.unattended_off"), true);
		return players;
	}
}
