package xyz.toesinthesand.ashfall.salvage.loot;

import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.Level;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.admin.AdminAccess;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server side of the Loot Zone screen: answers {@link LootZoneActionPayload}s with a {@link LootZoneSyncPayload}.
 * <p>
 * Every action does exactly what the matching {@code /lootzone} subcommand does (same container claiming, same
 * release on shrink), so the GUI and the commands can be mixed freely. Every payload is permission-checked here,
 * never on the client.
 */
public final class LootZoneAdmin {
	/** What {@code StringArgumentType.word()} accepts, so a GUI-made zone can still be named in commands. */
	static final Pattern VALID_ID = Pattern.compile("[0-9A-Za-z_.+-]{1,64}");
	public static final int MAX_RADIUS = 512;

	/** Outcome of one action, shown in the screen's status line. */
	public record Result(boolean ok, String message, String clipboard) {
		static Result ok(String message) {
			return new Result(true, message, "");
		}

		static Result error(String message) {
			return new Result(false, message, "");
		}
	}

	private LootZoneAdmin() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(LootZoneActionPayload.TYPE, LootZoneActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LootZoneSyncPayload.TYPE, LootZoneSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LootZoneActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				Result result = handle(player, player.permissions(), payload);
				if (!AdminAccess.isAdmin(player.permissions())) {
					// No zone list for a rejected sender, just the refusal.
					player.sendSystemMessage(Component.literal(result.message()));
					return;
				}
				boolean open = payload.action() == LootZoneActionPayload.Action.OPEN && result.ok();
				reply(player, open, result);
			});
		});
	}

	/** Opens the screen for {@code player} (from {@code /lootzone gui}, which is already op-only). */
	public static void open(ServerPlayer player) {
		reply(player, true, Result.ok(""));
	}

	/**
	 * Runs one GUI action. Public so gametests can drive the exact path a client payload takes.
	 *
	 * @param permissions the sender's permissions ({@code player.permissions()} on the network path)
	 */
	public static Result handle(ServerPlayer player, PermissionSet permissions, LootZoneActionPayload payload) {
		if (!AdminAccess.check(player, permissions, "loot_zone/" + payload.action().name().toLowerCase())) {
			return Result.error("You need operator permission (level 2) to manage loot zones");
		}
		MinecraftServer server = player.level().getServer();
		ZoneManager manager = ZoneManager.get(server);
		return switch (payload.action()) {
			case OPEN, REFRESH -> Result.ok("");
			case SAVE -> save(player, manager, payload.zoneId(), payload.form());
			case EXPORT -> export(server, manager);
			case IMPORT_TEXT -> importText(server, payload.text());
			case IMPORT_FILE -> importFile(server);
			case SCAN, REFILL, DELETE, TELEPORT -> {
				LootZone zone = manager.zone(payload.zoneId());
				if (zone == null) {
					yield Result.error("No loot zone named '" + payload.zoneId() + "'");
				}
				ServerLevel level = levelOf(server, zone, player.level());
				yield switch (payload.action()) {
					case SCAN -> Result.ok("Loot zone '" + zone.id() + "' now owns "
							+ LootRefill.apply(level, zone, zone.radius()) + " loaded container(s)");
					case REFILL -> Result.ok("Refilled " + LootRefill.forceRefill(level, zone)
							+ " container(s) in loot zone '" + zone.id() + "'");
					case DELETE -> {
						int released = LootRefill.releaseAll(level, zone);
						manager.removeZone(zone.id());
						AshfallSalvage.LOGGER.info("{} deleted loot zone '{}' from the admin GUI", player.getName().getString(), zone.id());
						yield Result.ok("Deleted loot zone '" + zone.id() + "'; " + released + " loaded container(s) reverted");
					}
					default -> {
						BlockPos c = zone.center();
						player.teleportTo(level, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
						yield Result.ok("Teleported to loot zone '" + zone.id() + "'");
					}
				};
			}
		};
	}

	private static ServerLevel levelOf(MinecraftServer server, LootZone zone, ServerLevel fallback) {
		ServerLevel level = server.getLevel(zone.dimension());
		return level != null ? level : fallback;
	}

	/** Creates ({@code zoneId} empty) or updates a zone from the form, validating like the command does. */
	static Result save(ServerPlayer player, ZoneManager manager, String zoneId, LootZoneForm form) {
		MinecraftServer server = player.level().getServer();
		boolean create = zoneId.isEmpty();
		String id = form.id().trim();
		if (!VALID_ID.matcher(id).matches()) {
			return Result.error("Zone names use letters, digits and _ . + - (no spaces, at most 64)");
		}
		LootZone old = create ? null : manager.zone(zoneId);
		if (!create && old == null) {
			return Result.error("No loot zone named '" + zoneId + "'");
		}
		if (!create && !zoneId.equals(id)) {
			return Result.error("Zones cannot be renamed: create '" + id + "' and delete '" + zoneId + "'");
		}
		if (create && manager.zone(id) != null) {
			return Result.error("Loot zone '" + id + "' already exists");
		}
		if (form.radius() < 1 || form.radius() > MAX_RADIUS) {
			return Result.error("Radius must be 1 to " + MAX_RADIUS);
		}
		if (form.delayTicks() < 0 || form.quietTicks() < 0) {
			return Result.error("Refill delay and quiet period cannot be negative");
		}
		if (form.noPlayerRadius() < 0 || form.noPlayerRadius() > 256) {
			return Result.error("No-player radius must be 0 to 256");
		}
		DataResult<ContainerFilter> parsedType = ContainerFilter.parse(form.containerType());
		if (parsedType.isError()) {
			return Result.error("Container type must be 'any', a block id or #tag");
		}
		ContainerFilter type = parsedType.getOrThrow();
		if (!type.isKnown()) {
			return Result.error("Unknown block " + type.spec() + " (use a block id, #tag or 'any')");
		}
		Identifier table = Identifier.tryParse(form.table().trim());
		if (table == null || !tableExists(server, table)) {
			return Result.error("Unknown loot table " + form.table().trim());
		}
		ResourceKey<Level> dimension;
		if (form.dimension().isBlank()) {
			dimension = old != null ? old.dimension() : player.level().dimension();
		} else {
			Identifier dim = Identifier.tryParse(form.dimension().trim());
			dimension = dim == null ? null : ResourceKey.create(Registries.DIMENSION, dim);
			if (dimension == null || server.getLevel(dimension) == null) {
				return Result.error("Unknown dimension " + form.dimension().trim());
			}
		}
		BuildingProfile.Refill refill = new BuildingProfile.Refill(form.regen(), form.delayTicks(), form.noPlayerRadius(), form.quietTicks());
		LootZone zone = new LootZone(id, dimension, new BlockPos(form.x(), form.y(), form.z()), form.radius(), type, table,
				refill, old != null ? old.mode() : LootZone.SHARED);
		manager.putZone(zone);
		ServerLevel level = levelOf(server, zone, player.level());
		int count;
		if (old != null && (!old.center().equals(zone.center()) || !old.dimension().equals(zone.dimension()))) {
			// Moved: let go of everything around the old centre first, then claim around the new one.
			LootRefill.releaseAll(levelOf(server, old, player.level()), old);
			count = LootRefill.apply(level, zone, zone.radius());
		} else {
			count = LootRefill.apply(level, zone, old != null ? old.radius() : zone.radius());
		}
		AshfallSalvage.LOGGER.info("{} {} loot zone '{}' from the admin GUI", player.getName().getString(), create ? "created" : "updated", id);
		return Result.ok((create ? "Created" : "Updated") + " loot zone '" + id + "'; " + count + " container(s) in loaded chunks");
	}

	static boolean tableExists(MinecraftServer server, Identifier table) {
		return server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).get(LootRefill.tableKey(table)).isPresent();
	}

	static List<String> tableIds(MinecraftServer server) {
		return server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
				.map(k -> k.identifier().toString()).sorted().toList();
	}

	/**
	 * Writes every zone to {@link LootZoneTransfer#FILE} (an older export is kept under a timestamped name, never
	 * overwritten) and hands the same JSON to the client's clipboard.
	 */
	static Result export(MinecraftServer server, ZoneManager manager) {
		String json = LootZoneTransfer.toJson(manager.zones());
		Path file = LootZoneTransfer.file(server);
		try {
			Files.createDirectories(file.getParent());
			if (Files.exists(file)) {
				String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
				Files.move(file, file.resolveSibling("loot_zones." + stamp + ".json"));
			}
			Files.writeString(file, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			AshfallSalvage.LOGGER.error("Could not write loot zone export {}", file, e);
			return new Result(false, "Could not write " + LootZoneTransfer.FILE + ": " + e.getMessage() + " (copied to clipboard anyway)", json);
		}
		return new Result(true, "Exported " + manager.zones().size() + " zone(s) to " + LootZoneTransfer.FILE + " and the clipboard", json);
	}

	static Result importText(MinecraftServer server, String text) {
		DataResult<List<LootZone>> parsed = LootZoneTransfer.fromJson(text);
		return parsed.mapOrElse(zones -> importZones(server, zones),
				error -> Result.error("Import failed: " + error.message()));
	}

	static Result importFile(MinecraftServer server) {
		Path file = LootZoneTransfer.file(server);
		if (!Files.exists(file)) {
			return Result.error("No " + LootZoneTransfer.FILE + " in this world (export first, or place one there)");
		}
		try {
			return importText(server, Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			return Result.error("Could not read " + LootZoneTransfer.FILE + ": " + e.getMessage());
		}
	}

	/**
	 * Adds or replaces each zone by id and claims its loaded containers. Zones with an id the commands could not
	 * name are skipped; zones in a dimension this server lacks are kept (they apply once the dimension exists).
	 */
	public static Result importZones(MinecraftServer server, List<LootZone> zones) {
		ZoneManager manager = ZoneManager.get(server);
		int added = 0, replaced = 0, skipped = 0, containers = 0;
		for (LootZone zone : zones) {
			if (!VALID_ID.matcher(zone.id()).matches()) {
				skipped++;
				continue;
			}
			LootZone old = manager.zone(zone.id());
			if (old != null) {
				ServerLevel oldLevel = server.getLevel(old.dimension());
				if (oldLevel != null) {
					LootRefill.releaseAll(oldLevel, old);
				}
				replaced++;
			} else {
				added++;
			}
			manager.putZone(zone);
			ServerLevel level = server.getLevel(zone.dimension());
			if (level != null) {
				containers += LootRefill.apply(level, zone, zone.radius());
			}
		}
		String msg = "Imported " + (added + replaced) + " zone(s): " + added + " new, " + replaced + " replaced"
				+ (skipped > 0 ? ", " + skipped + " skipped (bad id)" : "") + "; " + containers + " loaded container(s)";
		AshfallSalvage.LOGGER.info(msg);
		return Result.ok(msg);
	}

	static LootZoneSyncPayload sync(MinecraftServer server, boolean open, Result result) {
		List<LootZoneForm> zones = ZoneManager.get(server).zones().stream().map(LootZoneForm::of).toList();
		return new LootZoneSyncPayload(zones, open ? tableIds(server) : List.of(), open, result.message(), !result.ok(), result.clipboard());
	}

	private static void reply(ServerPlayer player, boolean open, Result result) {
		if (!ServerPlayNetworking.canSend(player, LootZoneSyncPayload.TYPE)) {
			if (!result.message().isEmpty()) {
				player.sendSystemMessage(Component.literal(result.message()));
			} else if (open) {
				player.sendSystemMessage(Component.literal("The Loot Zone screen needs Ashfall on the client; use /lootzone instead"));
			}
			return;
		}
		ServerPlayNetworking.send(player, sync(player.level().getServer(), open, result));
	}
}
