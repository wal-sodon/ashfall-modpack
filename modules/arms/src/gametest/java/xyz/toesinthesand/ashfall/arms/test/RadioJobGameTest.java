package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import xyz.toesinthesand.ashfall.arms.radio.RadioBlocks;
import xyz.toesinthesand.ashfall.arms.radio.RadioJob;
import xyz.toesinthesand.ashfall.arms.radio.RadioJobState;
import xyz.toesinthesand.ashfall.arms.radio.RadioJobs;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorBalance;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorMode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Radio jobs on the flat test floor, each aimed at a fixed spot (the building search needs a Lost Cities world and is
 * left to play): a stash is a chest that finishes the job when opened, a nest pays out once its zombies are dead, a
 * distress beacon turns into a chest and a note after the hold (and from day 7 the caller follows whoever held it), and a
 * missed deadline takes back what was placed. A nest counts only real deaths (a zombie that drowns is still owed, as is
 * one removed without dying); a lost job's beacon goes once its chunk loads again; a lost job's zombies go with it, and
 * a job zombie that loads with no job to belong to is removed.
 */
public class RadioJobGameTest {
	private static RadioJob job(GameTestHelper helper, RadioJob.Type type, BlockPos rel, long ticksLeft) {
		ServerLevel level = helper.getLevel();
		return RadioJob.offer(type, 0L, "home", "montford", 1, 0, 0)
				.accepted(level.getGameTime() + ticksLeft).at(helper.absolutePos(rel));
	}

	private static RadioJob active(RadioJobState state) {
		return state.active().orElseThrow(() -> new AssertionError("a job should be under way"));
	}

	/** Opens a chest the way a player does: rolls its loot table into it. */
	private static void open(ServerLevel level, BlockPos pos) {
		((ChestBlockEntity) level.getBlockEntity(pos)).unpackLootTable(null);
	}

