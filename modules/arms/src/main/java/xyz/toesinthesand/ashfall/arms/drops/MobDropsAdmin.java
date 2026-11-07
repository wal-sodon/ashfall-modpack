package xyz.toesinthesand.ashfall.arms.drops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import xyz.toesinthesand.ashfall.arms.AshfallArms;
import xyz.toesinthesand.ashfall.arms.item.MobDropRule;
import xyz.toesinthesand.ashfall.arms.item.MobDrops;
import xyz.toesinthesand.ashfall.salvage.admin.AdminAccess;
import xyz.toesinthesand.ashfall.salvage.admin.OverridePack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Server side of the Mob Drops screen and {@code /mobdrops}.
 * <p>
 * A save writes the entity's whole rule as {@code data/ashfall/ashfall/mob_drops/<ns>/<path>.json} into the world
 * override datapack and reloads datapacks, which rebuilds the entity's loot table with the new pools. The answer
 * goes back only after the reload, so the screen always shows rules that are really in effect. Every payload is
 * checked for operator level 2 here.
 */
public final class MobDropsAdmin {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/**
	 * Outcome of one action.
	 *
	 * @param reload the datapack reload the action started (already complete when there was none)
	 */
	public record Result(boolean ok, String message, CompletableFuture<Void> reload) {
		static Result ok(String message) {
			return new Result(true, message, CompletableFuture.completedFuture(null));
		}

		static Result error(String message) {
			return new Result(false, message, CompletableFuture.completedFuture(null));
		}
	}

