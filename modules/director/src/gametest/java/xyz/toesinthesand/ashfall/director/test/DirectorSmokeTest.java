package xyz.toesinthesand.ashfall.director.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

public class DirectorSmokeTest {
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded("ashfall_director"), "ashfall_director is not loaded");
		helper.succeed();
	}
}
