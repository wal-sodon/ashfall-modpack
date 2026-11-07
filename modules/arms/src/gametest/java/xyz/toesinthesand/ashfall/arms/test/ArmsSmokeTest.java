package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

public class ArmsSmokeTest {
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded("ashfall_arms"), "ashfall_arms is not loaded");
		helper.succeed();
	}
}
