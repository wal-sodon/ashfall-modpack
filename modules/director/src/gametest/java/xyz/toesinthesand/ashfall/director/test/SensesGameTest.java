package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.claims.Claims;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.director.senses.GameEventNoise;
import xyz.toesinthesand.ashfall.director.senses.SenseGoal;
import xyz.toesinthesand.ashfall.director.senses.SenseGoals;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;

import java.util.List;

/**
 * Senses and fortification on a real server. Stimulus radii stay small and the tests get extra padding
 * so one test's stimuli never reach another test's zombie (gametests share one level).
 */
public class SensesGameTest {
	private static final int PADDING = 24;

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@GameTest(maxTicks = 300, padding = PADDING)
	public void zombieWalksToSound(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5));
		helper.assertTrue(SenseGoals.find(zombie) != null, "the load event gave the zombie a SenseGoal");
		Vec3 target = helper.absoluteVec(new Vec3(6.5, 1, 6.5));
		Stimuli.emit(helper.getLevel(), target, StimulusType.SOUND, 12, null);
		helper.succeedWhen(() -> helper.assertTrue(zombie.distanceToSqr(target.x, zombie.getY(), target.z) < 2.0 * 2.0,
				"zombie at " + zombie.position() + " has not reached the sound at " + target));
	}

	@GameTest(padding = PADDING)
	public void lightBehindWoolIgnoredThroughGlassSeen(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 3.5));
		SenseGoal goal = SenseGoals.attach(zombie, 1.0);
		wall(helper, Blocks.WOOL.white());
		Stimulus light = Stimuli.emit(helper.getLevel(), helper.absoluteVec(new Vec3(6.5, 2.5, 3.5)), StimulusType.LIGHT, 10, null);
		helper.assertTrue(light != null, "light stimulus was dropped");
		helper.assertTrue(goal.pickStimulus() == null, "a zombie must not see light through a wool curtain");
		wall(helper, Blocks.GLASS);
		helper.assertTrue(goal.pickStimulus() == light, "a zombie must see light through glass");
		helper.succeed();
	}

	/**
	 * Hearing scales SOUND only: a dull-eared variant (hearing 0.8) still sees a LIGHT out to its full
	 * radius, but doesn't hear a SOUND of the same radius at the same distance.
	 */
	@GameTest(padding = PADDING)
	public void hearingScalesSoundNotLight(GameTestHelper helper) {
		floor(helper);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 3.5));
		SenseGoal goal = SenseGoals.attach(zombie, 0.8);
		helper.assertTrue(goal.hearing() == 0.8, "attach replaced the default goal with the variant hearing");
		Vec3 eyes = zombie.getEyePosition();
		// 5 blocks away: inside radius 6, outside 6 x 0.8 = 4.8
		Vec3 spot = eyes.add(5.0, 0.0, 0.0);
		Stimulus sound = Stimuli.emit(helper.getLevel(), spot, StimulusType.SOUND, 6, null);
		helper.assertTrue(sound != null, "sound stimulus was dropped");
		helper.assertTrue(goal.pickStimulus() == null, "hearing 0.8 must not hear a radius-6 SOUND 5 blocks away");
		Stimulus light = Stimuli.emit(helper.getLevel(), spot, StimulusType.LIGHT, 6, null);
		helper.assertTrue(goal.pickStimulus() == light, "hearing must not shorten LIGHT reach");
		helper.succeed();
	}

	private static void wall(GameTestHelper helper, Block block) {
		for (int y = 1; y <= 4; y++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(4, y, z, block);
			}
		}
	}

	@GameTest(padding = PADDING)
	public void doorProducesSound(GameTestHelper helper) {
		floor(helper);
		GameEventNoise.Entry entry = GameEventNoise.entry(GameEvent.BLOCK_OPEN);
		helper.assertTrue(entry != null && entry.radius() == 10, "noise table entry for block_open: " + entry);
		BlockPos lower = new BlockPos(3, 1, 3);
		BlockState door = Blocks.OAK_DOOR.defaultBlockState();
		helper.setBlock(lower.above(), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(lower, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(lower);
		((DoorBlock) Blocks.OAK_DOOR).setOpen(null, level, level.getBlockState(abs), abs, true);
		List<Stimulus> heard = Stimuli.query(level, Vec3.atCenterOf(abs), 1.0);
		helper.assertTrue(heard.stream().anyMatch(s -> s.type() == StimulusType.SOUND && s.radius() == 10
				&& s.distanceSqr(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5) < 2.0),
				"opening a door should leave a radius-10 SOUND at it, got " + heard);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PADDING)
	public void stimulusExpires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		Stimulus s = Stimuli.emit(level, pos, StimulusType.SOUND, 3, null);
		helper.assertTrue(s != null && Stimuli.query(level, pos, 1.0).contains(s), "fresh stimulus is queryable");
		helper.runAfterDelay(StimulusType.SOUND.lifetimeTicks() + 1, () -> {
			helper.assertFalse(Stimuli.query(level, pos, 1.0).contains(s), "SOUND must expire after 100 ticks");
			helper.succeed();
		});
	}

	@GameTest
	public void fortifiedPredicate(GameTestHelper helper) {
		helper.assertFalse(Fortified.mayBreak(Blocks.OBSIDIAN.defaultBlockState(), EntityTypes.ZOMBIE), "zombie breaks obsidian");
		helper.assertFalse(Fortified.mayBreak(Blocks.IRON_DOOR.defaultBlockState(), EntityTypes.HUSK), "husk breaks iron door");
		helper.assertFalse(Fortified.mayBreak(DirectorBlocks.BARRICADE.defaultBlockState(), EntityTypes.ZOMBIE), "zombie breaks barricade");
		helper.assertFalse(Fortified.mayBreak(DirectorBlocks.REINFORCED_PLANKS.defaultBlockState(), EntityTypes.ZOMBIE), "zombie breaks reinforced planks");
		helper.assertTrue(Fortified.mayBreak(Blocks.OAK_PLANKS.defaultBlockState(), EntityTypes.ZOMBIE), "zombie may break plain planks");
		// the gametest datapack adds minecraft:giant to #ashfall:bosses (the real bosses are other mods')
		helper.assertTrue(Fortified.mayBreak(Blocks.OBSIDIAN.defaultBlockState(), EntityTypes.GIANT), "a boss may break fortified blocks");
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		try {
			Claims.setCheckerForTesting((level, p) -> true);
			helper.assertFalse(Fortified.mayBuild(helper.getLevel(), pos), "zombies may not build in a claim");
			Claims.setCheckerForTesting((level, p) -> false);
			helper.assertTrue(Fortified.mayBuild(helper.getLevel(), pos), "zombies may build in the wild");
		} finally {
			Claims.setCheckerForTesting(null);
		}
		helper.succeed();
	}
}
