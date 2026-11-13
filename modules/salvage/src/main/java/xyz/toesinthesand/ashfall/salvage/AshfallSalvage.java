package xyz.toesinthesand.ashfall.salvage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.CityCommands;
import xyz.toesinthesand.ashfall.salvage.city.CitySpawn;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.city.ZoneLoader;
import xyz.toesinthesand.ashfall.salvage.city.lc.LostCitiesBridge;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.nio.file.Files;

public class AshfallSalvage implements ModInitializer {
	public static final String MOD_ID = "ashfall_salvage";
	public static final String NAMESPACE = "ashfall";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Refill bookkeeping on loot container block entities. */
	public static final AttachmentType<LootState> LOOT_STATE = AttachmentRegistry.createPersistent(id("loot_state"), LootState.CODEC);

	private static volatile BuildingSource buildings = BuildingSource.NONE;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	/** Where the retired Arnis map wrote its building sidecar; only checked to warn about old worlds. */
	private static final String ARNIS_SIDECAR = "ashfall/buildings.json";

	/**
	 * Buildings of the running overworld: Lost Cities' plans in a Lost Cities world, {@link BuildingSource#NONE}
	 * otherwise (and before the server has started).
	 */
	public static BuildingSource buildings() {
		return buildings;
	}

	@Override
	public void onInitialize() {
		SalvageItems.init();
		xyz.toesinthesand.ashfall.salvage.bike.Bikes.init();
		xyz.toesinthesand.ashfall.salvage.lock.Locks.init();
		xyz.toesinthesand.ashfall.salvage.torch.TorchBurnout.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(BuildingProfiles.ID, new BuildingProfiles());
		xyz.toesinthesand.ashfall.salvage.loot.LootZones.register();

		// Lost Cities only knows a level's profile once the levels exist, so its source is picked here. Chunks that
		// loaded during startup were not queued (no source yet); the atlas picks them up now. The Arnis map is
		// retired (archive/arnis/): an old Arnis world still loads, but nothing reads its sidecar any more, so say
		// so instead of letting loot and populations silently stop.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (Files.exists(server.getWorldPath(LevelResource.ROOT).resolve(ARNIS_SIDECAR))) {
				LOGGER.warn("This world was made with the retired Arnis map; its building data is no longer read. "
						+ "Create a new world with the Lost Cities profile for building-aware loot and populations.");
			}
			if (buildings.isEmpty() && LostCityContext.available()) {
				LostCitiesBuildingSource.forLevel(server.overworld()).ifPresent(source -> {
					buildings = source;
					LOGGER.info("Building context from The Lost Cities (profile {})",
							LostCityContext.profile(server.overworld()).orElse("?"));
				});
			}
			AtlasProcessor.onSourceReady(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			buildings = BuildingSource.NONE;
			// Caches built from the old source hold its ServerLevel; drop them so a closed world is not kept alive.
			xyz.toesinthesand.ashfall.salvage.loot.PresenceTracker.reset();
		});
		AtlasProcessor.register();

		// City layout: zone data, /ashfall city, the Montford spawn, and (when The Lost Cities is installed)
		// our Lost Cities profile. The zone hooks themselves are mixins gated on Lost Cities being present.
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ZoneLoader.ID, new ZoneLoader());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			CityCommands.register(dispatcher);
			xyz.toesinthesand.ashfall.salvage.site.SiteCommand.register(dispatcher);
		});
		CitySpawn.register();
		// The Wreck hull and the crash sites: authored templates our own code places outside the city generator.
		xyz.toesinthesand.ashfall.salvage.site.Sites.register();
		if (LostCityContext.available()) {
			LostCitiesBridge.registerProfile();
			LostCitiesBridge.registerLandmarkProtection();
			xyz.toesinthesand.ashfall.salvage.city.LandmarkCleanup.register();
			xyz.toesinthesand.ashfall.salvage.city.DistrictGround.register();
		}

		LOGGER.info("Ashfall Salvage loaded");
	}
}
