package xyz.toesinthesand.ashfall.salvage.city;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code /ashfall city zone|info|levels}: shows which zone the caller stands in and what Lost Cities planned for
 * the chunk, or how many street levels the city around the caller has. The zone layout and the Lost Cities hooks are invisible in play otherwise, and later stages
 * (loot, Director, relays) all key off these two answers, so testers need a quick way to read them.
 */
public final class CityCommands {
	private CityCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("city")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("zone").executes(CityCommands::zone))
						.then(Commands.literal("info").executes(CityCommands::info))
						.then(Commands.literal("levels").executes(ctx -> levels(ctx, 16))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> levels(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static int zone(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		BlockPos pos = BlockPos.containing(source.getPosition());
		Zone zone = ZoneMap.zoneAt(pos.getX(), pos.getZ());
		String msg = describeZone(zone, pos);
		source.sendSuccess(() -> Component.literal(msg), false);
		return zone.districtIndex();
	}

	/**
	 * City levels planned in the square of chunks around the caller, and how often neighbouring city chunks differ
	 * by more than one level (a climb of 12 or more blocks). Reads Lost Cities' plans only, so nothing generates.
	 */
	private static int levels(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		int cx0 = pos.getX() >> 4, cz0 = pos.getZ() >> 4;
		int size = radius * 2 + 1;
		int[][] grid = new int[size][size];
		TreeMap<Integer, int[]> counts = new TreeMap<>();
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				CityChunk c = LostCityContext.chunk(level, cx0 - radius + dx, cz0 - radius + dz).orElse(null);
				grid[dx][dz] = c != null && c.isCity() ? c.cityLevel() : Integer.MIN_VALUE;
				if (c != null && c.isCity()) {
					int[] entry = counts.computeIfAbsent(c.cityLevel(), k -> new int[] {0, c.groundY()});
					entry[0]++;
				}
			}
		}
		int pairs = 0, steep = 0;
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int a = grid[dx][dz];
				if (a == Integer.MIN_VALUE) {
					continue;
				}
				for (int[] n : new int[][] {{dx + 1, dz}, {dx, dz + 1}}) {
					if (n[0] < size && n[1] < size && grid[n[0]][n[1]] != Integer.MIN_VALUE) {
						pairs++;
						if (Math.abs(a - grid[n[0]][n[1]]) > 1) {
							steep++;
						}
					}
				}
			}
		}
		StringBuilder sb = new StringBuilder("City levels within ").append(radius).append(" chunks:");
		counts.forEach((lvl, e) -> sb.append("\n level ").append(lvl).append(" (y ").append(e[1]).append("): ").append(e[0]).append(" chunks"));
		sb.append("\n").append(steep).append(" of ").append(pairs).append(" neighbouring city chunks differ by 2+ levels");
		String msg = sb.toString();
		source.sendSuccess(() -> Component.literal(msg), false);
		return steep;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
		Zone zone = ZoneMap.zoneAtChunk(cx, cz);
		StringBuilder sb = new StringBuilder(describeZone(zone, pos)).append('\n');
		if (!LostCityContext.available()) {
			sb.append("The Lost Cities is not installed");
		} else {
			String profile = LostCityContext.profile(level).orElse("none");
			Optional<CityChunk> chunk = LostCityContext.chunk(level, cx, cz);
			sb.append("Lost Cities profile: ").append(profile).append('\n');
			if (chunk.isEmpty()) {
				sb.append("Lost Cities is inactive in this dimension");
			} else {
				CityChunk c = chunk.get();
				sb.append("chunk ").append(cx).append(", ").append(cz)
						.append(": isCity=").append(c.isCity())
						.append(" building=").append(c.buildingId() == null ? (c.isCity() ? "(street)" : "-") : c.buildingId());
				if (c.multiBuilding() != null) {
					sb.append(" multi=").append(c.multiBuilding());
				}
				sb.append(" floors=").append(c.floors()).append(" cellars=").append(c.cellars())
						.append(" cityLevel=").append(c.cityLevel()).append(" groundY=").append(c.groundY())
						.append(" style=").append(c.cityStyle() == null ? "-" : c.cityStyle())
						.append(c.ruinLevel() >= 0 ? " ruinedAbove=" + c.ruinLevel() : "");
				// The building instance and profile the loot, refill and Director passes use for this chunk.
				Building building = AshfallSalvage.buildings().inChunk(cx, cz).stream().findFirst().orElse(null);
				if (building != null) {
					Identifier profileId = BuildingProfiles.classifyByTags(building);
					sb.append("\ninstance ").append(building.tags().getOrDefault(LostCitiesBuildingSource.TAG_KEY, Long.toString(building.id())))
							.append(" profile ").append(profileId != null ? profileId : BuildingProfiles.GENERIC);
				}
			}
		}
		String msg = sb.toString();
		source.sendSuccess(() -> Component.literal(msg), false);
		return LostCityContext.chunk(level, cx, cz).map(c -> c.isCity() ? 1 : 0).orElse(0);
	}

	private static String describeZone(Zone zone, BlockPos pos) {
		return "Zone at " + pos.getX() + ", " + pos.getZ() + ": " + zone.id() + " (" + zone.kind()
				+ (zone.isDistrict() ? ", district " + zone.districtIndex() : "") + ", tier " + zone.tier()
				+ (zone.city() ? ", style " + zone.cityStyle().orElse("-") : ", wilderness")
				+ ", ruins " + zone.ruinChance() + ", damage x" + zone.damageScale()
				+ ", population x" + zone.populationScale()
				+ (zone.director().darknessScale() != 1f ? ", darkness x" + zone.director().darknessScale() : "")
				+ (zone.director().gentleDays() > 0 ? ", x" + zone.director().gentleScale() + " for the first "
						+ zone.director().gentleDays() + " days" : "") + ")"
				+ (ZoneMap.current().isEmpty() ? " [no zone data loaded]" : "");
	}
}
