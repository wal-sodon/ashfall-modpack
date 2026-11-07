package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.loot.ContainerFilter;
import xyz.toesinthesand.ashfall.salvage.loot.LootRefill;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.loot.LootZone;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneActionPayload;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneActionPayload.Action;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneAdmin;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneForm;
import xyz.toesinthesand.ashfall.salvage.loot.LootZoneTransfer;
import xyz.toesinthesand.ashfall.salvage.loot.ZoneManager;

import java.util.List;
import java.util.UUID;

/**
 * The Loot Zone screen's server side, driven through the same handler a client payload reaches: non-ops are
 * refused (plan verification: "Non-ops cannot use admin commands or payloads"), and an op can create, edit, export
 * and re-import a zone with every field the screen shows.
 */
public class LootZoneAdminGameTest {
	private static final String TABLE = "minecraft:chests/simple_dungeon";
	private static final String OTHER_TABLE = "minecraft:chests/abandoned_mineshaft";
	private static final PermissionSet OP = LevelBasedPermissionSet.GAMEMASTER;

	private static String uniqueId(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
	}

	private static LootZoneForm form(String id, ServerLevel level, BlockPos center, int radius, String type, String table,
									 boolean regen, long delay, long quiet) {
		return new LootZoneForm(id, level.dimension().identifier().toString(), center.getX(), center.getY(), center.getZ(),
				radius, type, table, regen, delay, quiet, 24);
	}

