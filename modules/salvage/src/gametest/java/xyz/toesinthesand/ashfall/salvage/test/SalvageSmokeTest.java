package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

public class SalvageSmokeTest {
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.assertTrue(FabricLoader.getInstance().isModLoaded("ashfall_salvage"), "ashfall_salvage is not loaded");
		helper.succeed();
	}
}
