package xyz.toesinthesand.ashfall.director.client;

import net.fabricmc.api.ClientModInitializer;
import xyz.toesinthesand.ashfall.director.client.hydration.ThirstHud;
import xyz.toesinthesand.ashfall.director.client.infection.InfectionHud;
import xyz.toesinthesand.ashfall.director.client.special.SpecialInfectedClient;

public class AshfallDirectorClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ThirstHud.init();
		InfectionHud.init();
		SpecialInfectedClient.init();
	}
}
