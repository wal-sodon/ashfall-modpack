package xyz.toesinthesand.ashfall.director.senses;

import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.fortify.FortifyWear;

/**
 * Wires up the Director's senses and fortifications (spec sections 2 to 4): the stimulus field, the
 * game-event, player-light and blood-trail sources, the zombie sense goal, the stun effect, the fortification blocks and their wear, and the
 * {@code /ashfall debug stimuli} view. The ZB&B vetoes need no wiring; their mixin config applies itself
 * when ZB&B is present.
 */
public final class Senses {
	private Senses() {
	}

	public static void init() {
		Stimuli.init();
		GameEventNoise.init();
		LightEmitter.init();
		BloodTrails.init();
		SenseGoals.init();
		Stun.init();
		StimuliDebugCommand.init();
		DirectorBlocks.init();
		FortifyWear.init();
	}
}
