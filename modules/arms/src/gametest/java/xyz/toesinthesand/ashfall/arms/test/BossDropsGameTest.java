package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.director.boss.BossLair;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;

import java.util.List;
import java.util.UUID;

/**
 * The story bosses' guaranteed drops with the real lair data and arms items: every boss drops one Resonance
 * Crystal on any death (environment or player), plus Energy Cells from its tiered loot table. Each test clones the
 * shipped lair under a fresh id so the real lairs' records in the test world are left alone.
 */
public class BossDropsGameTest {
	private static final List<String> LAIRS = List.of("abomination", "patient_zero", "juggernaut");

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void killAndCheck(GameTestHelper helper, boolean byPlayer) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		BlockPos spawn = helper.absolutePos(rel);
		Player player = byPlayer ? helper.makeMockPlayer(GameType.SURVIVAL) : null;
		for (String name : LAIRS) {
			BossLair real = BossLairs.get(Identifier.fromNamespaceAndPath("ashfall", name));
			helper.assertTrue(real != null, "lair ashfall:" + name + " is not loaded");
			Identifier id = Identifier.fromNamespaceAndPath("ashfall_test", "drops_" + name + "_" + UUID.randomUUID().toString().substring(0, 8));
			BossLairs.setForTesting(id, real);
			try {
				helper.killAllEntitiesOfClass(ItemEntity.class);
				Mob boss = BossLairs.spawnBoss(level, id, real, spawn);
				helper.assertTrue(boss != null, name + " did not spawn (fallback variant missing?)");
				if (byPlayer) {
					boss.hurtServer(level, level.damageSources().playerAttack(player), 100_000f);
				} else {
					boss.kill(level);
				}
				helper.assertTrue(!boss.isAlive(), name + " survived");
				List<ItemEntity> items = helper.getEntities(EntityTypes.ITEM, rel, 6);
				int crystals = items.stream().filter(i -> i.getItem().is(ArmsItems.RESONANCE_CRYSTAL)).mapToInt(i -> i.getItem().getCount()).sum();
				int cells = items.stream().filter(i -> i.getItem().is(ArmsItems.ENERGY_CELL)).mapToInt(i -> i.getItem().getCount()).sum();
				helper.assertTrue(crystals == 1, name + " dropped " + crystals + " Resonance Crystals (want exactly 1)");
				helper.assertTrue(cells >= 2, name + " dropped only " + cells + " Energy Cells");
			} finally {
				BossLairs.setForTesting(id, null);
			}
		}
		helper.killAllEntities();
		helper.succeed();
	}

	@GameTest
	public void everyBossDropsACrystalOnAnEnvironmentDeath(GameTestHelper helper) {
		killAndCheck(helper, false);
	}

	@GameTest
	public void everyBossDropsACrystalOnAPlayerKill(GameTestHelper helper) {
		killAndCheck(helper, true);
	}
}