	private MobDropsAdmin() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(MobDropsActionPayload.TYPE, MobDropsActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MobDropsSyncPayload.TYPE, MobDropsSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MobDropsActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> {
				Result result = handle(player, player.permissions(), payload);
				if (!AdminAccess.isAdmin(player.permissions())) {
					player.sendSystemMessage(Component.literal(result.message()));
					return;
				}
				boolean open = payload.action() == MobDropsActionPayload.Action.OPEN;
				result.reload().whenComplete((ignored, error) -> server.execute(() -> reply(player, open,
						error == null ? result : Result.error("Saved, but the datapack reload failed: " + error.getMessage()))));
			});
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> registerCommand(dispatcher));
	}

	/**
	 * {@code /mobdrops gui|list|add|remove|clear|reset} (op level 2). {@code gui} opens the screen; the rest work from
	 * the server console too, because a dedicated-server admin (or a headless check) has no screen to click:
	 * <ul>
	 * <li>{@code add <entity> <item> <chance> [<min> <max> [<any_kill>]]} appends a drop to the entity's current
	 * rule;</li>
	 * <li>{@code remove <entity> <index>} drops row {@code index} (1-based, as {@code list} prints them);</li>
	 * <li>{@code clear <entity>} saves an empty rule, so the entity drops nothing extra;</li>
	 * <li>{@code reset <entity>} moves the override aside and returns to the shipped drops.</li>
	 * </ul>
	 * Each goes through the same validated {@link #save}/{@link #reset} as the screen, so the files are identical.
	 */
	private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mobdrops")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("gui").executes(ctx -> {
					reply(ctx.getSource().getPlayerOrException(), true, Result.ok(""));
					return 1;
				}))
				.then(Commands.literal("list").executes(ctx -> {
					Map<Identifier, List<MobDropRule.Drop>> rules = MobDrops.rules();
					for (var rule : rules.entrySet()) {
						StringBuilder line = new StringBuilder(rule.getKey().toString())
								.append(MobDrops.isOverridden(rule.getKey()) ? " (override):" : ":");
						for (MobDropRule.Drop d : rule.getValue()) {
							line.append(' ').append(d.item()).append(' ').append(Math.round(d.chance() * 1000) / 10.0).append('%')
									.append(d.min() == d.max() ? " x" + d.min() : " x" + d.min() + "-" + d.max())
									.append(d.killedByPlayer() ? "" : " (any kill)").append(';');
						}
						ctx.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
					}
					return rules.size();
				}))
				.then(Commands.literal("add").then(Commands.argument("entity", IdentifierArgument.id()).suggests(ENTITIES)
						.then(Commands.argument("item", IdentifierArgument.id()).suggests(ITEMS)
								.then(Commands.argument("chance", FloatArgumentType.floatArg(0f, 1f))
										.executes(ctx -> add(ctx, 1, 1, false))
										.then(Commands.argument("min", IntegerArgumentType.integer(1, MobDropRule.Drop.MAX_COUNT))
												.then(Commands.argument("max", IntegerArgumentType.integer(1, MobDropRule.Drop.MAX_COUNT))
														.executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "min"),
																IntegerArgumentType.getInteger(ctx, "max"), false))
														.then(Commands.argument("any_kill", BoolArgumentType.bool())
																.executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "min"),
																		IntegerArgumentType.getInteger(ctx, "max"),
																		BoolArgumentType.getBool(ctx, "any_kill"))))))))))
				.then(Commands.literal("remove").then(Commands.argument("entity", IdentifierArgument.id()).suggests(RULES)
						.then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(MobDropsAdmin::remove))))
				.then(Commands.literal("clear").then(Commands.argument("entity", IdentifierArgument.id()).suggests(ENTITIES)
						.executes(ctx -> report(ctx, save(actor(ctx), ctx.getSource().getServer(),
								IdentifierArgument.getId(ctx, "entity").toString(), List.of())))))
				.then(Commands.literal("reset").then(Commands.argument("entity", IdentifierArgument.id()).suggests(RULES)
						.executes(ctx -> report(ctx, reset(actor(ctx), ctx.getSource().getServer(),
								IdentifierArgument.getId(ctx, "entity").toString()))))));
	}

	private static final SuggestionProvider<CommandSourceStack> ENTITIES = (c, b) -> SharedSuggestionProvider.suggestResource(
			BuiltInRegistries.ENTITY_TYPE.keySet(), b);
	private static final SuggestionProvider<CommandSourceStack> ITEMS = (c, b) -> SharedSuggestionProvider.suggestResource(
			BuiltInRegistries.ITEM.keySet(), b);
	private static final SuggestionProvider<CommandSourceStack> RULES = (c, b) -> SharedSuggestionProvider.suggestResource(
			MobDrops.rules().keySet(), b);

	private static String actor(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getTextName();
	}

	/** The entity's current drops as form rows (empty when it has no rule yet). */
	private static List<DropForm> current(Identifier entity) {
		return new ArrayList<>(MobDrops.rules().getOrDefault(entity, List.of()).stream().map(DropForm::of).toList());
	}

	private static int add(CommandContext<CommandSourceStack> ctx, int min, int max, boolean anyKill) {
		Identifier entity = IdentifierArgument.getId(ctx, "entity");
		List<DropForm> forms = current(entity);
		forms.add(new DropForm(IdentifierArgument.getId(ctx, "item").toString(), FloatArgumentType.getFloat(ctx, "chance"),
				!anyKill, min, max));
		return report(ctx, save(actor(ctx), ctx.getSource().getServer(), entity.toString(), forms));
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		Identifier entity = IdentifierArgument.getId(ctx, "entity");
		List<DropForm> forms = current(entity);
		int index = IntegerArgumentType.getInteger(ctx, "index");
		if (index > forms.size()) {
			ctx.getSource().sendFailure(Component.literal(entity + " has " + forms.size() + " drop(s)"));
			return 0;
		}
		forms.remove(index - 1);
		return report(ctx, save(actor(ctx), ctx.getSource().getServer(), entity.toString(), forms));
	}

	/** Sends the result once the datapack reload it started is done, so "saved" means "in effect". */
	private static int report(CommandContext<CommandSourceStack> ctx, Result result) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		if (!result.ok()) {
			source.sendFailure(Component.literal(result.message()));
			return 0;
		}
		result.reload().whenComplete((ignored, error) -> server.execute(() -> {
			if (error == null) {
				source.sendSuccess(() -> Component.literal(result.message()), true);
			} else {
				source.sendFailure(Component.literal("Saved, but the datapack reload failed: " + error.getMessage()));
			}
		}));
		return 1;
	}

	/**
	 * Runs one screen action. Public so gametests drive the exact path a client payload takes.
	 *
	 * @param permissions the sender's permissions ({@code player.permissions()} on the network path)
	 */
	public static Result handle(ServerPlayer player, PermissionSet permissions, MobDropsActionPayload payload) {
		if (!AdminAccess.check(player, permissions, "mob_drops/" + payload.action().name().toLowerCase())) {
			return Result.error("You need operator permission (level 2) to change mob drops");
		}
		MinecraftServer server = player.level().getServer();
		return switch (payload.action()) {
			case OPEN, REFRESH -> Result.ok("");
			case SAVE -> save(player.getName().getString(), server, payload.entity(), payload.drops());
			case RESET -> reset(player.getName().getString(), server, payload.entity());
		};
	}

	private static Identifier entityId(String raw) {
		Identifier id = Identifier.tryParse(raw.trim());
		if (id == null) {
			return null;
		}
		// A shipped rule may name a modded entity that is not installed; it can still be edited.
		return BuiltInRegistries.ENTITY_TYPE.containsKey(id) || MobDrops.rules().containsKey(id) ? id : null;
	}

	static Result save(String actor, MinecraftServer server, String rawEntity, List<DropForm> forms) {
		Identifier entity = entityId(rawEntity);
		if (entity == null) {
			return Result.error("Unknown entity " + rawEntity.trim());
		}
		if (forms.size() > MobDropsActionPayload.MAX_DROPS) {
			return Result.error("At most " + MobDropsActionPayload.MAX_DROPS + " drops per entity");
		}
		List<MobDropRule.Drop> drops = new ArrayList<>();
		for (int i = 0; i < forms.size(); i++) {
			DropForm f = forms.get(i);
			String row = "Drop " + (i + 1) + ": ";
			Identifier item = Identifier.tryParse(f.item().trim());
			if (item == null || !BuiltInRegistries.ITEM.containsKey(item) || item.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
				return Result.error(row + "unknown item " + f.item().trim());
			}
			if (!(f.chance() >= 0 && f.chance() <= 1)) {
				return Result.error(row + "chance must be between 0 and 1");
			}
			if (f.min() < 1 || f.max() > MobDropRule.Drop.MAX_COUNT || f.min() > f.max()) {
				return Result.error(row + "count must be 1 to " + MobDropRule.Drop.MAX_COUNT + " with min <= max");
			}
			drops.add(new MobDropRule.Drop(item, f.chance(), f.killedByPlayer(), f.min(), f.max()));
		}
		MobDropRule rule = new MobDropRule(entity, List.copyOf(drops));
		String json = GSON.toJson(MobDropRule.CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow()) + "\n";
		Path file;
		try {
			file = OverridePack.write(server, MobDrops.filePath(entity), json);
		} catch (IOException e) {
			AshfallArms.LOGGER.error("Could not write the mob drop override for {}", entity, e);
			return Result.error("Could not write the override file: " + e.getMessage());
		}
		AshfallArms.LOGGER.info("{} saved mob drops for {} ({} drops) to {}", actor, entity, drops.size(), file);
		return new Result(true, "Saved " + drops.size() + " drop(s) for " + entity + "; datapacks reloaded", OverridePack.reload(server));
	}

	static Result reset(String actor, MinecraftServer server, String rawEntity) {
		Identifier entity = Identifier.tryParse(rawEntity.trim());
		if (entity == null) {
			return Result.error("Unknown entity " + rawEntity.trim());
		}
		Optional<Path> moved;
		try {
			moved = OverridePack.disable(server, MobDrops.filePath(entity));
		} catch (IOException e) {
			return Result.error("Could not move the override aside: " + e.getMessage());
		}
		if (moved.isEmpty()) {
			return Result.error(entity + " has no override to reset");
		}
		AshfallArms.LOGGER.info("{} reset mob drops for {} (override kept as {})", actor, entity, moved.get());
		return new Result(true, "Reset " + entity + " to the shipped drops (override kept as " + moved.get().getFileName()
				+ "); datapacks reloaded", OverridePack.reload(server));
	}

	static MobDropsSyncPayload sync(boolean open, Result result) {
		List<MobDropView> views = MobDrops.rules().entrySet().stream()
				.map(e -> new MobDropView(e.getKey().toString(), e.getValue().stream().map(DropForm::of).toList(), MobDrops.isOverridden(e.getKey())))
				.toList();
		List<String> entities = open ? BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Identifier::toString).sorted().toList() : List.of();
		List<String> items = open ? BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString).sorted().toList() : List.of();
		return new MobDropsSyncPayload(views, entities, items, open, result.message(), !result.ok());
	}

	private static void reply(ServerPlayer player, boolean open, Result result) {
		if (!ServerPlayNetworking.canSend(player, MobDropsSyncPayload.TYPE)) {
			if (!result.message().isEmpty()) {
				player.sendSystemMessage(Component.literal(result.message()));
			} else if (open) {
				player.sendSystemMessage(Component.literal("The Mob Drops screen needs Ashfall on the client; edit data/<ns>/ashfall/mob_drops instead"));
			}
			return;
		}
		ServerPlayNetworking.send(player, sync(open, result));
	}
}
