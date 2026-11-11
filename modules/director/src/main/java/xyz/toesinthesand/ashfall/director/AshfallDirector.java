package xyz.toesinthesand.ashfall.director;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.toesinthesand.ashfall.director.haven.Havens;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.item.DirectorItems;
import xyz.toesinthesand.ashfall.director.senses.Senses;

public class AshfallDirector implements ModInitializer {
	public static final String MOD_ID = "ashfall_director";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DirectorItems.init();
		Hydration.init();
		Infection.init();
		Senses.init();
		xyz.toesinthesand.ashfall.director.spawn.SpawnDirector.init();
		xyz.toesinthesand.ashfall.director.special.SpecialInfected.init();
		Havens.init();
		xyz.toesinthesand.ashfall.director.skills.Skills.init();
		xyz.toesinthesand.ashfall.director.horde.HordeSystem.init();
		xyz.toesinthesand.ashfall.director.boss.BossLairs.init();
		xyz.toesinthesand.ashfall.director.graffiti.Graffiti.init();
		xyz.toesinthesand.ashfall.director.pets.StrayDogs.init();
		xyz.toesinthesand.ashfall.director.pets.PetGuard.init();
		LOGGER.info("Ashfall Director loaded");
	}
}
