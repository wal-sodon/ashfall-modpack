package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.boss.BossLair;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.boss.BossState;
import xyz.toesinthesand.ashfall.director.boss.LairRules;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Boss lairs on a real server: the spawn-once rule, seals, marker scanning and guaranteed drops. Each test uses a
 * fresh lair id, because the gametest run directory keeps saved data (and so lair records) between runs.
 */
public class BossLairGameTest {
	private static Identifier freshId(String what) {
		return Identifier.fromNamespaceAndPath("ashfall_test", what + "_" + UUID.randomUUID().toString().substring(0, 8));
	}

	private static BossLair lair(BossLair.Anchor anchor, boolean sealed, Optional<BossLair.Respawn> respawn, BossLair.Drops drops) {
		return new BossLair("boss.ashfall.juggernaut", anchor, new BossLair.Boss(Optional.of(Identifier.fromNamespaceAndPath("ashfall", "juggernaut")),
				Optional.empty(), Optional.empty()), Map.of(), true, Optional.empty(), BossLair.Bar.DEFAULT, 8, 32, sealed, 6,
				respawn, drops, Optional.empty());
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest
	public void lairSpawnsOnceAndStaysDead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
		Identifier id = freshId("once");
		BossLair lair = lair(BossLair.Anchor.at(spawn), false, Optional.empty(), BossLair.Drops.NONE);
		BossLairs.setForTesting(id, lair);
		try {
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn).add(200, 0, 0)) == null,
					"a far player must not wake the lair");
			Mob boss = BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn).add(3, 0, 0));
			helper.assertTrue(boss != null, "a near player should wake the lair");
			helper.assertTrue(boss.isPersistenceRequired(), "bosses wait in their lair: persistent");
			helper.assertTrue(boss.getMaxHealth() >= 300, "the juggernaut variant applied, max health " + boss.getMaxHealth());
			helper.assertTrue(!boss.hasAttached(DirectorSpawns.TAG), "bosses are not Director zombies and do not count toward caps");
			helper.assertTrue(id.toString().equals(boss.getAttached(BossLairs.BOSS)), "boss carries its lair id");
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn)) == null, "an active lair must not spawn a second boss");

			boss.kill(level);
			BossState.Lair record = BossState.get(level.getServer()).lair(id.toString());
			helper.assertTrue(record.status() == LairRules.Status.DEFEATED, "killed boss marks the lair defeated: " + record.status());
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn)) == null, "a defeated one-time lair stays empty");
		} finally {
			BossLairs.setForTesting(id, null);
		}
		helper.killAllEntities();
		helper.succeed();
	}

	@GameTest
	public void lairRespawnsAfterTheEndingOnlyWithARule(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
		Identifier id = freshId("aftershock");
		BossLair lair = lair(BossLair.Anchor.at(spawn), false, Optional.of(new BossLair.Respawn(1)), BossLair.Drops.NONE);
		BossLairs.setForTesting(id, lair);
		try {
			Mob boss = BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn));
			helper.assertTrue(boss != null, "first wake");
			boss.kill(level);
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn)) == null, "no respawn before the ending");
			long now = level.getGameTime();
			// The story ended long ago, but the boss died just now: still a day to wait.
			BossLairs.setEndingTickSource(server -> OptionalLong.of(now - 10 * LairRules.DAY_TICKS));
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn)) == null, "respawn waits a day after the defeat");
			helper.assertTrue(LairRules.maySpawn(LairRules.Status.DEFEATED, false, now, OptionalLong.of(now - 10 * LairRules.DAY_TICKS),
					lair.respawn(), now + LairRules.DAY_TICKS), "one day after the defeat it may return");
		} finally {
			BossLairs.setEndingTickSource(null);
			BossLairs.setForTesting(id, null);
		}
		helper.killAllEntities();
		helper.succeed();
	}

	@GameTest
	public void sealedLairWaitsForUnsealAndProtectsItsDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos door = helper.absolutePos(new BlockPos(4, 1, 2));
		BlockState lower = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		level.setBlock(door, lower, 2 | 16);
		level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
		Identifier id = freshId("sealed");
		BossLair lair = lair(BossLair.Anchor.at(spawn), true, Optional.empty(), BossLair.Drops.NONE);
		BossLairs.setForTesting(id, lair);
		try {
			helper.assertTrue(BossLairs.sealed(level.getServer(), id, lair), "starts sealed");
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn)) == null, "a sealed lair never wakes");
			int opened = BossLairs.unseal(level, id);
			helper.assertTrue(opened == 1, "unseal should open the seal's door, opened " + opened);
			helper.assertTrue(level.getBlockState(door).getValue(DoorBlock.OPEN), "the iron door is open");
			helper.assertTrue(!BossLairs.sealed(level.getServer(), id, lair), "unsealed");
			helper.assertTrue(BossLairs.tryWake(level, id, lair, Vec3.atCenterOf(spawn)) != null, "after unseal the boss wakes");
		} finally {
			BossLairs.setForTesting(id, null);
		}
		helper.killAllEntities();
		helper.succeed();
	}

	/** Zombies neither break nor wear a still-sealed lair's door; once unsealed it wears like any fortified door. */
	@GameTest
	public void zombiesCannotWearASealedLairsDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos door = helper.absolutePos(new BlockPos(4, 1, 2));
		BlockState lower = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		level.setBlock(door, lower, 2 | 16);
		level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2 | 16);
		Identifier id = freshId("sealed_wear");
		BossLair lair = lair(BossLair.Anchor.at(spawn), true, Optional.empty(), BossLair.Drops.NONE);
		BossLairs.setForTesting(id, lair);
		try {
			BossLairs.resolveSpawn(level, id, lair);
			Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 1, 2.5));
			helper.assertTrue(BossLairs.sealedDoor(level, door, level.getBlockState(door)), "the seal's door counts as sealed");
			helper.assertFalse(Fortified.zombieBreak(level, door, level.getBlockState(door), zombie), "a zombie may not break the seal");
			helper.assertValueEqual(FortifyWear.wear(level, door), 0, "wear on the sealed door");
			BossLairs.unseal(level, id);
			helper.assertFalse(BossLairs.sealedDoor(level, door, level.getBlockState(door)), "unsealed doors are ordinary");
			Fortified.zombieBreak(level, door, level.getBlockState(door), zombie);
			helper.assertValueEqual(FortifyWear.wear(level, door), 1, "wear on the door after unsealing");
		} finally {
			BossLairs.setForTesting(id, null);
		}
		helper.killAllEntities();
		helper.succeed();
	}

	/** A landmark lair finds its boss marker block in the landmark's chunks and stands the boss on top of it. */
	@GameTest
	public void landmarkLairStandsOnItsMarker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos marker = helper.absolutePos(new BlockPos(3, 0, 1));
		level.setBlock(marker, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
		Identifier id = freshId("landmark");
		BossLair.Anchor anchor = new BossLair.Anchor(Optional.of("ashfall_test:landmark"),
				Optional.of(List.of(marker.getX() >> 4, marker.getZ() >> 4)), Optional.of(List.of(1, 1)), Optional.empty(), Optional.empty());
		BossLair lair = lair(anchor, false, Optional.empty(), BossLair.Drops.NONE);
		BossLairs.setForTesting(id, lair);
		try {
			Optional<BlockPos> spawn = BossLairs.resolveSpawn(level, id, lair);
			helper.assertTrue(spawn.isPresent(), "the marker should be found");
			// Another test in the same chunk may have a lower marker; ours must at least resolve to a marker top.
			helper.assertTrue(level.getBlockState(spawn.get().below()).is(Blocks.REINFORCED_DEEPSLATE),
					"spawn point stands on a boss marker: " + spawn.get());
		} finally {
			BossLairs.setForTesting(id, null);
		}
		helper.succeed();
	}

	/** Guaranteed drops fall on any death, not only player kills (director-only: a vanilla item stands in for the crystal). */
	@GameTest
	public void guaranteedDropsFallOnAnyDeath(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
		Identifier id = freshId("drops");
		BossLair lair = lair(BossLair.Anchor.at(spawn), false, Optional.empty(),
				new BossLair.Drops(List.of(new BossLair.ItemCount(Identifier.withDefaultNamespace("diamond"), 1)), Optional.empty()));
		BossLairs.setForTesting(id, lair);
		try {
			Mob boss = BossLairs.spawnBoss(level, id, lair, spawn);
			helper.assertTrue(boss != null, "spawned");
			helper.assertTrue(lair.bossbar().color() == BossEvent.BossBarColor.RED, "default bar");
			boss.kill(level);
			List<ItemEntity> items = helper.getEntities(EntityTypes.ITEM, new BlockPos(2, 1, 2), 4);
			helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.DIAMOND)), "the guaranteed drop is missing: " + items);
		} finally {
			BossLairs.setForTesting(id, null);
		}
		helper.killAllEntities();
		helper.succeed();
	}

	/**
	 * Patient Zero's summon with the shipped numbers: each call brings {@code count} minions carrying the lair's summon
	 * tag, and calls stop adding once {@code max_alive} are up. Only the radius is shrunk to fit the test floor. The
	 * Phase 2 playtest could not reach this headless (the summon waits for a target), so it is checked here.
	 */
	@GameTest
	public void summonCallsMinionsUpToTheCap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BossLair shipped = BossLairs.get(Identifier.fromNamespaceAndPath("ashfall", "patient_zero"));
		helper.assertTrue(shipped != null && shipped.summon().isPresent(), "patient_zero lair with a summon should be loaded");
		BossLair.Summon s = shipped.summon().get();
		BossLair.Summon summon = new BossLair.Summon(s.variant(), s.everyTicks(), s.count(), s.maxAlive(), 2);
		BlockPos spawn = helper.absolutePos(new BlockPos(2, 1, 2));
		Identifier id = freshId("summon");
		BossLair lair = lair(BossLair.Anchor.at(spawn), false, Optional.empty(), BossLair.Drops.NONE);
		BossLairs.setForTesting(id, lair);
		try {
			Mob boss = BossLairs.spawnBoss(level, id, lair, spawn);
			helper.assertTrue(boss != null, "boss should spawn");
			int total = 0;
			for (int call = 0; call < 10 && total < summon.maxAlive(); call++) {
				int spawned = BossLairs.summon(level, id, boss, summon);
				helper.assertTrue(spawned == Math.min(summon.count(), summon.maxAlive() - total),
						"call " + call + " summoned " + spawned + " with " + total + " already up");
				total += spawned;
			}
			helper.assertTrue(total == summon.maxAlive(), "summoned " + total + ", cap " + summon.maxAlive());
			helper.assertTrue(BossLairs.summon(level, id, boss, summon) == 0, "no minions past the cap");
			String tag = BossLairs.SUMMON_TAG_PREFIX + id;
			int tagged = level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(16), m -> m.entityTags().contains(tag)).size();
			helper.assertTrue(tagged == summon.maxAlive(), "minions carrying the summon tag: " + tagged);
		} finally {
			BossLairs.setForTesting(id, null);
		}
		helper.killAllEntities();
		helper.succeed();
	}
}
