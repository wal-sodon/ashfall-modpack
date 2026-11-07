package xyz.toesinthesand.ashfall.director.horde;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Collectors;

/**
 * {@code /ashfall horde status | wave [at <pos>] | breach [substation]} (permission level 2). {@code status} prints the schedule as
 * the server sees it (today, the next horde night, the tier, the unlocked classes, the next Breach, live hordes);
 * {@code wave} sends tonight's first wave at the calling player now ({@code wave at <pos>} aims it at a place, so the
 * console of a server with nobody online can send one too); {@code breach} starts a Breach at once. The last
 * two exist so admins and headless playtests can check a horde without waiting for the right night.
 */
public final class HordeCommand {
	private HordeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("horde")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("status").executes(HordeCommand::status))
						.then(Commands.literal("wave").executes(HordeCommand::wave)
								.then(Commands.literal("at")
										.then(Commands.argument("pos", BlockPosArgument.blockPos())
												.executes(HordeCommand::waveAt))))
						.then(Commands.literal("breach")
								.executes(ctx -> breach(ctx, null))
								.then(Commands.argument("substation", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(
												HordeHooks.breach().targets(c.getSource().getServer()), b))
										.executes(ctx -> breach(ctx, StringArgumentType.getString(ctx, "substation")))))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		HordeSchedule schedule = HordeSchedule.get(level.getServer());
		long clock = level.getOverworldClockTime();
		int day = HordeSchedule.day(clock);
		int next = schedule.nextHordeDay(day, level.getSeed());
		int tier = HordeHooks.tier(schedule, day, level.getServer());
		String unlocked = schedule.unlockedOn(day).stream().map(id -> id.getPath()).collect(Collectors.joining(", "));
		int breach = schedule.nextBreachDay(day);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.horde.status", day,
				next == Integer.MAX_VALUE ? "-" : String.valueOf(next), tier, HordeHooks.gridTier(),
				unlocked.isEmpty() ? "-" : unlocked, breach == Integer.MAX_VALUE ? "-" : String.valueOf(breach),
				Hordes.all().size(), HordeNights.hordeNight(level.getServer())), false);
		return next == Integer.MAX_VALUE ? 0 : next - day;
	}

	private static int wave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getServer().overworld();
		HordeSchedule schedule = HordeSchedule.get(level.getServer());
		int day = HordeSchedule.day(level.getOverworldClockTime());
		HordeSchedule.Tier row = schedule.tierRow(HordeHooks.tier(schedule, day, level.getServer()));
		Horde horde = HordeNights.spawnWave(level, player, schedule, row.waves().getFirst(), day);
		if (horde == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.horde.wave_failed"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.horde.wave", horde.size()), true);
		return horde.size();
	}

	private static int waveAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerLevel level = ctx.getSource().getServer().overworld();
		Vec3 target = Vec3.atBottomCenterOf(BlockPosArgument.getLoadedBlockPos(ctx, level, "pos"));
		HordeSchedule schedule = HordeSchedule.get(level.getServer());
		int day = HordeSchedule.day(level.getOverworldClockTime());
		HordeSchedule.Tier row = schedule.tierRow(HordeHooks.tier(schedule, day, level.getServer()));
		Horde horde = HordeNights.spawnWaveAt(level, target, schedule, row.waves().getFirst(), day);
		if (horde == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.horde.wave_failed"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.horde.wave_at", horde.size(),
				(int) target.x, (int) target.y, (int) target.z), true);
		return horde.size();
	}

	private static int breach(CommandContext<CommandSourceStack> ctx, String target) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		String hit = HordeNights.startBreach(level, target);
		if (hit == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.horde.no_breach"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.horde.breach", HordeHooks.breach().name(hit)), true);
		return 1;
	}
}
