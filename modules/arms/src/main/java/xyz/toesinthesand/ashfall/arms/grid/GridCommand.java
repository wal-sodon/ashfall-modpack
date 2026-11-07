package xyz.toesinthesand.ashfall.arms.grid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.director.spawn.DirectorDay;

import java.util.Arrays;

/**
 * {@code /ashfall grid status | set <district> <state> | damage <district> <hp> | reset} (permission level 2). {@code set} jumps a
 * district straight to a state with that state's bookkeeping filled in (a set REPAIRED has its couplers, a set
 * ENERGIZED has a siege scheduled), so every step can be tested without walking the loop; the change event fires
 * as if it happened in play. {@code reset} sets every district back to DARK; core positions are kept.
 */
public final class GridCommand {
	private GridCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Permission on "grid", not the shared root, like the other /ashfall subcommands.
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("grid")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("status").executes(GridCommand::status))
						.then(Commands.literal("set")
								.then(Commands.argument("district", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(GridDistricts.ids(), b))
										.then(Commands.argument("state", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(
														Arrays.stream(DistrictState.values()).map(DistrictState::id), b))
												.executes(GridCommand::set))))
						.then(Commands.literal("damage")
								.then(Commands.argument("district", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(GridDistricts.ids(), b))
										.then(Commands.argument("amount", IntegerArgumentType.integer(1))
												.executes(GridCommand::damage))))
						.then(Commands.literal("relays")
								.then(Commands.argument("district", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(GridDistricts.ids(), b))
										.then(Commands.argument("condition", IntegerArgumentType.integer(0, DistrictRecord.FULL_CONDITION))
												.executes(GridCommand::relays))))
						.then(Commands.literal("reset").executes(GridCommand::reset))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		GridState grid = GridState.get(level.getServer());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.grid.status", grid.gridPercent(),
				grid.reclaimedCount(), grid.districts().size()), false);
		for (String id : grid.districts()) {
			ctx.getSource().sendSuccess(() -> GridActions.statusLine(level, id).copy().append(coreLine(grid, id)), false);
		}
		return grid.gridPercent();
	}

	/**
	 * Where the district's core is, so an admin (or a headless test) can find and force-load the substation. A core
	 * is only known once its chunk has loaded with arms present ({@link CoreConversion}), so before that it says so.
	 */
	private static Component coreLine(GridState grid, String id) {
		DistrictRecord r = grid.record(id);
		if (r == null || !r.hasCore()) {
			return Component.translatable("status.ashfall.grid.no_core");
		}
		BlockPos core = BlockPos.of(r.corePos);
		return Component.translatable("status.ashfall.grid.core", core.getX(), core.getY(), core.getZ());
	}

	private static int set(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "district");
		DistrictState to = DistrictState.byId(StringArgumentType.getString(ctx, "state"));
		if (!GridDistricts.isDistrict(id)) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.grid.unknown_district", id));
			return 0;
		}
		if (to == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.grid.unknown_state",
					StringArgumentType.getString(ctx, "state")));
			return 0;
		}
		ServerLevel level = ctx.getSource().getServer().overworld();
		force(level, id, to);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.grid.set", GridDistricts.name(id),
				GridActions.stateName(to)), true);
		return 1;
	}

	/**
	 * Damages a besieged core as siege zombies would, through the same loss handling. For admins and headless tests:
	 * whether zombies reach a core depends on the building around it, and the loss path must be testable anyway.
	 */
	private static int damage(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "district");
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		ServerLevel level = ctx.getSource().getServer().overworld();
		DistrictRecord r = GridState.get(level.getServer()).record(id);
		if (!GridDistricts.isDistrict(id) || r == null || r.state != DistrictState.SIEGE) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.grid.not_besieged", GridDistricts.name(id)));
			return 0;
		}
		boolean destroyed = GridSiege.damageCore(level, id, amount);
		int hp = GridState.get(level.getServer()).record(id).coreHp;
		ctx.getSource().sendSuccess(() -> destroyed
				? Component.translatable("commands.ashfall.grid.damage_destroyed", GridDistricts.name(id))
				: Component.translatable("commands.ashfall.grid.damage", GridDistricts.name(id), hp), true);
		return destroyed ? 0 : hp;
	}

	/** Sets every linked relay of a district to {@code condition} (decay testing), then re-checks the district. */
	private static int relays(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "district");
		int condition = IntegerArgumentType.getInteger(ctx, "condition");
		if (!GridDistricts.isDistrict(id)) {
			ctx.getSource().sendFailure(Component.translatable("commands.ashfall.grid.unknown_district", id));
			return 0;
		}
		ServerLevel level = ctx.getSource().getServer().overworld();
		GridState grid = GridState.get(level.getServer());
		grid.update(level, id, r -> r.linked.forEach(chunk -> r.setCondition(chunk, condition)));
		GridDecay.refresh(level, id);
		int linked = grid.record(id).linked.size();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.grid.relays", GridDistricts.name(id), linked, condition), true);
		return linked;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		GridState grid = GridState.get(level.getServer());
		int n = 0;
		for (String id : grid.districts()) {
			force(level, id, DistrictState.DARK);
			n++;
		}
		int count = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.grid.reset", count), true);
		return n;
	}

	/** Puts {@code id} into {@code to} with that state's bookkeeping filled in. Public for gametests. */
	public static void force(ServerLevel level, String id, DistrictState to) {
		LoopRules rules = GridDistricts.settings(id).rules();
		long clock = level.getOverworldClockTime();
		int day = DirectorDay.today(level);
		GridState.get(level.getServer()).update(level, id, r -> {
			if (to == DistrictState.DARK) {
				r.reset();
				return;
			}
			r.couplers = rules.couplers();
			r.breach = false;
			switch (to) {
				case REPAIRED -> {
					r.state = DistrictState.REPAIRED;
					r.cells = 0;
					r.holdTicks = 0;
					r.siegeAt = DistrictRecord.UNSET;
				}
				case ENERGIZED -> {
					r.cells = rules.cells();
					r.holdTicks = rules.holdTicks();
					r.linked.addAll(GridDistricts.keyRelayChunks(id));
					r.state = DistrictState.REPAIRED;
					DistrictLoop.energize(r, clock);
				}
				case SIEGE -> {
					r.cells = rules.cells();
					r.holdTicks = rules.holdTicks();
					r.linked.addAll(GridDistricts.keyRelayChunks(id));
					r.siegeAt = clock;
					DistrictLoop.startSiege(r, rules, GridSiege.coreHp(level, id));
				}
				case RECLAIMED -> {
					// a forced reclaim gets its relays too, or decay would black it out at once
					r.linked.addAll(GridDistricts.keyRelayChunks(id));
					r.relayCondition.clear();
					r.flickerSinceDay = DistrictRecord.UNSET;
					DistrictLoop.reclaim(r, day);
				}
				case BROKEN -> {
					// as a lost Breach leaves it: no couplers in the broken core, links and reward kept
					if (r.reclaimedAtDay == DistrictRecord.UNSET) {
						r.reclaimedAtDay = day;
					}
					r.couplers = 0;
					r.cells = 0;
					r.holdTicks = 0;
					r.siegeAt = DistrictRecord.UNSET;
					r.state = DistrictState.BROKEN;
				}
				case BLACKOUT -> {
					// as worn-out relays leave it: reclaimed once, every linked relay offline
					if (r.reclaimedAtDay == DistrictRecord.UNSET) {
						r.reclaimedAtDay = day;
					}
					r.siegeAt = DistrictRecord.UNSET;
					r.flickerSinceDay = DistrictRecord.UNSET;
					r.linked.forEach(chunk -> r.setCondition(chunk, 0));
					r.state = DistrictState.BLACKOUT;
				}
				default -> {
				}
			}
		});
	}
}
