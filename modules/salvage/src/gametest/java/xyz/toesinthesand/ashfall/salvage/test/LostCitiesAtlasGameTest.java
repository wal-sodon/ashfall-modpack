package xyz.toesinthesand.ashfall.salvage.test;

import mcjty.lostcities.LostCities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasData;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.atlas.LightStripper;
import xyz.toesinthesand.ashfall.salvage.atlas.RelayPlacer;
import xyz.toesinthesand.ashfall.salvage.atlas.RelayPlan;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.BuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.LostCitiesBuildingSource;
import xyz.toesinthesand.ashfall.salvage.city.CityChunk;
import xyz.toesinthesand.ashfall.salvage.city.LostCityContext;
import xyz.toesinthesand.ashfall.salvage.city.Zone;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.loot.LootPlaceholder;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.List;
import java.util.Optional;

/**
 * The salvage passes on a real Lost Cities city ({@code ashfall_test:lc_city}, see {@link CityZoneGameTest} for
 * why a separate dimension): building instances from Lost Cities' plans, a generated building processed end to
 * end (placeholder loot at generation, building-profile loot and per-storey top-up on first load, lights off),
 * and the district key relays placed on streets.
 * <p>
 * The gametest world persists between runs, so chunks may already be generated and processed; each test clears
 * the chunk's atlas version before processing, and every pass is idempotent.
 */
public class LostCitiesAtlasGameTest {
	private static final ResourceKey<Level> LC_CITY = ResourceKey.create(Registries.DIMENSION,
			Identifier.fromNamespaceAndPath("ashfall_test", "lc_city"));

