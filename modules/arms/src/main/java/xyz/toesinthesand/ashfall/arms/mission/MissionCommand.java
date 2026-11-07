package xyz.toesinthesand.ashfall.arms.mission;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.grid.GridState;

import java.util.List;

/**
 * {@code /ashfall mission} (every player): the mission as a chat checklist with the current step, where it is and how
 * to do it. {@code briefing} opens the full briefing; {@code book} hands out a new copy of the book if the player has
 * none. These work with {@code ashfall:objective} off too: the rule stops the story prompting, not a player asking.
 */
public final class MissionCommand {
	private MissionCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("mission")
						.executes(MissionCommand::checklist)
						.then(Commands.literal("briefing").executes(MissionCommand::briefing))
						.then(Commands.literal("book").executes(MissionCommand::book))));
	}

	private static int checklist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		for (Component line : checklistLines(player)) {
			player.sendSystemMessage(line);
		}
		return 1;
	}

	/** The checklist as chat lines (public for gametests and the client screenshot test). */
	public static List<Component> checklistLines(ServerPlayer player) {
		MissionFacts facts = MissionWorld.collect(player);
		Objective now = MissionResolver.current(facts);
		List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable("mission.ashfall.header", GridState.get(player.level().getServer()).gridPercent())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (MissionResolver.Entry entry : MissionResolver.checklist(facts)) {
			MutableComponent line = Component.literal("  ").append(mark(entry.mark())).append(" ");
			ChatFormatting colour = switch (entry.mark()) {
				case DONE -> ChatFormatting.GRAY;
				case CURRENT -> ChatFormatting.WHITE;
				case TODO -> ChatFormatting.DARK_GRAY;
			};
			line.append(entry.label().text().copy().withStyle(colour));
			if (entry.optional()) {
				line.append(Component.literal(" ")).append(Component.translatable("mission.ashfall.optional").withStyle(ChatFormatting.DARK_GRAY,
						ChatFormatting.ITALIC));
			}
			lines.add(line);
		}
		MutableComponent current = Component.translatable("mission.ashfall.now", now.text().copy().withStyle(ChatFormatting.YELLOW))
				.withStyle(ChatFormatting.GOLD);
		now.target().ifPresent(t -> current.append(" ").append(MissionRadio.heading(player, t).withStyle(ChatFormatting.GRAY)));
		lines.add(current);
		lines.add(now.how().copy().withStyle(ChatFormatting.GRAY));
		lines.add(MissionBriefing.link("briefing.ashfall.link.read", "briefing.ashfall.link.read.hover", "/ashfall mission briefing",
				ChatFormatting.AQUA).append(Component.literal("  ")).append(MissionBriefing.link("mission.ashfall.link.book",
				"mission.ashfall.link.book.hover", "/ashfall mission book", ChatFormatting.GREEN)));
		return lines;
	}

	private static Component mark(MissionResolver.Mark mark) {
		return switch (mark) {
			case DONE -> Component.literal("✔").withStyle(ChatFormatting.GREEN);
			case CURRENT -> Component.literal("▶").withStyle(ChatFormatting.YELLOW);
			case TODO -> Component.literal("○").withStyle(ChatFormatting.DARK_GRAY);
		};
	}

	private static int briefing(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		MissionBriefing.openDialog(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int book(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		for (ItemStack stack : player.getInventory()) {
			if (MissionBriefing.isBriefing(stack)) {
				ctx.getSource().sendSuccess(() -> Component.translatable("mission.ashfall.book.have").withStyle(ChatFormatting.GRAY), false);
				return 0;
			}
		}
		MissionBriefing.give(player, MissionBriefing.book());
		ctx.getSource().sendSuccess(() -> Component.translatable("mission.ashfall.book.given").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}
