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
import xyz.toesinthesand.ashfall.arms.story.StoryItems;

import java.util.List;

/**
 * {@code /ashfall mission} (every player): the mission as a chat checklist with the current step, where it is and how
 * to do it. {@code briefing} opens the full briefing; {@code book} hands out a new copy of the book if the player has
 * none. These work with {@code ashfall:objective} off too: the rule stops the story prompting, not a player asking.
 * <p>
 * {@code /ashfall radio} (every player) hands out a Field Radio to a player who carries none, at most once every
 * three in-game days ({@link #RADIO_COOLDOWN_TICKS} of game time, which sleeping doesn't skip). The radio is a starter kit
 * item from the safehouse cache with no recipe, so without this a player who lost it had no way back to relay tuning.
 */
public final class MissionCommand {
	/** Three in-game days between radios from {@code /ashfall radio}. */
	public static final long RADIO_COOLDOWN_TICKS = 3L * 24000L;

	/** What {@link #giveRadio} did: gave one, refused because one is carried, or refused for the cooldown. */
	public enum RadioResult { GIVEN, HAVE_ONE, COOLDOWN }
	private MissionCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("mission")
						.executes(MissionCommand::checklist)
						.then(Commands.literal("briefing").executes(MissionCommand::briefing))
						.then(Commands.literal("book").executes(MissionCommand::book))));
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("radio").executes(MissionCommand::radio)));
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

	private static int radio(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		RadioResult result = giveRadio(player);
		Component message = switch (result) {
			case GIVEN -> Component.translatable("mission.ashfall.radio.given").withStyle(ChatFormatting.GREEN);
			case HAVE_ONE -> Component.translatable("mission.ashfall.radio.have").withStyle(ChatFormatting.GRAY);
			case COOLDOWN -> Component.translatable("mission.ashfall.radio.cooldown",
					(radioCooldownLeft(player) + 1199) / 1200).withStyle(ChatFormatting.GRAY);
		};
		ctx.getSource().sendSuccess(() -> message, false);
		return result == RadioResult.GIVEN ? 1 : 0;
	}

	/** Gives {@code player} a Field Radio unless they carry one or had one from this command less than three days ago. */
	public static RadioResult giveRadio(ServerPlayer player) {
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(StoryItems.FIELD_RADIO)) {
				return RadioResult.HAVE_ONE;
			}
		}
		if (radioCooldownLeft(player) > 0) {
			return RadioResult.COOLDOWN;
		}
		MissionProgress.set(player, MissionProgress.get(player).withRadioAt(player.level().getGameTime()));
		MissionBriefing.give(player, new ItemStack(StoryItems.FIELD_RADIO));
		return RadioResult.GIVEN;
	}

	/** Game ticks until {@code player} may get another radio from {@code /ashfall radio}; 0 when they may now. */
	public static long radioCooldownLeft(ServerPlayer player) {
		long at = MissionProgress.get(player).radioAt();
		if (at == MissionProgress.NEVER) {
			return 0L;
		}
		long since = player.level().getGameTime() - at;
		return since < 0 ? 0L : Math.max(0L, RADIO_COOLDOWN_TICKS - since);
	}
}
