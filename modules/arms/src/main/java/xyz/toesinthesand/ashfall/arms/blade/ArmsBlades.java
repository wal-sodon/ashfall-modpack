package xyz.toesinthesand.ashfall.arms.blade;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.director.senses.ItemLight;

/**
 * Common setup for Plasma Blades: the stats loader, the crystal-socketing recipe, the sweep's target cap
 * and the blade's light for the Director.
 */
public final class ArmsBlades {
	private ArmsBlades() {
	}

	public static void init() {
		SocketCrystalRecipe.SERIALIZER.getClass(); // forces serializer registration
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(BladeStatsManager.ID, new BladeStatsManager());

		// The sweep cap: a 4th target in one swing takes no damage at all (no knockback, no hurt flash).
		// Cancelled here rather than as 0 damage because vanilla still knocks back on a 0-damage hit.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
				return true;
			}
			ItemStack weapon = source.getWeaponItem();
			if (weapon == null || !(weapon.getItem() instanceof PlasmaBladeItem)) {
				return true;
			}
			int cap = BladeStatsManager.get(weapon.getItem()).maxTargets();
			return !BladeLogic.overCap(player.getUUID(), entity.level().getGameTime(), cap);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BladeLogic.forget(handler.getPlayer().getUUID()));

		// Dedicated servers have no LambDynamicLights, so the Director's light sense asks us directly.
		ItemLight.addSource(stack -> stack.getItem() instanceof PlasmaBladeItem && stack.has(ArmsComponents.BLADE_LIT)
				? BladeStatsManager.get(stack.getItem()).light() : 0);
	}
}
