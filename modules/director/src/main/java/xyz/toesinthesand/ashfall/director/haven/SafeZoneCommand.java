package xyz.toesinthesand.ashfall.director.haven;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xyz.toesinthesand.ashfall.director.claims.Claims;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /ashfall safezone list|add <name> [radius]|remove <id>} (permission level 2).
 * <ul>
 *   <li>{@code add} makes a safe zone centred on the chunk you stand in, with your block as its respawn point.
 *   The radius is in chunks (default 1, a 3x3-chunk zone).</li>
 *   <li>{@code remove} deletes a zone added in game; for a datapack zone it only disables it on this world (the
 *   datapack file is left alone, and the zone comes back if the world's saved data is reset).</li>
 * </ul>
 * Both re-sync the OPAC server claims and the haven regions immediately.
 */
public final class SafeZoneCommand {
	private SafeZoneCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("safezone")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("list").executes(SafeZoneCommand::list))
						.then(Commands.literal("add")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(c -> add(c, 1))
										.then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
												.executes(c -> add(c, IntegerArgumentType.getInteger(c, "radius"))))))
						.then(Commands.literal("remove")
								.then(Commands.argument("id", StringArgumentType.greedyString())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(SafeZones.active(c.getSource().getServer()).keySet(), b))
										.executes(SafeZoneCommand::remove)))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Map<String, SafeZone> zones = SafeZones.active(server);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.safezone.list.header", zones.size(),
				Claims.claimsModPresent() ? "OPAC" : "-"), false);
		zones.forEach((id, zone) -> {
			ChunkArea a = zone.area();
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.ashfall.safezone.list.entry", id, zone.name(),
					zone.dimension().identifier().toString(), a.chunkX(), a.chunkZ(), a.radius(),
					HavenState.get(server).serverClaims(id).size()), false);
		});
		return zones.size();
	}

	private static int add(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		SafeZone zone = new SafeZone(name, level.dimension(), ChunkArea.around(pos.getX(), pos.getZ(), radius),
				Optional.of(pos), false, Optional.empty());
		HavenState.get(source.getServer()).putCustomZone(name, zone);
		Havens.refresh(source.getServer());
		int side = radius * 2 + 1;
		source.sendSuccess(() -> Component.translatable("commands.ashfall.safezone.added", name, side, side,
				HavenState.get(source.getServer()).serverClaims(name).size()), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		String id = StringArgumentType.getString(ctx, "id").trim();
		HavenState state = HavenState.get(server);
		SafeZone zone = SafeZones.active(server).get(id);
		if (zone == null) {
			source.sendFailure(Component.translatable("commands.ashfall.safezone.unknown", id));
			return 0;
		}
		boolean custom = state.removeCustomZone(id);
		if (!custom) {
			state.disableZone(id);
		}
		Havens.releaseServerClaims(server, id, zone);
		Havens.rebuild(server);
		source.sendSuccess(() -> Component.translatable(custom ? "commands.ashfall.safezone.removed"
				: "commands.ashfall.safezone.disabled", id), true);
		return 1;
	}
}
