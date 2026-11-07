package xyz.toesinthesand.ashfall.arms.gun;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.mixin.CooldownInstanceAccessor;
import xyz.toesinthesand.ashfall.arms.mixin.ItemCooldownsAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Draw and holster time. Bringing a blaster (or the flamethrower) into the main hand puts it on an item cooldown of
 * its {@code draw_ticks} ({@link GunStats#drawTicks}); firing and reloading both wait for cooldowns, and the hotbar
 * shows the sweep. Swapping straight from one gun to another adds the old gun's {@link #HOLSTER_TICKS}. Melee
 * weapons and other items stay instant.
 * <p>
 * A draw never shortens a longer cooldown that is already running (a reload, say), so quick-swapping can't skip it.
 * <p>
 * The switch is seen from the gun's own main-hand inventory tick: each player's last ticked gun (selected slot,
 * item, game tick) is kept here. A gun ticked on the next tick in the same slot is still being held; anything else
 * is a fresh draw, and it came straight from another gun when that gun was held on the tick before.
 */
public final class DrawTime {
	/** Extra ticks for putting the previous gun away when swapping gun to gun. */
	public static final int HOLSTER_TICKS = 4;

	private record Held(int slot, Item item, long tick) {
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private DrawTime() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HELD.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HELD.clear());
	}

	/** Called every server tick while {@code stack} is in {@code player}'s main hand. */
	public static void onMainHandTick(ServerPlayer player, ItemStack stack, int drawTicks) {
		onMainHandTick(player, player.getInventory().getSelectedSlot(), stack, drawTicks, player.level().getGameTime());
	}

	/**
	 * The detection itself, with the slot and tick passed in so gametests can play a sequence of ticks. Returns the
	 * draw time this tick asked for (0 while the gun is simply still held), whether or not a longer running cooldown
	 * kept it from being applied.
	 */
	public static int onMainHandTick(Player player, int slot, ItemStack stack, int drawTicks, long now) {
		Held last = HELD.put(player.getUUID(), new Held(slot, stack.getItem(), now));
		boolean heldLastTick = last != null && (last.tick() == now - 1 || last.tick() == now);
		if (heldLastTick && last.slot() == slot && last.item() == stack.getItem()) {
			return 0;
		}
		int ticks = drawTicks + (heldLastTick ? HOLSTER_TICKS : 0);
		if (ticks > 0) {
			extendCooldown(player, stack, ticks);
		}
		return ticks;
	}

	/** Puts {@code stack} on a {@code ticks} cooldown unless a cooldown running at least that long is already on it. */
	public static void extendCooldown(Player player, ItemStack stack, int ticks) {
		if (ticks > remainingCooldown(player, stack)) {
			player.getCooldowns().addCooldown(stack, ticks);
		}
	}

	/** Ticks left on {@code stack}'s cooldown group for {@code player}; 0 when it is not cooling down. */
	public static int remainingCooldown(Player player, ItemStack stack) {
		ItemCooldowns cooldowns = player.getCooldowns();
		ItemCooldownsAccessor access = (ItemCooldownsAccessor) cooldowns;
		Object running = access.ashfall$cooldowns().get(cooldowns.getCooldownGroup(stack));
		if (running == null) {
			return 0;
		}
		return Math.max(0, ((CooldownInstanceAccessor) running).ashfall$endTime() - access.ashfall$tickCount());
	}
}
