package xyz.toesinthesand.ashfall.arms.grid;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * No sleeping while a District Siege is set for tonight (ENERGIZED) or running (SIEGE): the siege starts at
 * nightfall, and a bed would skip it, along with the wait for dawn that also wins it.
 */
final class SiegeSleep {
	private SiegeSleep() {
	}

	static void init() {
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (!(player.level() instanceof ServerLevel level)) {
				return null;
			}
			boolean siege = GridState.get(level.getServer()).snapshot().values().stream()
					.anyMatch(state -> state == DistrictState.ENERGIZED || state == DistrictState.SIEGE);
			return siege ? new Player.BedSleepingProblem(Component.translatable("message.ashfall.no_sleep.siege")) : null;
		});
	}
}
