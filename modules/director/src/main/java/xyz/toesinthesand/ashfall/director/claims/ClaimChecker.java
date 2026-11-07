package xyz.toesinthesand.ashfall.director.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Answers "is this spot inside a land claim?". Gametests pass one to {@link Claims#setCheckerForTesting} to
 * stand in a fake claim without OPAC installed.
 */
@FunctionalInterface
public interface ClaimChecker {
	/** Nothing is ever claimed. */
	ClaimChecker NONE = (level, pos) -> false;

	boolean isClaimed(ServerLevel level, BlockPos pos);
}
