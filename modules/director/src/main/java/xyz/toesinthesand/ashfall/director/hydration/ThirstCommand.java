package xyz.toesinthesand.ashfall.director.hydration;

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
 * {@code /thirst get|set <player> [value]} (permission level 2): a testing aid for balance and for the
 * HUD, so nobody has to sprint around a desert to see the empty-bar effects.
 */
public final class ThirstCommand {
	private ThirstCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("thirst")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("get")
						.then(Commands.argument("player", EntityArgument.player()).executes(ThirstCommand::get)))
				.then(Commands.literal("set")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("value", IntegerArgumentType.integer(0, ThirstData.MAX_THIRST))
										.executes(ThirstCommand::set)))));
	}

	private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ThirstData data = Hydration.get(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.thirst.get",
				player.getDisplayName(), data.thirst(), String.format("%.1f", data.saturation())), false);
		return data.thirst();
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int value = IntegerArgumentType.getInteger(ctx, "value");
		Hydration.set(player, Hydration.get(player).withThirst(value));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.thirst.set",
				player.getDisplayName(), value), true);
		return value;
	}
}
