package xyz.toesinthesand.ashfall.arms;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.MobDrops;

public class AshfallArms implements ModInitializer {
	public static final String MOD_ID = "ashfall_arms";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ArmsItems.init();
		xyz.toesinthesand.ashfall.arms.gun.ArmsGuns.init();
		xyz.toesinthesand.ashfall.arms.charger.ArmsBlocks.init();
		xyz.toesinthesand.ashfall.arms.blade.ArmsBlades.init();
		xyz.toesinthesand.ashfall.arms.bench.ArmsBench.init();
		MobDrops.init();
		xyz.toesinthesand.ashfall.arms.drops.MobDropsAdmin.init();
		xyz.toesinthesand.ashfall.arms.grid.Grid.init();
		xyz.toesinthesand.ashfall.arms.story.Story.init();
		xyz.toesinthesand.ashfall.arms.finale.Finale.init();
		xyz.toesinthesand.ashfall.arms.mission.Mission.init();
		xyz.toesinthesand.ashfall.arms.travel.Travel.init();
		xyz.toesinthesand.ashfall.arms.supply.SupplyDrops.init();
		xyz.toesinthesand.ashfall.arms.radio.RadioJobs.init();
		xyz.toesinthesand.ashfall.arms.survivor.Survivors.init();
		LOGGER.info("Ashfall Arms loaded");
	}
}
