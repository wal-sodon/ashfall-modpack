package xyz.toesinthesand.ashfall.director.infection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ashfall infection get|set|clear <player> [stage]} (permission level 2): a testing aid, so balance
 * passes and HUD checks don't need a zombie bite and an hour of waiting.
 */
public final class InfectionCommand {
	private InfectionCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Permission on "infection", not the shared root: see DirectorCommand for why.
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("infection")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("get")
								.then(Commands.argument("player", EntityArgument.player()).executes(InfectionCommand::get)))
						.then(Commands.literal("set")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("stage", IntegerArgumentType.integer(1, InfectionRules.MAX_STAGE))
												.executes(InfectionCommand::set))))
						.then(Commands.literal("clear")
								.then(Commands.argument("player", EntityArgument.player()).executes(InfectionCommand::clear)))));
	}

	/** Returns the stage (0 = healthy) so command blocks and tests can read it. */
	private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		InfectionData data = Infection.get(player);
		int stage = Infection.rules().stageAt(data);
		if (stage == 0) {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.infection.healthy", player.getDisplayName()), false);
		} else {
			long left = Math.max(0, Infection.rules().deathAt() - data.progress()) / 20;
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.infection.get", player.getDisplayName(),
					stage, data.progress() / 20, data.pauseTicks() / 20, left), false);
		}
		return stage;
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int stage = IntegerArgumentType.getInteger(ctx, "stage");
		Infection.setStage(player, stage);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.infection.set", player.getDisplayName(), stage), true);
		return stage;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Infection.clear(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.infection.clear", player.getDisplayName()), true);
		return 1;
	}
}
