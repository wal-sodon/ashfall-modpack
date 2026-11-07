package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code /lootzone} (permission level 2): create, inspect, edit and remove admin loot zones, open the Loot Zone
 * screen ({@code gui}), and export or import every zone as {@code <world>/ashfall/loot_zones.json}.
 * Zone operations only reach loaded chunks; containers in unloaded chunks are picked up by
 * {@code scan} once someone has been there (and deleted zones are dropped lazily on open).
 */
public final class LootZoneCommand {
	private static final DynamicCommandExceptionType UNKNOWN_ZONE =
			new DynamicCommandExceptionType(id -> Component.literal("No loot zone named '" + id + "'"));
	private static final DynamicCommandExceptionType UNKNOWN_TABLE =
			new DynamicCommandExceptionType(id -> Component.literal("Unknown loot table " + id));
	private static final DynamicCommandExceptionType UNKNOWN_BLOCK =
			new DynamicCommandExceptionType(id -> Component.literal("Unknown block " + id + " (use a block id or 'any')"));
	private static final DynamicCommandExceptionType ZONE_EXISTS =
			new DynamicCommandExceptionType(id -> Component.literal("Loot zone '" + id + "' already exists"));

	private static final SuggestionProvider<CommandSourceStack> ZONES = (ctx, b) ->
			SharedSuggestionProvider.suggest(ZoneManager.get(ctx.getSource().getServer()).zones().stream().map(LootZone::id), b);
	private static final SuggestionProvider<CommandSourceStack> TABLES = (ctx, b) ->
			SharedSuggestionProvider.suggestResource(lootTables(ctx.getSource().getServer()), b);
	private static final SuggestionProvider<CommandSourceStack> TYPES = (ctx, b) -> {
		SharedSuggestionProvider.suggest(Stream.of(LootZone.ANY), b);
		return SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), b);
	};

	private static final SuggestionProvider<CommandSourceStack> BLOCK_TAGS = (ctx, b) ->
			SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.getTags().map(t -> t.key().location()), b);

	private LootZoneCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lootzone")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("create")
						.then(Commands.argument("id", StringArgumentType.word())
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
										.then(Commands.argument("container_type", IdentifierArgument.id()).suggests(TYPES)
												.then(createTail(Commands.argument("table", IdentifierArgument.id()).suggests(TABLES)))))))
				.then(Commands.literal("list").executes(LootZoneCommand::list))
				.then(Commands.literal("info").then(zoneArg().executes(LootZoneCommand::info)))
				.then(Commands.literal("edit").then(zoneArg()
						.then(Commands.literal("table").then(Commands.argument("value", IdentifierArgument.id()).suggests(TABLES)
								.executes(ctx -> edit(ctx, z -> z.withTable(table(ctx, "value"))))))
						.then(Commands.literal("radius").then(Commands.argument("value", IntegerArgumentType.integer(1, 512))
								.executes(ctx -> edit(ctx, z -> z.withRadius(IntegerArgumentType.getInteger(ctx, "value"))))))
						.then(Commands.literal("regen").then(Commands.argument("value", BoolArgumentType.bool())
								.executes(ctx -> edit(ctx, z -> z.withRefill(withEnabled(z.refill(), BoolArgumentType.getBool(ctx, "value")))))))
						.then(Commands.literal("delay").then(Commands.argument("value", LongArgumentType.longArg(0))
								.executes(ctx -> edit(ctx, z -> z.withRefill(withDelay(z.refill(), LongArgumentType.getLong(ctx, "value")))))))
						.then(Commands.literal("type").then(Commands.argument("value", IdentifierArgument.id()).suggests(TYPES)
								.executes(ctx -> edit(ctx, z -> z.withContainerType(containerType(ctx, "value"))))))
						// A block tag: '#' does not parse as an id argument, so the tag gets its own literal.
						.then(Commands.literal("tag").then(Commands.argument("value", IdentifierArgument.id()).suggests(BLOCK_TAGS)
								.executes(ctx -> edit(ctx, z -> z.withContainerType(ContainerFilter.tag(IdentifierArgument.getId(ctx, "value")))))))
						.then(Commands.literal("quiet").then(Commands.argument("value", LongArgumentType.longArg(0))
								.executes(ctx -> edit(ctx, z -> z.withRefill(withQuiet(z.refill(), LongArgumentType.getLong(ctx, "value")))))))))
				.then(Commands.literal("gui").executes(ctx -> {
					LootZoneAdmin.open(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("export").executes(ctx -> report(ctx, LootZoneAdmin.export(ctx.getSource().getServer(),
						ZoneManager.get(ctx.getSource().getServer())))))
				.then(Commands.literal("import").executes(ctx -> report(ctx, LootZoneAdmin.importFile(ctx.getSource().getServer()))))
				.then(Commands.literal("scan").then(zoneArg().executes(LootZoneCommand::scan)))
				.then(Commands.literal("refill").then(zoneArg().executes(LootZoneCommand::refill)))
				.then(Commands.literal("delete").then(zoneArg().executes(LootZoneCommand::delete)))
				.then(Commands.literal("tp").then(zoneArg().executes(LootZoneCommand::tp))));
	}

	/** {@code <table> [regen <bool>] [delay <ticks>]} */
	private static ArgumentBuilder<CommandSourceStack, ?> createTail(ArgumentBuilder<CommandSourceStack, ?> table) {
		return table.executes(ctx -> create(ctx, BuildingProfile.Refill.DEFAULT.enabled(), BuildingProfile.Refill.DEFAULT.delayTicks()))
				.then(Commands.literal("regen").then(Commands.argument("regen", BoolArgumentType.bool())
						.executes(ctx -> create(ctx, BoolArgumentType.getBool(ctx, "regen"), BuildingProfile.Refill.DEFAULT.delayTicks()))
						.then(Commands.literal("delay").then(Commands.argument("delay", LongArgumentType.longArg(0))
								.executes(ctx -> create(ctx, BoolArgumentType.getBool(ctx, "regen"), LongArgumentType.getLong(ctx, "delay")))))))
				.then(Commands.literal("delay").then(Commands.argument("delay", LongArgumentType.longArg(0))
						.executes(ctx -> create(ctx, BuildingProfile.Refill.DEFAULT.enabled(), LongArgumentType.getLong(ctx, "delay")))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> zoneArg() {
		return Commands.argument("id", StringArgumentType.word()).suggests(ZONES);
	}

	private static Stream<Identifier> lootTables(MinecraftServer server) {
		return server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(k -> k.identifier());
	}

	private static Identifier table(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(ctx, arg);
		if (ctx.getSource().getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE)
				.get(LootRefill.tableKey(id)).isEmpty()) {
			throw UNKNOWN_TABLE.create(id);
		}
		return id;
	}

	private static Optional<Identifier> containerType(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(ctx, arg);
		if (id.getPath().equals(LootZone.ANY) && id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return Optional.empty();
		}
		if (!BuiltInRegistries.BLOCK.containsKey(id)) {
			throw UNKNOWN_BLOCK.create(id);
		}
		return Optional.of(id);
	}

	private static LootZone zone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "id");
		LootZone zone = ZoneManager.get(ctx.getSource().getServer()).zone(id);
		if (zone == null) {
			throw UNKNOWN_ZONE.create(id);
		}
		return zone;
	}

	private static ServerLevel levelOf(CommandContext<CommandSourceStack> ctx, LootZone zone) {
		ServerLevel level = ctx.getSource().getServer().getLevel(zone.dimension());
		return level != null ? level : ctx.getSource().getLevel();
	}

	private static BuildingProfile.Refill withEnabled(BuildingProfile.Refill r, boolean enabled) {
		return new BuildingProfile.Refill(enabled, r.delayTicks(), r.noPlayerRadius(), r.quietTicks());
	}

	private static BuildingProfile.Refill withQuiet(BuildingProfile.Refill r, long quiet) {
		return new BuildingProfile.Refill(r.enabled(), r.delayTicks(), r.noPlayerRadius(), quiet);
	}

	private static int report(CommandContext<CommandSourceStack> ctx, LootZoneAdmin.Result result) {
		if (result.ok()) {
			ctx.getSource().sendSuccess(() -> Component.literal(result.message()), true);
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal(result.message()));
		return 0;
	}

	private static BuildingProfile.Refill withDelay(BuildingProfile.Refill r, long delay) {
		return new BuildingProfile.Refill(r.enabled(), delay, r.noPlayerRadius(), r.quietTicks());
	}

	private static int create(CommandContext<CommandSourceStack> ctx, boolean regen, long delay) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "id");
		ZoneManager manager = ZoneManager.get(source.getServer());
		if (manager.zone(id) != null) {
			throw ZONE_EXISTS.create(id);
		}
		BuildingProfile.Refill refill = withDelay(withEnabled(BuildingProfile.Refill.DEFAULT, regen), delay);
		LootZone zone = new LootZone(id, source.getLevel().dimension(), BlockPos.containing(source.getPosition()),
				IntegerArgumentType.getInteger(ctx, "radius"), containerType(ctx, "container_type"), table(ctx, "table"),
				refill, LootZone.SHARED);
		manager.putZone(zone);
		int count = LootRefill.apply(source.getLevel(), zone, zone.radius());
		source.sendSuccess(() -> Component.literal("Created loot zone '" + id + "' with " + count + " container(s) using " + zone.table()), true);
		return count;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		var zones = ZoneManager.get(ctx.getSource().getServer()).zones();
		if (zones.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No loot zones"), false);
		}
		for (LootZone z : zones) {
			ctx.getSource().sendSuccess(() -> Component.literal(z.id() + ": " + z.dimension().identifier() + " "
					+ z.center().toShortString() + " r=" + z.radius() + " " + z.containerTypeName() + " -> " + z.table()), false);
		}
		return zones.size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		LootZone z = zone(ctx);
		ZoneManager manager = ZoneManager.get(ctx.getSource().getServer());
		ZoneManager.Presence p = manager.presence(z.areaKey());
		long now = ctx.getSource().getServer().overworld().getGameTime();
		int[] owned = {0};
		LootRefill.forEachLoadedContainer(levelOf(ctx, z), z, z.radius(), be -> {
			LootState s = be.getAttached(xyz.toesinthesand.ashfall.salvage.AshfallSalvage.LOOT_STATE);
			if (s != null && s.zone().filter(z.id()::equals).isPresent()) {
				owned[0]++;
			}
		});
		BuildingProfile.Refill r = z.refill();
		ctx.getSource().sendSuccess(() -> Component.literal("Loot zone '" + z.id() + "' (" + z.mode() + ")\n"
				+ " at " + z.dimension().identifier() + " " + z.center().toShortString() + " radius " + z.radius() + "\n"
				+ " containers: " + z.containerTypeName() + ", table " + z.table() + ", " + owned[0] + " loaded\n"
				+ " refill: " + (r.enabled() ? "on" : "off") + ", delay " + r.delayTicks() + "t, quiet " + r.quietTicks()
				+ "t, no-player radius " + r.noPlayerRadius() + "\n"
				+ " last player seen: " + (p.lastSeen() < 0 ? "never" : (now - p.lastSeen()) + "t ago")
				+ ", last quiet gap ended: " + (p.lastQuietGapEnd() < 0 ? "never" : (now - p.lastQuietGapEnd()) + "t ago")), false);
		return owned[0];
	}

	private interface ZoneEdit {
		LootZone apply(LootZone zone) throws CommandSyntaxException;
	}

	private static int edit(CommandContext<CommandSourceStack> ctx, ZoneEdit edit) throws CommandSyntaxException {
		LootZone old = zone(ctx);
		LootZone updated = edit.apply(old);
		ZoneManager.get(ctx.getSource().getServer()).putZone(updated);
		int count = LootRefill.apply(levelOf(ctx, updated), updated, old.radius());
		ctx.getSource().sendSuccess(() -> Component.literal("Updated loot zone '" + updated.id() + "'; " + count + " container(s) in loaded chunks"), true);
		return count;
	}

	private static int scan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		LootZone z = zone(ctx);
		int count = LootRefill.apply(levelOf(ctx, z), z, z.radius());
		ctx.getSource().sendSuccess(() -> Component.literal("Loot zone '" + z.id() + "' now owns " + count + " loaded container(s)"), true);
		return count;
	}

	private static int refill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		LootZone z = zone(ctx);
		int count = LootRefill.forceRefill(levelOf(ctx, z), z);
		ctx.getSource().sendSuccess(() -> Component.literal("Refilled " + count + " container(s) in loot zone '" + z.id() + "'"), true);
		return count;
	}

	private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		LootZone z = zone(ctx);
		int count = LootRefill.releaseAll(levelOf(ctx, z), z);
		ZoneManager.get(ctx.getSource().getServer()).removeZone(z.id());
		ctx.getSource().sendSuccess(() -> Component.literal("Deleted loot zone '" + z.id() + "'; " + count
				+ " loaded container(s) reverted to building rules (others revert when next opened)"), true);
		return count;
	}

	private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		LootZone z = zone(ctx);
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = levelOf(ctx, z);
		BlockPos c = z.center();
		player.teleportTo(level, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		ctx.getSource().sendSuccess(() -> Component.literal("Teleported to loot zone '" + z.id() + "'"), false);
		return 1;
	}
}
