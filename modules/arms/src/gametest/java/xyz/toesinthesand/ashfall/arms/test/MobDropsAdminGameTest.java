package xyz.toesinthesand.ashfall.arms.test;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import xyz.toesinthesand.ashfall.arms.drops.DropForm;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsActionPayload;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsActionPayload.Action;
import xyz.toesinthesand.ashfall.arms.drops.MobDropsAdmin;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.MobDropRule;
import xyz.toesinthesand.ashfall.arms.item.MobDrops;
import xyz.toesinthesand.ashfall.salvage.admin.OverridePack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Mob Drops screen's server side end to end: a non-op is refused, an op's save lands in the world override
 * datapack as a rule file, a real datapack reload picks it up and rebuilds the pig's loot table, a player kill then
 * drops exactly the configured items, and Reset moves the file aside (never deletes it) and reloads back.
 * <p>
 * Runs in its own test environment: environments run as separate batches, so the two datapack reloads here never
 * overlap another test's ticks.
 */
public class MobDropsAdminGameTest {
	private static final PermissionSet OP = LevelBasedPermissionSet.GAMEMASTER;
	private static final Identifier PIG = Identifier.withDefaultNamespace("pig");
	private static final Identifier SCRAP = Identifier.fromNamespaceAndPath("ashfall", "scrap_electronics");

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static int scrapAround(GameTestHelper helper, BlockPos rel) {
		return helper.getEntities(EntityTypes.ITEM, rel, 6).stream()
				.filter(i -> i.getItem().is(ArmsItems.SCRAP_ELECTRONICS)).mapToInt(i -> i.getItem().getCount()).sum();
	}

	private static String lootJson(MinecraftServer server, Identifier table) {
		HolderLookup.Provider registries = server.reloadableRegistries().lookup();
		LootTable loot = server.reloadableRegistries().getLootTable(MobDrops.tableFor(table));
		return LootTable.DIRECT_CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), loot).getOrThrow().toString();
	}

	@GameTest(environment = "ashfall_arms_test:datapack_reload", maxTicks = 2400)
	public void overrideIsWrittenReloadedAndDrops(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer admin = helper.makeMockServerPlayerInLevel();
		Path file = OverridePack.dataFile(server, MobDrops.filePath(PIG));
		List<DropForm> forms = List.of(new DropForm(SCRAP.toString(), 1f, true, 2, 2));

		// Non-ops: refused, and nothing is written.
		for (Action action : Action.values()) {
			MobDropsAdmin.Result denied = MobDropsAdmin.handle(admin, PermissionSet.NO_PERMISSIONS, new MobDropsActionPayload(action, PIG.toString(), forms));
			helper.assertTrue(!denied.ok(), action + " accepted from a non-op");
			helper.assertTrue(denied.reload().isDone(), action + " from a non-op started a reload");
		}
		helper.assertTrue(!Files.exists(file), "a non-op's save wrote " + file);

		// Bad rows are refused before anything is written.
		helper.assertTrue(!MobDropsAdmin.handle(admin, OP, new MobDropsActionPayload(Action.SAVE, PIG.toString(),
				List.of(new DropForm("ashfall:no_such_item", 0.5f, true, 1, 1)))).ok(), "unknown item accepted");
		helper.assertTrue(!MobDropsAdmin.handle(admin, OP, new MobDropsActionPayload(Action.SAVE, PIG.toString(),
				List.of(new DropForm(SCRAP.toString(), 1.5f, true, 1, 1)))).ok(), "chance above 1 accepted");
		helper.assertTrue(!MobDropsAdmin.handle(admin, OP, new MobDropsActionPayload(Action.SAVE, PIG.toString(),
				List.of(new DropForm(SCRAP.toString(), 0.5f, true, 3, 2)))).ok(), "min above max accepted");
		helper.assertTrue(!MobDropsAdmin.handle(admin, OP, new MobDropsActionPayload(Action.SAVE, "minecraft:no_such_mob", forms)).ok(),
				"unknown entity accepted");
		helper.assertTrue(!Files.exists(file), "a refused save wrote " + file);

		MobDropsAdmin.Result saved = MobDropsAdmin.handle(admin, OP, new MobDropsActionPayload(Action.SAVE, PIG.toString(), forms));
		helper.assertTrue(saved.ok(), "op save failed: " + saved.message());
		helper.assertTrue(Files.exists(file), "override file not written at " + file);
		helper.assertTrue(Files.exists(OverridePack.root(server).resolve("pack.mcmeta")), "override pack has no pack.mcmeta");
		String json;
		try {
			json = Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		MobDropRule written = MobDropRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
		helper.assertValueEqual(written, new MobDropRule(PIG, List.of(new MobDropRule.Drop(SCRAP, 1f, true, 2, 2))), "written rule");

		AtomicReference<CompletableFuture<Void>> resetReload = new AtomicReference<>();
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(saved.reload().isDone(), "waiting for the datapack reload"))
				.thenExecute(() -> {
					helper.assertTrue(!saved.reload().isCompletedExceptionally(), "datapack reload failed");
					helper.assertTrue(server.getPackRepository().getSelectedIds().contains(OverridePack.PACK_ID), "override pack not enabled");
					helper.assertValueEqual(MobDrops.rules().get(PIG), written.drops(), "rule after reload");
					helper.assertTrue(MobDrops.isOverridden(PIG), "rule not marked as an override");
					String loot = lootJson(server, PIG);
					helper.assertTrue(loot.contains(SCRAP.toString()) && loot.contains("minecraft:killed_by_player"),
							"pig loot table was not rebuilt with the override: " + loot);
					helper.assertTrue(MobDrops.rules().containsKey(Identifier.withDefaultNamespace("zombie")), "shipped rules lost on reload");

					floor(helper);
					helper.killAllEntitiesOfClass(ItemEntity.class);
					Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
					Pig pig = helper.spawn(EntityTypes.PIG, rel);
					pig.hurtServer(level, level.damageSources().playerAttack(killer), 1000f);
					helper.assertTrue(!pig.isAlive(), "pig survived");
					helper.assertValueEqual(scrapAround(helper, rel), 2, "scrap from a player kill");
					helper.killAllEntitiesOfClass(ItemEntity.class);
					Pig other = helper.spawn(EntityTypes.PIG, rel);
					other.kill(level);
					helper.assertValueEqual(scrapAround(helper, rel), 0, "scrap from an environment kill (rule is player-kill only)");
					helper.killAllEntitiesOfClass(ItemEntity.class);

					MobDropsAdmin.Result reset = MobDropsAdmin.handle(admin, OP, MobDropsActionPayload.of(Action.RESET, PIG.toString()));
					helper.assertTrue(reset.ok(), "reset failed: " + reset.message());
					helper.assertTrue(!Files.exists(file), "reset left the override active");
					helper.assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".disabled")), "reset did not keep the file");
					resetReload.set(reset.reload());
				})
				.thenWaitUntil(() -> helper.assertTrue(resetReload.get().isDone(), "waiting for the reset reload"))
				.thenExecute(() -> {
					helper.assertTrue(MobDrops.rules().get(PIG) == null, "pig rule still active after reset");
					helper.assertTrue(!lootJson(server, PIG).contains(SCRAP.toString()), "pig loot still has the override after reset");
					helper.assertTrue(!MobDropsAdmin.handle(admin, OP, MobDropsActionPayload.of(Action.RESET, PIG.toString())).ok(),
							"a second reset found something to reset");
				})
				.thenSucceed();
	}
}