	@GameTest(maxTicks = 400)
	public void instancesFollowLostCitiesPlans(GameTestHelper helper) {
		ServerLevel level = cityLevel(helper);
		BuildingSource source = source(helper, level);
		int multis = 0, streets = 0, classified = 0;
		for (int cx = -20; cx <= 20; cx++) {
			for (int cz = -20; cz <= 20; cz++) {
				Optional<CityChunk> plan = source.chunk(cx, cz);
				if (plan.isEmpty() || !plan.get().isCity()) {
					continue;
				}
				CityChunk c = plan.get();
				if (c.isStreet()) {
					helper.assertTrue(source.inChunk(cx, cz).isEmpty(), "street chunk " + cx + "," + cz + " has a building");
					streets++;
					continue;
				}
				Building building = source.inChunk(cx, cz).getFirst();
				// Every part of a multibuilding names the same instance, keyed by its north-west origin chunk.
				helper.assertValueEqual(building.id(), ChunkPos.pack(c.originX(), c.originZ()), "instance id of " + cx + "," + cz);
				helper.assertTrue(source.get(building.id()) == building, "get(id) does not find the instance of " + cx + "," + cz);
				helper.assertTrue(building.footprint().contains(cx * 16 + 8, cz * 16 + 8), "footprint misses chunk " + cx + "," + cz);
				if (c.multiBuilding() != null) {
					multis++;
					CityChunk origin = source.chunk(c.originX(), c.originZ()).orElseThrow();
					helper.assertTrue(origin.multiX() == 0 && origin.multiZ() == 0 && c.multiBuilding().equals(origin.multiBuilding()),
							"origin of " + cx + "," + cz + " is not the multibuilding's north-west chunk: " + origin);
					helper.assertValueEqual(building.footprint().area(), 256 * c.multiW() * c.multiH(), "multibuilding footprint");
				}
				helper.assertValueEqual(building.units().size(), c.floors() + c.cellars(), "storeys of " + cx + "," + cz);
				helper.assertTrue(ZoneMap.zoneAtChunk(c.originX(), c.originZ()).id().equals(building.tags().get(LostCitiesBuildingSource.TAG_ZONE)),
						"zone tag of " + cx + "," + cz);
				if (BuildingProfiles.classifyByTags(building) != null) {
					classified++;
				}
			}
		}
		helper.assertTrue(multis > 0, "no multibuilding near Downtown");
		helper.assertTrue(streets > 0, "no street near Downtown");
		helper.assertTrue(classified > 0, "no Lost Cities building matched a building profile");
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void processesAGeneratedBuilding(GameTestHelper helper) {
		ServerLevel level = cityLevel(helper);
		BuildingSource source = source(helper, level);
		CityChunk target = findTower(helper, source);
		ChunkPos pos = new ChunkPos(target.chunkX(), target.chunkZ());
		level.getChunkSource().addTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
		try {
			LevelChunk chunk = level.getChunk(pos.x(), pos.z());
			Building building = source.inChunk(pos.x(), pos.z()).getFirst();

			// Generation: Lost Cities' own loot conditions were swapped for our placeholder, so Lootr never sees them.
			int generated = 0;
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				if (be instanceof RandomizableContainer rc && rc.getLootTable() != null) {
					generated++;
					helper.assertTrue(LootPlaceholder.isOurs(rc.getLootTable().identifier()),
							"Lost Cities container at " + be.getBlockPos() + " kept table " + rc.getLootTable().identifier());
				}
			}

			AtlasProcessor.setBuildingsForTesting(source);
			chunk.removeAttached(AtlasProcessor.VERSION);
			AtlasProcessor.processChunk(level, chunk);
			helper.assertValueEqual(chunk.getAttachedOrElse(AtlasProcessor.VERSION, 0), AtlasProcessor.CURRENT_VERSION, "atlas version");
			helper.assertValueEqual(LightStripper.count(chunk), 0, "lights left in the building chunk");

			Identifier byTags = BuildingProfiles.classifyByTags(building);
			Identifier expected = byTags != null ? byTags : BuildingProfiles.GENERIC;
			int containers = 0;
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				BlockPos at = be.getBlockPos();
				boolean inside = at.getY() >= building.baseY() && at.getY() <= building.topY() + 1;
				if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) || !inside) {
					continue;
				}
				LootState state = be.getAttached(AshfallSalvage.LOOT_STATE);
				helper.assertTrue(state != null, "container at " + at + " in " + building.tags().get(LostCitiesBuildingSource.TAG_KEY) + " has no LootState");
				Identifier profile = state.profile().orElse(null);
				// Storey containers take the building's profile; a stray one outside every storey falls back to it too.
				helper.assertTrue(expected.equals(profile), "container at " + at + " has profile " + profile + ", expected " + expected);
				Identifier table = BuildingProfiles.get(expected).lootTable().orElseThrow();
				helper.assertTrue(table.equals(state.table()), "container at " + at + " has table " + state.table() + ", expected " + table);
				containers++;
			}
			AshfallSalvage.LOGGER.info("LC atlas test: {} ({}, {} floors, {} cellars) had {} containers with a table before processing, {} after",
					building.tags().get(LostCitiesBuildingSource.TAG_KEY), expected, target.floors(), target.cellars(), generated, containers);
			helper.assertTrue(containers >= 1, "building " + building.tags().get(LostCitiesBuildingSource.TAG_KEY) + " (" + expected
					+ ", " + target.floors() + " floors) has no loot container after processing");
		} finally {
			AtlasProcessor.setBuildingsForTesting(null);
			level.getChunkSource().removeTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
		}
		helper.succeed();
	}

	/**
	 * Modern Tweaks multibuildings whose parts carry loot conditions (shops, town hall, data centre, library,
	 * factory): every container Lost Cities filled there must hold an Ashfall table, never its own.
	 */
	@GameTest(maxTicks = 2400)
	public void generatedContainersGetPlaceholderLoot(GameTestHelper helper) {
		ServerLevel level = cityLevel(helper);
		BuildingSource source = source(helper, level);
		List<String> looted = List.of("lcmt:shopping", "lcmt:shopping_open", "lcmt:townhall", "lcmt:center", "lcmt:library", "lcmt:factory");
		int chunks = 0, withTable = 0;
		for (int r = 0; r <= 24 && chunks < 12 && withTable == 0; r++) {
			for (int cx = -r; cx <= r && chunks < 12; cx++) {
				for (int cz = -r; cz <= r && chunks < 12; cz++) {
					if (Math.max(Math.abs(cx), Math.abs(cz)) != r) {
						continue;
					}
					CityChunk c = source.chunk(cx, cz).orElse(null);
					if (c == null || !c.hasBuilding() || c.multiBuilding() == null || !looted.contains(c.multiBuilding())) {
						continue;
					}
					ChunkPos pos = new ChunkPos(cx, cz);
					level.getChunkSource().addTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
					try {
						LevelChunk chunk = level.getChunk(cx, cz);
						chunks++;
						for (BlockEntity be : chunk.getBlockEntities().values()) {
							if (be instanceof RandomizableContainer rc && rc.getLootTable() != null && !be.hasAttached(AshfallSalvage.LOOT_STATE)) {
								withTable++;
								helper.assertTrue(rc.getLootTable().identifier().equals(LootPlaceholder.TABLE),
										"container at " + be.getBlockPos() + " in " + c.multiBuilding() + " has table " + rc.getLootTable().identifier());
							}
						}
					} finally {
						level.getChunkSource().removeTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
					}
				}
			}
		}
		AshfallSalvage.LOGGER.info("LC atlas test: {} generated containers with a table in {} looted multibuilding chunks", withTable, chunks);
		helper.assertTrue(chunks > 0, "no Modern Tweaks multibuilding with loot near Downtown");
		helper.assertTrue(withTable > 0, "no generated container with a loot table in " + chunks + " looted multibuilding chunks");
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void keyRelaysStandOnDistrictStreets(GameTestHelper helper) {
		ServerLevel level = cityLevel(helper);
		BuildingSource source = source(helper, level);
		List<RelayPlan.KeyRelay> relays = RelayPlan.keyRelays(ZoneMap.current(), source::chunk);
		int targets = 0;
		for (Zone zone : ZoneMap.current().zones()) {
			targets += zone.keyRelayTargets().size();
		}
		helper.assertTrue(targets > 0, "no key relay targets in the zone data");
		helper.assertValueEqual(relays.size(), targets, "key relays found");
		for (RelayPlan.KeyRelay relay : relays) {
			helper.assertTrue(source.chunk(relay.chunkX(), relay.chunkZ()).map(CityChunk::isStreet).orElse(false),
					"key relay " + relay + " is not on a street chunk");
			helper.assertTrue(Math.hypot((relay.chunkX() << 4) + 8 - relay.targetX(), (relay.chunkZ() << 4) + 8 - relay.targetZ()) < 16 * 9,
					"key relay " + relay + " is far from its target");
		}

		// Place one for real: the downtown relay nearest the origin.
		RelayPlan.KeyRelay relay = relays.stream().filter(r -> r.zone().equals("downtown"))
				.min((a, b) -> Long.compare(dist2(a), dist2(b))).orElseThrow();
		long key = RelayPlan.relayKey(relay.chunkX(), relay.chunkZ());
		ChunkPos pos = new ChunkPos(relay.chunkX(), relay.chunkZ());
		level.getChunkSource().addTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
		try {
			RelayPlacer.setBlockForTesting(Blocks.LODESTONE);
			AtlasProcessor.setBuildingsForTesting(source);
			RelayPlan.recordKeyRelays(level, source);
			AtlasData data = AtlasData.get(level.getServer());
			helper.assertTrue(data.relays().stream().anyMatch(r -> r.building() == key && r.dimension().equals(LC_CITY)), "key relay not recorded");
			LevelChunk chunk = level.getChunk(pos.x(), pos.z());
			chunk.removeAttached(AtlasProcessor.VERSION);
			AtlasProcessor.processChunk(level, chunk);
			helper.assertTrue(data.relayPlaced(key), "key relay not marked placed");
			BlockPos cell = relay.pos();
			boolean found = false;
			for (BlockPos p : BlockPos.betweenClosed(cell.offset(-2, -2, -2), cell.offset(2, 2, 2))) {
				found |= level.getBlockState(p).is(Blocks.LODESTONE);
			}
			AshfallSalvage.LOGGER.info("LC atlas test: {} key relays planned; downtown relay at {} placed={}", relays.size(), cell, found);
			helper.assertTrue(found, "no relay block near " + cell + " (holds " + level.getBlockState(cell) + ", below "
					+ level.getBlockState(cell.below()) + ")");
		} finally {
			RelayPlacer.setBlockForTesting(null);
			AtlasProcessor.setBuildingsForTesting(null);
			level.getChunkSource().removeTicketWithRadius(AtlasProcessor.TICKET, pos, 1);
		}
		helper.succeed();
	}

	private static long dist2(RelayPlan.KeyRelay relay) {
		return (long) relay.targetX() * relay.targetX() + (long) relay.targetZ() * relay.targetZ();
	}

	/** The intact one-chunk building with the most floors near Downtown's centre (deterministic per seed). */
	private static CityChunk findTower(GameTestHelper helper, BuildingSource source) {
		CityChunk best = null;
		for (int r = 1; r <= 10 && best == null; r++) {
			for (int cx = -r; cx <= r; cx++) {
				for (int cz = -r; cz <= r; cz++) {
					CityChunk c = source.chunk(cx, cz).orElse(null);
					if (c != null && c.hasBuilding() && c.multiBuilding() == null && c.ruinLevel() < 0 && c.floors() >= 2
							&& (best == null || c.floors() > best.floors())) {
						best = c;
					}
				}
			}
		}
		helper.assertTrue(best != null, "no intact one-chunk building near Downtown");
		return best;
	}

	private static ServerLevel cityLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(LC_CITY);
		helper.assertTrue(level != null, "test dimension ashfall_test:lc_city is missing");
		LostCities.lostCitiesImp.registerDimension(level, LC_CITY, LostCityContext.PROFILE);
		return level;
	}

	private static BuildingSource source(GameTestHelper helper, ServerLevel level) {
		Optional<BuildingSource> source = LostCitiesBuildingSource.forLevel(level);
		helper.assertTrue(source.isPresent(), "Lost Cities is not active in the test dimension");
		return source.get();
	}
}
