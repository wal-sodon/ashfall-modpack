package xyz.toesinthesand.ashfall.arms.client;

import net.fabricmc.api.ClientModInitializer;

public class AshfallArmsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		xyz.toesinthesand.ashfall.arms.client.gun.ArmsGunsClient.init();
		xyz.toesinthesand.ashfall.arms.client.charger.ChargerScreen.register();
		xyz.toesinthesand.ashfall.arms.client.bench.WeaponsmithBenchScreen.register();
		xyz.toesinthesand.ashfall.arms.client.grid.GridClient.init();
		xyz.toesinthesand.ashfall.arms.client.mission.MissionClient.init();
		xyz.toesinthesand.ashfall.arms.client.finale.ChoirRenderer.register();
		xyz.toesinthesand.ashfall.arms.client.survivor.SurvivorRenderer.register();
		xyz.toesinthesand.ashfall.arms.client.drops.MobDropsClient.init();
	}
}
