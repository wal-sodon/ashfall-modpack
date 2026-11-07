package xyz.toesinthesand.ashfall.arms.supply;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /ashfall supplydrop start|here|status|clear}: run supply drops by hand for testing. */
public final class SupplyDropCommand {
	private SupplyDropCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall").then(Commands.literal("supplydrop")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("start").executes(ctx -> {
					SupplyDrops.Point point = SupplyDrops.POINTS.get(ctx.getSource().getLevel().getRandom().nextInt(SupplyDrops.POINTS.size()));
					SupplyDropState.Active drop = SupplyDrops.start(ctx.getSource().getServer().overworld(), point.x(), point.z(), true);
					ctx.getSource().sendSuccess(() -> Component.literal("Supply drop aimed at " + drop.x() + ", " + drop.z()), true);
					return 1;
				}))
				.then(Commands.literal("here").executes(ctx -> {
					BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
					SupplyDrops.start(ctx.getSource().getServer().overworld(), pos.getX() + 20, pos.getZ(), false);
					ctx.getSource().sendSuccess(() -> Component.literal("Supply drop aimed 20 blocks east"), true);
					return 1;
				}))
				.then(Commands.literal("status").executes(ctx -> {
					SupplyDropState state = SupplyDropState.get(ctx.getSource().getServer());
					String msg = state.active().map(d -> "Active at " + d.x() + ", " + d.z() + ", landed=" + d.crate().isPresent()
							+ ", ticks left " + (d.deadline() - ctx.getSource().getServer().overworld().getGameTime()))
							.orElse("No drop under way; next on day " + state.nextDay());
					ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
					return 1;
				}))
				.then(Commands.literal("clear").executes(ctx -> {
					SupplyDrops.finish(ctx.getSource().getServer().overworld(), SupplyDropState.get(ctx.getSource().getServer()),
							Component.literal("Supply drop cleared"));
					return 1;
				}))));
	}
}