	@GameTest
	public void nonOpsAreRejectedForEveryAction(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		ZoneManager manager = ZoneManager.get(level.getServer());
		String id = uniqueId("nonop");
		BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
		LootZoneForm attempt = form(id, level, center, 4, LootZone.ANY, TABLE, true, 100, 100);

		for (PermissionSet perms : List.of(PermissionSet.NO_PERMISSIONS, LevelBasedPermissionSet.MODERATOR)) {
			for (Action action : Action.values()) {
				LootZoneAdmin.Result result = LootZoneAdmin.handle(player, perms,
						new LootZoneActionPayload(action, action == Action.SAVE ? "" : id, attempt, "{\"zones\":[]}"));
				helper.assertTrue(!result.ok(), action + " was accepted from a non-op");
				helper.assertTrue(result.clipboard().isEmpty(), action + " leaked an export to a non-op");
			}
		}
		helper.assertTrue(manager.zone(id) == null, "a non-op created a zone");

		// The same zone, made by an op, survives a non-op's DELETE.
		helper.assertTrue(LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "", attempt, "")).ok(), "op create");
		try {
			LootZoneAdmin.handle(player, PermissionSet.NO_PERMISSIONS, LootZoneActionPayload.of(Action.DELETE, id));
			helper.assertTrue(manager.zone(id) != null, "a non-op deleted a zone");
		} finally {
			LootZoneAdmin.handle(player, OP, LootZoneActionPayload.of(Action.DELETE, id));
		}
		helper.succeed();
	}

	@GameTest
	public void opCreatesAndEditsAZoneThroughThePayloadHandler(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos chestRel = new BlockPos(1, 1, 1), barrelRel = new BlockPos(3, 1, 1);
		helper.setBlock(chestRel, Blocks.CHEST);
		helper.setBlock(barrelRel, Blocks.BARREL);
		ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
		BarrelBlockEntity barrel = helper.getBlockEntity(barrelRel, BarrelBlockEntity.class);
		BlockPos center = helper.absolutePos(chestRel);
		ZoneManager manager = ZoneManager.get(level.getServer());
		String id = uniqueId("gui");

		try {
			// Bad input is refused with a message and changes nothing.
			helper.assertTrue(!LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "",
					form(id, level, center, 4, LootZone.ANY, "minecraft:no_such_table", true, 100, 100), "")).ok(), "unknown table accepted");
			helper.assertTrue(!LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "",
					form("has space", level, center, 4, LootZone.ANY, TABLE, true, 100, 100), "")).ok(), "bad id accepted");
			helper.assertTrue(!LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "",
					form(id, level, center, 0, LootZone.ANY, TABLE, true, 100, 100), "")).ok(), "radius 0 accepted");
			helper.assertTrue(!LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "",
					form(id, level, center, 4, "minecraft:not_a_block", TABLE, true, 100, 100), "")).ok(), "unknown block accepted");
			helper.assertTrue(manager.zone(id) == null, "a refused save created the zone");

			// Create: only the chest (container type minecraft:chest), radius 1 so the barrel is outside anyway.
			LootZoneAdmin.Result created = LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "",
					form(id, level, center, 1, "chest", TABLE, false, 1234, 567), ""));
			helper.assertTrue(created.ok(), "create failed: " + created.message());
			LootZone zone = manager.zone(id);
			helper.assertTrue(zone != null, "zone not stored");
			helper.assertValueEqual(zone.center(), center, "centre");
			helper.assertValueEqual(zone.radius(), 1, "radius");
			helper.assertValueEqual(zone.containerType(), ContainerFilter.block(Identifier.withDefaultNamespace("chest")), "type (bare id gets minecraft:)");
			helper.assertValueEqual(zone.table(), Identifier.parse(TABLE), "table");
			helper.assertValueEqual(zone.refill().enabled(), false, "regen");
			helper.assertValueEqual(zone.refill().delayTicks(), 1234L, "delay");
			helper.assertValueEqual(zone.refill().quietTicks(), 567L, "quiet");
			helper.assertValueEqual(zone.refill().noPlayerRadius(), 24, "no-player radius");
			helper.assertValueEqual(chest.getLootTable(), LootRefill.tableKey(Identifier.parse(TABLE)), "chest armed on create");
			helper.assertTrue(barrel.getAttached(AshfallSalvage.LOOT_STATE) == null, "barrel outside the zone was claimed");

			// A second create with the same name is refused.
			helper.assertTrue(!LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, "",
					form(id, level, center, 1, "chest", TABLE, false, 1234, 567), "")).ok(), "duplicate create accepted");

			// Edit: bigger radius, a block tag that matches barrels, another table, regen on.
			LootZoneAdmin.Result edited = LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, id,
					form(id, level, center, 3, "#minecraft:guarded_by_piglins", OTHER_TABLE, true, 50, 60), ""));
			helper.assertTrue(edited.ok(), "edit failed: " + edited.message());
			zone = manager.zone(id);
			helper.assertValueEqual(zone.radius(), 3, "edited radius");
			helper.assertTrue(zone.containerType().isTag(), "edited type is a tag");
			helper.assertValueEqual(zone.table(), Identifier.parse(OTHER_TABLE), "edited table");
			helper.assertValueEqual(zone.refill().enabled(), true, "edited regen");
			LootState barrelState = barrel.getAttached(AshfallSalvage.LOOT_STATE);
			helper.assertTrue(barrelState != null && barrelState.zone().filter(id::equals).isPresent(), "tag edit claimed the barrel");
			helper.assertValueEqual(barrel.getLootTable(), LootRefill.tableKey(Identifier.parse(OTHER_TABLE)), "barrel armed with the new table");

			// Renames are refused rather than silently creating a copy.
			helper.assertTrue(!LootZoneAdmin.handle(player, OP, new LootZoneActionPayload(Action.SAVE, id,
					form(id + "_renamed", level, center, 3, LootZone.ANY, TABLE, true, 50, 60), "")).ok(), "rename accepted");

			// Refill now and scan go through the same handler.
			helper.assertTrue(LootZoneAdmin.handle(player, OP, LootZoneActionPayload.of(Action.REFILL, id)).ok(), "refill");
			helper.assertTrue(LootZoneAdmin.handle(player, OP, LootZoneActionPayload.of(Action.SCAN, id)).ok(), "scan");

			// Export, delete, then import the export: the zone comes back unchanged.
			LootZone beforeExport = manager.zone(id);
			String json = LootZoneTransfer.toJson(List.of(beforeExport));
			helper.assertTrue(LootZoneAdmin.handle(player, OP, LootZoneActionPayload.of(Action.DELETE, id)).ok(), "delete");
			helper.assertTrue(manager.zone(id) == null, "deleted zone still stored");
			helper.assertTrue(barrel.getAttached(AshfallSalvage.LOOT_STATE).zone().isEmpty(), "delete released the barrel");
			LootZoneAdmin.Result imported = LootZoneAdmin.handle(player, OP,
					new LootZoneActionPayload(Action.IMPORT_TEXT, "", LootZoneForm.BLANK, json));
			helper.assertTrue(imported.ok(), "import failed: " + imported.message());
			helper.assertValueEqual(manager.zone(id), beforeExport, "imported zone");
			helper.assertTrue(!LootZoneAdmin.handle(player, OP,
					new LootZoneActionPayload(Action.IMPORT_TEXT, "", LootZoneForm.BLANK, "{not json")).ok(), "garbage import accepted");
		} finally {
			LootZone left = manager.zone(id);
			if (left != null) {
				LootRefill.releaseAll(level, left);
				manager.removeZone(id);
			}
		}
		helper.succeed();
	}
}
