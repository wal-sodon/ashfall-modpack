package xyz.toesinthesand.ashfall.director.compat.zbb;

import com.tik.zbb.ai.AiTimers;
import com.tik.zbb.ai.action.ActionExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import xyz.toesinthesand.ashfall.director.horde.CoreBreakIn;

/**
 * A {@link CoreBreakIn.Breaker} on Zombies Break &amp; Build's own break action. Each zombie gets its own executor
 * and timers, the same pair ZB&amp;B's goal builds, so hits follow ZB&amp;B's cooldown, block health, cracks and block
 * restoration, and the director's {@code BreakActionMixin} still turns hits on fortified blocks into wear and keeps
 * safe zones whole.
 * <p>
 * Only loaded when ZB&amp;B is installed ({@link CoreBreakIn#breakerFor} checks first), so its classes always exist here.
 */
public final class ZbbCoreBreaker implements CoreBreakIn.Breaker {
	private final PathfinderMob mob;
	private final ActionExecutor executor;

	private ZbbCoreBreaker(PathfinderMob mob) {
		this.mob = mob;
		this.executor = new ActionExecutor(mob, new AiTimers());
	}

	public static CoreBreakIn.Breaker create(PathfinderMob mob) {
		return new ZbbCoreBreaker(mob);
	}

	@Override
	public boolean hit(BlockPos pos) {
		executor.tick(); // refreshes the executor's level and config snapshot
		mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		return executor.tryExecuteBreakAction(pos);
	}
}
