package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.gun.ArmsNoise;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;

import java.util.List;

/** Arms noise (gunshots, the relay hum) must reach the Director as SOUND stimuli zombies can hear. */
public class ArmsNoiseStimuliTest {
	@GameTest
	public void armsNoiseForwardsToStimuli(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		ArmsNoise.emit(helper.getLevel(), pos, 6f);
		List<Stimulus> heard = Stimuli.query(helper.getLevel(), Vec3.atCenterOf(pos).add(4, 0, 0), 1.0);
		helper.assertTrue(heard.stream().anyMatch(s -> s.type() == StimulusType.SOUND && s.radius() == 6.0),
				"ArmsNoise.emit should leave a radius-6 SOUND stimulus, got " + heard);
		helper.succeed();
	}
}
