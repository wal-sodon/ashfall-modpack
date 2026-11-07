package xyz.toesinthesand.ashfall.salvage.site;

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
import xyz.toesinthesand.ashfall.salvage.city.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /ashfall site list [radius]|place <key>} (permission level 2).
 * <p>
 * Sites are placed lazily near players ({@link Sites}), so on a server nobody has visited yet, or after a site
 * failed to place, there is otherwise no way to see where the ridge's crash sites will be or to put one down.
 * {@code list} shows the placed sites, the fixed sites and the crash sites planned around the caller (each
 * with its key and whether it is placed); {@code place} places one of those now, loading its chunks, and is
 * a no-op for a key that is already placed so it can never stamp a site twice. A crash key is looked up at the
 * chunk it names, so it can be placed from the console wherever the site is.
 */
public final class SiteCommand {
	/** Default and maximum search radius for planned crash sites, in blocks. */
	private static final int DEFAULT_RADIUS = 256;
	private static final int MAX_RADIUS = 2048;

	private SiteCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ashfall")
				.then(Commands.literal("site")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("list")
								.executes(c -> list(c, DEFAULT_RADIUS))
								.then(Commands.argument("radius", IntegerArgumentType.integer(16, MAX_RADIUS))
										.executes(c -> list(c, IntegerArgumentType.getInteger(c, "radius")))))
						.then(Commands.literal("place")
								.then(Commands.argument("key", StringArgumentType.greedyString())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(
												candidates(c.getSource(), DEFAULT_RADIUS).stream().map(Candidate::key), b))
										.executes(SiteCommand::place)))));
	}

	/** A site the caller could place: a fixed site or a planned crash site. */
	private record Candidate(String key, String template, int x, int z, Optional<Zone.FixedSite> fixed,
			Optional<CrashSitePlanner.Planned> planned) {
	}

	private static List<Candidate> candidates(CommandSourceStack source, int radius) {
		BlockPos pos = BlockPos.containing(source.getPosition());
		return candidates(source.getLevel(), pos.getX(), pos.getZ(), radius);
	}

	/** The fixed sites (always) and the crash sites planned within {@code radius} blocks of (x, z). */
	private static List<Candidate> candidates(ServerLevel level, int x, int z, int radius) {
		List<Candidate> out = new ArrayList<>();
		for (Zone.FixedSite site : Sites.fixedSites().values()) {
			out.add(new Candidate(PlacedSite.fixedKey(site.id()), site.template(), site.x(), site.z(), Optional.of(site), Optional.empty()));
		}
		for (CrashSitePlanner.Planned p : Sites.plannedNear(level, x, z, radius)) {
			out.add(new Candidate(p.key(), p.template(), p.centerX(), p.centerZ(), Optional.empty(), Optional.of(p)));
		}
		return out;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, int radius) {
		CommandSourceStack source = ctx.getSource();
		SiteState state = SiteState.get(source.getServer());
		boolean enabled = Sites.enabled(source.getLevel());
		List<Candidate> candidates = candidates(source, radius);
		StringBuilder sb = new StringBuilder("Sites: ").append(state.all().size()).append(" placed in this world")
				.append(enabled ? "" : " (automatic placement is off in this level)")
				.append("; ").append(candidates.size()).append(" fixed or planned within ").append(radius).append(" blocks");
		for (Candidate c : candidates) {
			PlacedSite placed = state.site(c.key());
			sb.append("\n  ").append(c.key()).append(" ").append(c.template()).append(" at ").append(c.x()).append(", ").append(c.z())
					.append(placed == null ? " - not placed" : " - placed at " + placed.origin().toShortString()
							+ ", guards left " + placed.guards());
		}
		String msg = sb.toString();
		source.sendSuccess(() -> Component.literal(msg), false);
		return candidates.size();
	}

	private static int place(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String key = StringArgumentType.getString(ctx, "key").trim();
		ServerLevel level = source.getLevel();
		if (SiteState.get(source.getServer()).placed(key)) {
			source.sendFailure(Component.literal("Site " + key + " is already placed"));
			return 0;
		}
		// A crash key names its chunk ("crash:<rule>:<cx>,<cz>"), so look there first: the console stands at the world
		// spawn, which can be further from a ridge site than any sensible search radius.
		Optional<Candidate> match = keyChunk(key)
				.map(chunk -> candidates(level, (chunk[0] << 4) + 8, (chunk[1] << 4) + 8, 16))
				.orElseGet(() -> candidates(source, MAX_RADIUS))
				.stream().filter(c -> c.key().equals(key)).findFirst();
		if (match.isEmpty()) {
			source.sendFailure(Component.literal("No fixed or planned site " + key + " (crash keys are looked up at their chunk, others within " + MAX_RADIUS + " blocks)"));
			return 0;
		}
		Candidate c = match.get();
		PlacedSite site = c.fixed().isPresent() ? Sites.placeFixed(level, c.fixed().get()) : Sites.placeCrashSite(level, c.planned().get());
		if (site == null) {
			source.sendFailure(Component.literal("Site " + key + " could not be placed (template " + c.template() + "; see the log)"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Placed site " + key + " at " + site.origin().toShortString()
				+ " (" + site.chests().size() + " containers, " + site.guards() + " guards)"), true);
		return 1;
	}

	/** The chunk a crash-site key names, as {x, z}; empty for fixed keys or anything that does not parse. */
	private static Optional<int[]> keyChunk(String key) {
		if (!key.startsWith("crash:")) {
			return Optional.empty();
		}
		String[] xz = key.substring(key.lastIndexOf(':') + 1).split(",");
		try {
			return xz.length == 2 ? Optional.of(new int[]{Integer.parseInt(xz[0]), Integer.parseInt(xz[1])}) : Optional.empty();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