	@GameTest
	public void stashIsAChestThatEndsTheJobWhenOpened(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		try {
			RadioJob job = job(helper, RadioJob.Type.STASH, new BlockPos(2, 1, 2), 1000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			BlockPos chest = active(state).spot().orElseThrow();
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the stash is a chest");
			ChestBlockEntity be = (ChestBlockEntity) level.getBlockEntity(chest);
			helper.assertTrue(be.getLootTable() != null && be.getLootTable().identifier().getPath().equals("radio_job/stash"),
					"the stash carries the tier-1 stash table, got " + be.getLootTable());
			RadioJobs.advance(level, state, active(state));
			helper.assertTrue(state.active().isPresent(), "an unopened stash is still under way");
			open(level, chest);
			RadioJobs.advance(level, state, active(state));
			helper.assertTrue(state.active().isEmpty(), "opening the stash finishes the job");
		} finally {
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}

	@GameTest
	public void missedDeadlineTakesTheStashBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		try {
			RadioJob job = job(helper, RadioJob.Type.STASH, new BlockPos(2, 1, 2), 1000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			RadioJob placed = active(state);
			BlockPos chest = placed.spot().orElseThrow();
			RadioJob late = new RadioJob(placed.type(), placed.building(), placed.profile(), placed.district(), placed.tier(),
					placed.x(), placed.z(), level.getGameTime() - 1, placed.stage(), placed.spot(), placed.zombies(), placed.held());
			RadioJobs.advance(level, state, late);
			helper.assertTrue(level.getBlockState(chest).isAir(), "an unopened stash is gone after the deadline");
			helper.assertTrue(state.active().isEmpty(), "the job is lost");
		} finally {
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}

	@GameTest
	public void nestPaysOutOnceItsZombiesAreDead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		try {
			RadioJob job = job(helper, RadioJob.Type.NEST, new BlockPos(2, 1, 2), 1000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			RadioJob placed = active(state);
			List<UUID> zombies = placed.zombies();
			helper.assertTrue(zombies.size() == RadioJobs.NEST_BASE + RadioJobs.NEST_PER_TIER,
					"a tier-1 nest spawns " + (RadioJobs.NEST_BASE + RadioJobs.NEST_PER_TIER) + " zombies, got " + zombies.size());
			for (UUID id : zombies) {
				Entity zombie = level.getEntity(id);
				helper.assertTrue(zombie != null && zombie.entityTags().contains(RadioJobs.ZOMBIE_TAG), "nest zombies carry the job tag");
			}
			RadioJobs.advance(level, state, placed);
			helper.assertTrue(active(state).stage() == RadioJob.PLACED, "no reward while the nest is alive");
			for (UUID id : zombies) {
				level.getEntity(id).kill(level);
			}
			RadioJobs.advance(level, state, active(state));
			RadioJob rewarded = active(state);
			helper.assertTrue(rewarded.stage() == RadioJob.REWARD, "a cleared nest puts out its reward");
			BlockPos chest = rewarded.spot().orElseThrow();
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the reward is a chest");
			open(level, chest);
			RadioJobs.advance(level, state, active(state));
			helper.assertTrue(state.active().isEmpty(), "opening the reward finishes the job");
		} finally {
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}

	@GameTest
	public void nestCountsOnlyRealDeaths(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		Mob drowned = null;
		try {
			RadioJob job = job(helper, RadioJob.Type.NEST, new BlockPos(2, 1, 2), 1000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			List<UUID> zombies = active(state).zombies();
			helper.assertTrue(zombies.size() >= 3, "a tier-1 nest has at least 3 zombies, got " + zombies.size());
			Mob first = (Mob) level.getEntity(zombies.get(0));
			drowned = first.convertTo(EntityTypes.DROWNED, ConversionParams.single(first, true, true), d -> {
			});
			helper.assertTrue(drowned != null, "the nest zombie did not drown");
			helper.assertTrue(drowned.entityTags().contains(RadioJobs.ZOMBIE_TAG), "the drowned lost the job tag");
			UUID gone = zombies.get(1);
			level.getEntity(gone).discard(); // removed without dying (unloaded, despawned): still owed
			for (UUID id : zombies.subList(2, zombies.size())) {
				level.getEntity(id).kill(level);
			}
			RadioJobs.advance(level, state, active(state));
			RadioJob still = active(state);
			helper.assertTrue(still.stage() == RadioJob.PLACED, "a nest with a drowned and a vanished zombie paid out");
			helper.assertValueEqual(still.zombies().size(), 2, "zombies still owed");
			helper.assertTrue(still.zombies().contains(drowned.getUUID()), "the drowned did not take the zombie's place");
			helper.assertTrue(still.zombies().contains(gone), "a zombie removed without dying was crossed off");
			drowned.kill(level);
			helper.assertValueEqual(active(state).zombies(), List.of(gone), "zombies owed once the drowned died");
		} finally {
			if (drowned != null) {
				drowned.discard();
			}
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}

	@GameTest
	public void lostJobBeaconIsClearedOnNextLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		BlockPos beacon = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos far = beacon.offset(200_000, 0, 0); // never loaded in the test world
		try {
			helper.setBlock(new BlockPos(2, 1, 2), RadioBlocks.DISTRESS_BEACON);
			// as a job lost while its beacon's chunk was unloaded leaves it
			state.addCleanup(beacon);
			state.addCleanup(far);
			RadioJobs.clearLostJobs(level, state);
			helper.assertTrue(level.getBlockState(beacon).isAir(), "a lost job's beacon is still there once loaded");
			helper.assertTrue(!state.cleanups().contains(beacon), "a cleared spot is still listed");
			helper.assertTrue(state.cleanups().contains(far), "a spot still unloaded was dropped from the list");
		} finally {
			state.removeCleanup(beacon);
			state.removeCleanup(far);
		}
		helper.succeed();
	}

	@GameTest
	public void lostJobZombiesAreRemoved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		Zombie stray = null;
		try {
			RadioJob job = job(helper, RadioJob.Type.NEST, new BlockPos(2, 1, 2), 1000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			RadioJob placed = active(state);
			RadioJob late = new RadioJob(placed.type(), placed.building(), placed.profile(), placed.district(), placed.tier(),
					placed.x(), placed.z(), level.getGameTime() - 1, placed.stage(), placed.spot(), placed.zombies(), placed.held());
			RadioJobs.advance(level, state, late);
			helper.assertTrue(state.active().isEmpty(), "the job is lost");
			for (UUID id : placed.zombies()) {
				Entity zombie = level.getEntity(id);
				helper.assertTrue(zombie == null || zombie.isRemoved(), "a lost nest's zombie is still about");
			}

			// a job zombie that loads with no job to belong to goes on the next tick
			stray = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			helper.assertTrue(stray != null, "stray zombie created");
			stray.addTag(RadioJobs.ZOMBIE_TAG);
			BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
			stray.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.addFreshEntity(stray);
			helper.assertTrue(!stray.isRemoved(), "the stray was removed inside the load callback");
			RadioJobs.removeStrays(level);
			helper.assertTrue(stray.isRemoved(), "a job zombie with no job is still about");
		} finally {
			if (stray != null) {
				stray.discard();
			}
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void distressBeaconBecomesAChestAndANoteAfterTheHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		try {
			RadioJob job = job(helper, RadioJob.Type.DISTRESS, new BlockPos(2, 1, 2), 100_000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			BlockPos beacon = active(state).spot().orElseThrow();
			helper.assertTrue(level.getBlockState(beacon).is(RadioBlocks.DISTRESS_BEACON), "a distress call puts out a beacon");
			for (int i = 0; i < RadioJobs.DISTRESS_HOLD_TICKS / 20 - 1; i++) {
				RadioJobs.hold(level, state, active(state));
			}
			helper.assertTrue(active(state).stage() == RadioJob.PLACED && !active(state).zombies().isEmpty(),
					"one update short of the hold: still the beacon, and the wave came");
			RadioJobs.hold(level, state, active(state));
			helper.assertTrue(active(state).stage() == RadioJob.REWARD, "the hold is done");
			helper.assertTrue(level.getBlockState(beacon).is(Blocks.CHEST), "the beacon became the reward chest");
			List<ItemEntity> notes = level.getEntitiesOfClass(ItemEntity.class, new AABB(beacon).inflate(3),
					e -> e.getItem().is(Items.WRITTEN_BOOK));
			helper.assertTrue(!notes.isEmpty(), "the caller left a note");
		} finally {
			state.setActive(Optional.empty());
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void distressRescueFollowsTheRescuer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RadioJobState state = RadioJobState.get(level.getServer());
		ServerPlayer rescuer = helper.makeMockServerPlayerInLevel();
		List<Survivor> found = List.of();
		try {
			for (int x = 0; x < 6; x++) {
				for (int z = 0; z < 6; z++) {
					helper.setBlock(x, 0, z, Blocks.STONE);
				}
			}
			RadioJob job = job(helper, RadioJob.Type.DISTRESS, new BlockPos(2, 1, 2), 100_000);
			RadioJobs.start(level, job);
			RadioJobs.place(level, state, job);
			BlockPos beacon = active(state).spot().orElseThrow();
			rescuer.snapTo(beacon.getX() + 2.5, beacon.getY(), beacon.getZ() + 0.5);
			for (int i = 0; i < RadioJobs.DISTRESS_HOLD_TICKS / 20; i++) {
				RadioJobs.hold(level, state, active(state), SurvivorBalance.get().rescueFirstDay());
			}
			helper.assertTrue(active(state).stage() == RadioJob.REWARD, "the hold is done");
			found = level.getEntitiesOfClass(Survivor.class, new AABB(beacon).inflate(3), e -> true);
			helper.assertTrue(found.size() == 1, "expected one rescued survivor by the beacon, found " + found.size());
			Survivor survivor = found.get(0);
			helper.assertValueEqual(survivor.leader(), rescuer.getUUID(), "the rescuer leads the survivor");
			helper.assertValueEqual(survivor.mode(), SurvivorMode.FOLLOW, "a rescued survivor follows");
			helper.assertTrue(survivor.isPersistenceRequired(), "a rescued survivor must never despawn");
		} finally {
			state.setActive(Optional.empty());
			found.forEach(Entity::discard);
			level.getServer().getPlayerList().remove(rescuer);
		}
		helper.succeed();
	}
}
