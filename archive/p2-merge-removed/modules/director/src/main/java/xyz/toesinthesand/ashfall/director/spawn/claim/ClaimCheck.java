package xyz.toesinthesand.ashfall.director.spawn.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * "Is this block inside a protected claim?" The Director asks this before every spawn so that no
 * zombie appears inside a player claim or a safe-zone server claim. It is an interface so the
 * Director runs without OPAC (no claims at all) and so gametests can plug in a fake.
 */
@FunctionalInterface
public interface ClaimCheck {
	ClaimCheck NONE = (level, pos) -> false;

	boolean isClaimed(ServerLevel level, BlockPos pos);
}
