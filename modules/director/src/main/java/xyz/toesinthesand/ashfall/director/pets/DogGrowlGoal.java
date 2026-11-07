package xyz.toesinthesand.ashfall.director.pets;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;

/**
 * The dog's warning. It has no flags, so it runs beside sitting, following and fighting (a dog told to sit by the
 * door still keeps watch). Every {@link #SCAN_INTERVAL} ticks it looks around ({@link Dogs#growl}); after a growl
 * it waits {@link Dogs#GROWL_COOLDOWN_TICKS} before the next. The look happens in {@link #canUse} and the goal never
 * starts: there is nothing to keep running, and the selector asks {@code canUse} of an idle goal every other tick.
 */
class DogGrowlGoal extends Goal {
	static final int SCAN_INTERVAL = 20;

	private final Wolf dog;
	private long nextScan;

	DogGrowlGoal(Wolf dog) {
		this.dog = dog;
	}

	@Override
	public boolean canUse() {
		if (!dog.isTame() || !(dog.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (now < nextScan) {
			return false;
		}
		nextScan = now + (Dogs.growl(level, dog) != null ? Dogs.GROWL_COOLDOWN_TICKS : SCAN_INTERVAL);
		return false;
	}
}
