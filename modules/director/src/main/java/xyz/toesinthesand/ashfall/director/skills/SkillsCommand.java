package xyz.toesinthesand.ashfall.director.skills;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * {@code /ashfall skills [player]} lists class and ranks for anyone. For testing and fixing a player by hand
 * (permission level 2): {@code set <player> <skill> <rank>}, {@code class <player> <class>} (sets the class with no
 * home or cooldown check and clears ranks) and {@code reset <player>} (no class, no ranks, the picker opens again).
 * Neither refunds XP.
 */
public final class SkillsCommand {
	private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
			Component.translatable("commands.ashfall.skills.unknown"));

	private SkillsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall").then(Commands.literal("skills")
				.executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player"))))
				.then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("skill", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Skill.values()).map(Skill::id), b))
										.then(Commands.argument("rank", IntegerArgumentType.integer(0, 10))
												.executes(SkillsCommand::set)))))
				.then(Commands.literal("class").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("class", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(SurvivorClass.values()).map(SurvivorClass::id), b))
										.executes(SkillsCommand::setClass))))
				.then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(SkillsCommand::reset)))));
	}

	private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		SkillRules rules = SkillRules.get();
		SurvivorClass chosen = Skills.survivorClass(player);
		Component name = chosen == null ? Component.translatable("class.ashfall.none") : Skills.className(chosen);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.skills.header",
				player.getDisplayName(), name, Skills.points(player)), false);
		for (Skill skill : Skill.values()) {
			int rank = Skills.rank(player, skill);
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.skills.line",
					Component.translatable("skill.ashfall." + skill.id()), rank, rules.maxRank(chosen, skill)), false);
		}
		return Skills.get(player).totalRanks();
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Skill skill = Skill.byId(StringArgumentType.getString(ctx, "skill"));
		if (skill == null) {
			throw UNKNOWN.create();
		}
		int rank = Math.min(IntegerArgumentType.getInteger(ctx, "rank"), SkillRules.get().maxRank(Skills.survivorClass(player), skill));
		Skills.set(player, Skills.get(player).with(skill, rank));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.skills.set",
				player.getDisplayName(), Component.translatable("skill.ashfall." + skill.id()), rank), true);
		return rank;
	}

	private static int setClass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		SurvivorClass chosen = SurvivorClass.byId(StringArgumentType.getString(ctx, "class"));
		if (chosen == null) {
			throw UNKNOWN.create();
		}
		Skills.set(player, Skills.get(player).asClass(chosen, -1L));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.skills.class",
				player.getDisplayName(), Skills.className(chosen)), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Skills.set(player, SkillData.EMPTY);
		if (ServerPlayNetworking.canSend(player, SkillPayloads.PickClass.TYPE)) {
			ServerPlayNetworking.send(player, SkillPayloads.PickClass.INSTANCE);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.skills.reset", player.getDisplayName()), true);
		return 1;
	}
}
