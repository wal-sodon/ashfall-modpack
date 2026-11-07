package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.loot.LootRefill;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.loot.LootZone;
import xyz.toesinthesand.ashfall.salvage.loot.RefillRule;
import xyz.toesinthesand.ashfall.salvage.loot.ZoneManager;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;

import java.util.Optional;

/**
 * End-to-end zone lifecycle on a real chest: claim by a zone, first open, then a due refill that
 * wipes leftovers and re-arms the table with the next deterministic seed.
 */
public class LootZoneGameTest {
	private static final Identifier TABLE = Identifier.withDefaultNamespace("chests/simple_dungeon");

	@GameTest
	public void zoneClaimsAndRefillsChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, Blocks.CHEST);
		BlockPos pos = helper.absolutePos(rel);
		ChestBlockEntity chest = helper.getBlockEntity(rel, ChestBlockEntity.class);

		String id = "gametest_" + pos.asLong();
		BuildingProfile.Refill rules = new BuildingProfile.Refill(true, 1_000, 8, 200);
		LootZone zone = new LootZone(id, level.dimension(), pos, 1, Optional.of(Identifier.withDefaultNamespace("chest")),
				TABLE, rules, LootZone.SHARED);
		ZoneManager manager = ZoneManager.get(level.getServer());
		manager.putZone(zone);
		try {
			// 1. create: the zone claims the chest and arms it with its table.
			int claimed = LootRefill.apply(level, zone, zone.radius());
			helper.assertValueEqual(claimed, 1, "containers claimed");
			helper.assertValueEqual(chest.getLootTable(), LootRefill.tableKey(TABLE), "loot table after create");
			LootState state = chest.getAttached(AshfallSalvage.LOOT_STATE);
			helper.assertTrue(state != null, "LootState attached");
			helper.assertValueEqual(state.zone(), Optional.of(id), "zone on LootState");
			helper.assertValueEqual(state.table(), TABLE, "table on LootState");
			helper.assertValueEqual(state.lastOpened(), -1L, "never opened");

			// 2. first open: stamped, nothing refilled; vanilla then unpacks the table.
			long now = 1_000_000; // explicit clock: a fresh test world is too young to have a past
			helper.assertTrue(LootRefill.handleOpen(level, pos, false, now), "chest is tracked");
			state = chest.getAttached(AshfallSalvage.LOOT_STATE);
			helper.assertValueEqual(state.lastOpened(), now, "lastOpened after first open");
			helper.assertValueEqual(state.refills(), 0, "no refill on first open");
			chest.unpackLootTable(null);
			helper.assertTrue(chest.getLootTable() == null, "vanilla unpack consumed the table");
			chest.setItem(chest.getContainerSize() - 1, new ItemStack(Items.NETHER_STAR));

			// 3. later: delay elapsed and a quiet period ended after the open -> refill.
			chest.setAttached(AshfallSalvage.LOOT_STATE, state.opened(now - 2_000));
			manager.setPresence(zone.areaKey(), new ZoneManager.Presence(now - 10, now - 10));
			helper.assertTrue(LootRefill.handleOpen(level, pos, false, now), "chest is tracked");
			state = chest.getAttached(AshfallSalvage.LOOT_STATE);
			helper.assertValueEqual(state.refills(), 1, "refilled once");
			helper.assertValueEqual(state.lastOpened(), now, "open stamped after refill");
			helper.assertValueEqual(chest.getLootTable(), LootRefill.tableKey(TABLE), "re-tabled");
			helper.assertValueEqual(chest.getLootTableSeed(), RefillRule.seed(pos.asLong(), 1), "deterministic reroll seed");
			chest.unpackLootTable(null);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				helper.assertTrue(!chest.getItem(i).is(Items.NETHER_STAR), "leftovers cleared before reroll");
			}

			// 4. opening again right away does not refill (too early, no new quiet gap).
			LootRefill.handleOpen(level, pos, false, now);
			helper.assertValueEqual(chest.getAttached(AshfallSalvage.LOOT_STATE).refills(), 1, "no second refill");
		} finally {
			manager.removeZone(id);
		}
		helper.succeed();
	}

	/**
	 * Drives the real {@code UseBlockCallback} handler with a mock player on a double chest:
	 * "any" claims both chest halves but not a hopper, clicking the untracked half still stamps the
	 * tracked half, and a later click (delay 0, quiet 0) refills it.
	 */
	@GameTest(maxTicks = 40)
	public void callbackRefillsDoubleChestAndAnySkipsHopper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos leftRel = new BlockPos(1, 1, 1);
		helper.setBlock(leftRel, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		BlockPos left = helper.absolutePos(leftRel);
		BlockPos right = ChestBlock.getConnectedBlockPos(left, level.getBlockState(left));
		level.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
		helper.setBlock(new BlockPos(1, 1, 3), Blocks.HOPPER);
		ChestBlockEntity leftChest = (ChestBlockEntity) level.getBlockEntity(left);
		ChestBlockEntity rightChest = (ChestBlockEntity) level.getBlockEntity(right);
		HopperBlockEntity hopper = helper.getBlockEntity(new BlockPos(1, 1, 3), HopperBlockEntity.class);

		String id = "gametest_cb_" + left.asLong();
		LootZone zone = new LootZone(id, level.dimension(), left, 3, Optional.empty(), TABLE,
				new BuildingProfile.Refill(true, 0, 8, 0), LootZone.SHARED);
		ZoneManager manager = ZoneManager.get(level.getServer());
		manager.putZone(zone);
		helper.assertValueEqual(LootRefill.apply(level, zone, zone.radius()), 2, "both chest halves claimed");
		helper.assertTrue(hopper.getAttached(AshfallSalvage.LOOT_STATE) == null && hopper.getLootTable() == null, "'any' leaves hoppers alone");

		leftChest.removeAttached(AshfallSalvage.LOOT_STATE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(left), Direction.UP, left, false);
		long t0 = level.getServer().overworld().getGameTime();
		LootRefill.onUseBlock(player, level, InteractionHand.MAIN_HAND, hit);
		helper.assertValueEqual(rightChest.getAttached(AshfallSalvage.LOOT_STATE).lastOpened(), t0, "other half stamped");
		rightChest.unpackLootTable(null);
		rightChest.setItem(0, new ItemStack(Items.NETHER_STAR));

		helper.runAfterDelay(5, () -> {
			try {
				LootRefill.onUseBlock(player, level, InteractionHand.MAIN_HAND, hit);
				LootState s = rightChest.getAttached(AshfallSalvage.LOOT_STATE);
				helper.assertValueEqual(s.refills(), 1, "tracked half refilled through the callback");
				helper.assertValueEqual(rightChest.getLootTable(), LootRefill.tableKey(TABLE), "re-tabled");
				rightChest.unpackLootTable(null);
				for (int i = 0; i < rightChest.getContainerSize(); i++) {
					helper.assertTrue(!rightChest.getItem(i).is(Items.NETHER_STAR), "leftovers wiped");
				}
				helper.assertTrue(leftChest.getAttached(AshfallSalvage.LOOT_STATE) == null, "untracked half stays untracked");
			} finally {
				manager.removeZone(id);
			}
			helper.succeed();
		});
	}
}
