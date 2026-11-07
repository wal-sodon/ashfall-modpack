package xyz.toesinthesand.ashfall.arms.gun;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.network.ArmsNetworking;

/**
 * Common setup for energy weapons: components, sounds, particle types, the bolt entity, the Dampener and flamethrower recipe types, the flamethrower's burn rule, the
 * gun and attachment stats datapack loaders, networking and draw time. Items themselves are registered by
 * {@link xyz.toesinthesand.ashfall.arms.item.ArmsItems}.
 */
public final class ArmsGuns {
	private ArmsGuns() {
	}

	public static void init() {
		ArmsComponents.init();
		ArmsSounds.init();
		ArmsParticles.init();
		ArmsEntities.init();
		DampenerRecipe.init();
		FlamethrowerRecipe.init();
		FlamerBurn.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(GunStatsManager.ID, new GunStatsManager());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(AttachmentStatsManager.ID, new AttachmentStatsManager());
		ArmsNetworking.init();
		DrawTime.init();
	}
}
