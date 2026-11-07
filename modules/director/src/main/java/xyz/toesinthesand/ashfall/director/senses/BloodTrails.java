package xyz.toesinthesand.ashfall.director.senses;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.director.horde.HordeSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blood trails (plan M8): a badly wounded player leaves a drop of BLOOD every couple of seconds as they move, and
 * zombies that smell a drop follow the trail ({@link SenseGoal} prefers fresh drops, so they follow it forwards).
 * Fleeing hurt is then a real risk, and patching up (the Director's medical items) matters before running.
 * <p>
 * Kept cheap on purpose: one health check per player per interval, one small stimulus per drop (sourceless, so a
 * row of drops survives as a trail instead of each new drop replacing the last), no drop while standing still, and
 * the stimulus field merges drops closer than 4 blocks.
 */
public final class BloodTrails {
	private static final Map<UUID, Vec3> LAST_DROP = new HashMap<>();

	private BloodTrails() {
	}

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			HordeSettings.Blood blood = HordeSettings.get().blood();
			if (!blood.enabled() || level.dimension() != Level.OVERWORLD || level.getGameTime() % blood.intervalTicks() != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				tick(level, player, blood);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_DROP.clear());
	}

	/** Leaves a drop for {@code player} if they are wounded and moved. Public for gametests. @return the drop, or null */
	public static @Nullable Stimulus tick(ServerLevel level, ServerPlayer player, HordeSettings.Blood blood) {
		if (!wounded(player.getHealth(), player.getMaxHealth(), blood.healthFraction()) || player.isSpectator() || player.isCreative()
				|| !player.isAlive()) {
			LAST_DROP.remove(player.getUUID());
			return null;
		}
		Vec3 pos = player.position();
		Vec3 last = LAST_DROP.get(player.getUUID());
		if (last != null && last.distanceToSqr(pos) < blood.minStep() * blood.minStep()) {
			return null;
		}
		LAST_DROP.put(player.getUUID(), pos);
		return Stimuli.emit(level, pos.add(0, 0.2, 0), StimulusType.BLOOD, blood.radius(), null);
	}

	/** Wounded enough to bleed: at or below {@code fraction} of max health. */
	public static boolean wounded(float health, float maxHealth, float fraction) {
		return maxHealth > 0 && health > 0 && health <= maxHealth * fraction;
	}
}
