package xyz.toesinthesand.ashfall.salvage.client;

import net.fabricmc.api.ClientModInitializer;
import xyz.toesinthesand.ashfall.salvage.client.loot.LootZoneClient;

public class AshfallSalvageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LootZoneClient.init();
		SatchelScreen.register();
		BicycleRenderer.register();
	}
}
