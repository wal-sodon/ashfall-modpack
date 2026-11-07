package xyz.toesinthesand.ashfall.director.spawn;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.city.Zone;

/**
 * {@code /ashfall director status|clear-building <building id>|here|repopulate <building id>|here} (permission
 * level 2). The building id is {@code Building#id()} (the packed origin chunk of a Lost Cities building instance,
 * shown by {@code /ashfall city info}); {@code here} acts on the building the command source stands in, which is
 * what an admin standing in a hospital actually wants.
 * Other features register their own {@code /ashfall ...} branches; Brigadier merges the literals.
 * Building commands always act on the overworld, where buildings and the Director live.
 */
public final class DirectorCommand {
	private static final SimpleCommandExceptionType NOT_IN_BUILDING =
			new SimpleCommandExceptionType(Component.translatable("commands.ashfall.director.not_in_building"));

	private DirectorCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// The permission sits on "director", not on the shared "ashfall" root: Brigadier keeps the
		// requirement of whichever module registered the root first, so an op-only root would hide
		// other modules' player-facing /ashfall branches whenever the Director happened to load first.
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("director")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("status").executes(DirectorCommand::status))
						.then(Commands.literal("clear-building")
								.then(Commands.literal("here").executes(c -> clearBuilding(c, here(c))))
								.then(Commands.argument("building_id", LongArgumentType.longArg())
										.executes(c -> clearBuilding(c, LongArgumentType.getLong(c, "building_id")))))
						.then(Commands.literal("repopulate")
								.then(Commands.literal("here").executes(c -> repopulate(c, here(c))))
								.then(Commands.argument("building_id", LongArgumentType.longArg())
										.executes(c -> repopulate(c, LongArgumentType.getLong(c, "building_id")))))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getServer().overworld();
		int day = DirectorDay.today(level);
		int ambient = DirectorTracker.count(DirectorZombie.Source.AMBIENT);
		int building = DirectorTracker.count(DirectorZombie.Source.BUILDING);
		int populated = 0, cleared = 0, ready = 0;
		for (BuildingRecord record : DirectorState.get(source.getServer()).buildings()) {
			switch (record.status()) {
				case POPULATED -> populated++;
				case CLEARED -> cleared++;
				case READY -> ready++;
			}
		}
		int next = SpawnDirector.ticksToNextCycle(level);
		boolean active = SpawnDirector.active(level);
		int fPopulated = populated, fCleared = cleared, fReady = ready;
		source.sendSuccess(() -> Component.translatable("commands.ashfall.director.status.header",
				day, active ? Component.translatable("commands.ashfall.director.status.on") : Component.translatable("commands.ashfall.director.status.off"),
				next < 0 ? "-" : String.valueOf(next)), false);
		source.sendSuccess(() -> Component.translatable("commands.ashfall.director.status.counts",
				DirectorTracker.total(), SpawnDirector.worldCap(), ambient, building), false);
		source.sendSuccess(() -> Component.translatable("commands.ashfall.director.status.buildings",
				fPopulated, fCleared, fReady), false);
		// The story's spawn multiplier (the Sever ending's x0.4) and the ambient cap it gives, so an ending's effect
		// on spawning can be read from the console.
		double multiplier = xyz.toesinthesand.ashfall.director.story.StoryHooks.spawnMultiplier(source.getServer());
		int storyCap = SpawnDirector.ambientCap(level);
		source.sendSuccess(() -> Component.translatable("commands.ashfall.director.status.story",
				String.format(java.util.Locale.ROOT, "%.2f", multiplier), storyCap), false);
		// The spawn weights where the command runs (console: the spawn point; use "execute positioned"), so other
		// modules' darkness modifiers (a reclaimed district's -80%) can be checked without a player there.
		BlockPos here = BlockPos.containing(source.getPosition());
		Zone zone = DirectorContexts.zoneAt(here);
		float zoneDark = zone.director().darknessScale();
		float modifier = SpawnDirector.darknessModifier(level, here);
		source.sendSuccess(() -> Component.translatable("commands.ashfall.director.status.here", here.getX(), here.getZ(),
				zone.id(), String.format(java.util.Locale.ROOT, "%.2f", zoneDark),
				String.format(java.util.Locale.ROOT, "%.2f", modifier),
				String.format(java.util.Locale.ROOT, "%.2f", zoneDark * modifier)), false);
		int cap = SpawnDirector.ambientCap(level);
		for (ServerPlayer player : level.players()) {
			int[] near96 = DirectorTracker.countNear(player.getX(), player.getY(), player.getZ(), SpawnRules.AMBIENT_RADIUS);
			int[] near128 = DirectorTracker.countNear(player.getX(), player.getY(), player.getZ(), SpawnRules.PLAYER_CAP_RADIUS);
			int room = SpawnDirector.allowance(level, player.getX(), player.getY(), player.getZ());
			source.sendSuccess(() -> Component.translatable("commands.ashfall.director.status.player",
					player.getDisplayName(), near96[0], cap, near128[1], SpawnRules.PLAYER_HARD_CAP, room), false);
		}
		return DirectorTracker.total();
	}

	/** Id of the building at the command source's position; fails the command when it stands in none. */
	private static long here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Building building = AshfallSalvage.buildings().at(BlockPos.containing(ctx.getSource().getPosition()));
		if (building == null) {
			throw NOT_IN_BUILDING.create();
		}
		return building.id();
	}

	private static int clearBuilding(CommandContext<CommandSourceStack> ctx, long id) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		int removed = BuildingPopulations.clear(level, id);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.director.clear_building", id, removed), true);
		return removed;
	}

	private static int repopulate(CommandContext<CommandSourceStack> ctx, long id) {
		ServerLevel level = ctx.getSource().getServer().overworld();
		boolean known = BuildingPopulations.building(id) != null;
		int spawned = BuildingPopulations.repopulate(level, id);
		ctx.getSource().sendSuccess(() -> known
				? Component.translatable("commands.ashfall.director.repopulate", id, spawned)
				: Component.translatable("commands.ashfall.director.repopulate.unknown", id), true);
		return spawned;
	}
}
