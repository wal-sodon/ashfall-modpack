package xyz.toesinthesand.ashfall.director.hydration;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sleeping through the night is not free: a skipped night costs water and food, as a night awake would,
 * and nobody can sleep while already low on either. Without a cost a bed is a button that removes every
 * night from the game. The cost is only taken when the night was actually skipped (the clock jumped while
 * the player lay in bed), so lying down and getting up again costs nothing.
 */
public final class SleepCost {
	/** Thirst points lost on waking from a skipped night (3 droplets). */
	public static final int THIRST_COST = 6;
	/** Food points lost on waking from a skipped night (2 shanks). */
	public static final int FOOD_COST = 4;
	/** Below this much thirst or food, a bed refuses you. */
	public static final int MIN_TO_SLEEP = 7;
	/** A clock jump this big while in bed means the night was skipped (lying in bed takes about 100 ticks). */
	static final long SKIP_TICKS = 1000;

	/** Overworld clock time when each player lay down. Lost on restart, which only means no cost that once. */
	private static final Map<UUID, Long> LAY_DOWN = new HashMap<>();

	private SleepCost() {
	}

	static void init() {
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
				player instanceof ServerPlayer serverPlayer ? problem(serverPlayer) : null);
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				LAY_DOWN.put(player.getUUID(), clock(player));
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Long start = LAY_DOWN.remove(player.getUUID());
				if (start != null && clock(player) - start >= SKIP_TICKS) {
					charge(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAY_DOWN.clear());
	}

	private static long clock(ServerPlayer player) {
		return player.level().getServer().overworld().getOverworldClockTime();
	}

	/** Why this player may not sleep, or null. Players whose thirst and hunger do not drain are exempt. */
	public static Player.BedSleepingProblem problem(ServerPlayer player) {
		if (!Hydration.drains(player)) {
			return null;
		}
		if (Hydration.get(player).thirst() < MIN_TO_SLEEP) {
			return new Player.BedSleepingProblem(Component.translatable("message.ashfall.no_sleep.thirsty"));
		}
		if (player.getFoodData().getFoodLevel() < MIN_TO_SLEEP) {
			return new Player.BedSleepingProblem(Component.translatable("message.ashfall.no_sleep.hungry"));
		}
		return null;
	}

	/** The cost of a skipped night. Saturation goes too, so a full buffer can not absorb it. */
	public static void charge(ServerPlayer player) {
		if (!Hydration.drains(player)) {
			return;
		}
		ThirstData thirst = Hydration.get(player);
		int newThirst = Math.max(thirst.thirst() - THIRST_COST, 0);
		Hydration.set(player, new ThirstData(newThirst, Math.min(thirst.saturation(), newThirst), thirst.exhaustion()));
		FoodData food = player.getFoodData();
		food.setFoodLevel(Math.max(food.getFoodLevel() - FOOD_COST, 0));
		food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
		player.sendSystemMessage(Component.translatable("message.ashfall.woke_hungry"), true);
	}
}
