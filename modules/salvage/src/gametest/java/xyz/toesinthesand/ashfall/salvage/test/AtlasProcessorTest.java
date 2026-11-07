package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import xyz.toesinthesand.ashfall.salvage.atlas.ContainerPass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasCommands;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasData;
import xyz.toesinthesand.ashfall.salvage.atlas.AtlasProcessor;
import xyz.toesinthesand.ashfall.salvage.building.Building;
import xyz.toesinthesand.ashfall.salvage.building.Footprint;
import xyz.toesinthesand.ashfall.salvage.building.StaticBuildingSource;
import xyz.toesinthesand.ashfall.salvage.building.Unit;
import xyz.toesinthesand.ashfall.salvage.loot.LootState;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfile;
import xyz.toesinthesand.ashfall.salvage.profile.BuildingProfiles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * End-to-end check of the atlas pipeline on a real server: an Arnis-style room (glowstone ceiling,
 * a hanging lantern, a chest of fixed vanilla items) goes in, a dark room with Ashfall loot
 * containers comes out.
 */
public class AtlasProcessorTest {
	private static final long BUILDING_ID = 990_001L;
	private static final Identifier PROFILE = AshfallSalvage.id("test_home");
	private static final Identifier TABLE = Identifier.withDefaultNamespace("chests/simple_dungeon");
	private static final int MIN_CONTAINERS = 3;

	/**
	 * A chest a landmark design placed with an {@code ashfall_cache} table (the safehouse's Field Radio cache) is a
	 * story cache: the container pass must leave its table alone instead of swapping in the building's loot.
	 */
	@GameTest
	public void authoredCachesAreNotConverted(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		helper.assertFalse(ContainerPass.isAuthoredCache(chest), "a chest with no table counted as a cache");
		chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("ashfall", "buildings/home")));
		helper.assertFalse(ContainerPass.isAuthoredCache(chest), "building loot counted as a cache");
		chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("ashfall_cache", "field_radio")));
		helper.assertTrue(ContainerPass.isAuthoredCache(chest), "the Field Radio cache was not recognised");
		helper.succeed();
	}

	/**
	 * The lag fix: the atlas only processes a chunk whose eight neighbours are fully loaded, and asking never loads
	 * one. Before it, a container or window at a chunk edge made the server thread load or generate the neighbour.
	 */
	@GameTest
	public void neighbourGateNeverLoadsChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos here = ChunkPos.containing(helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(level.getChunkSource().getChunkNow(here.x(), here.z()) != null, "the test chunk is not loaded");
		boolean ringLoaded = true;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ringLoaded &= level.getChunkSource().getChunkNow(here.x() + dx, here.z() + dz) != null;
			}
		}
		helper.assertTrue(AtlasProcessor.neighboursReady(level, here.x(), here.z()) == ringLoaded,
				"the gate disagrees with the chunks actually loaded around the test");
		int far = 100_000;
		helper.assertFalse(AtlasProcessor.neighboursReady(level, far, far), "a chunk nobody loaded counted as ready");
		helper.assertTrue(level.getChunkSource().getChunkNow(far - 1, far - 1) == null, "the gate loaded a neighbour");
		helper.assertTrue(AtlasProcessor.peek(level, new BlockPos(far << 4, 64, far << 4)) == null, "peek loaded a chunk");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void processesArnisRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		int y0 = o.getY();

		// 8x8 shell: floor at y0, stone brick walls y0+1..y0+4, ceiling at y0+5 with a glowstone grid.
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				boolean edge = x == 0 || z == 0 || x == 7 || z == 7;
				level.setBlock(o.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = 1; y <= 4; y++) {
					level.setBlock(o.offset(x, y, z), (edge ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
				}
				boolean lamp = !edge && x % 3 == 2 && z % 3 == 2;
				level.setBlock(o.offset(x, 5, z), (lamp ? Blocks.GLOWSTONE : Blocks.STONE_BRICKS).defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		BlockPos lantern = o.offset(3, 4, 4);
		level.setBlock(lantern, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), Block.UPDATE_ALL);
		BlockPos chestPos = o.offset(1, 1, 1);
		level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.BEDROCK)); // Arnis's fixed items; a full chest proves they were cleared
		}

		// Above the shell's top (outside every building): not Arnis furniture, must be left alone.
		BlockPos foreignPos = o.offset(0, 7, 0);
		level.setBlock(foreignPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		ChestBlockEntity foreign = (ChestBlockEntity) level.getBlockEntity(foreignPos);
		foreign.setItem(0, new ItemStack(Items.DIAMOND));

		Map<Identifier, BuildingProfile> savedProfiles = BuildingProfiles.all();
		try {
			BuildingProfiles.setForTesting(Map.of(PROFILE, new BuildingProfile(List.of(), List.of("Home"), 0, 1, Optional.of(TABLE),
					new BuildingProfile.Containers(0f, MIN_CONTAINERS, MIN_CONTAINERS, List.of(
							new BuildingProfile.ContainerBlock(Identifier.withDefaultNamespace("chest"), 1),
							new BuildingProfile.ContainerBlock(Identifier.withDefaultNamespace("barrel"), 1))),
					BuildingProfile.Refill.DEFAULT, BuildingProfile.Population.NONE, true, 1f, Optional.empty())));
			AtlasProcessor.setBuildingsForTesting(sidecar(o, y0));

			Set<Long> chunks = new LinkedHashSet<>();
			for (int x = 0; x < 8; x += 7) {
				for (int z = 0; z < 8; z += 7) {
					chunks.add(ChunkPos.pack(o.offset(x, 0, z)));
				}
			}
			for (long key : chunks) {
				LevelChunk chunk = level.getChunk(ChunkPos.getX(key), ChunkPos.getZ(key));
				AtlasProcessor.processChunk(level, chunk);
				helper.assertTrue(chunk.getAttachedOrElse(AtlasProcessor.VERSION, 0) == AtlasProcessor.CURRENT_VERSION,
						"chunk not stamped with the atlas version");
			}

			// Lights out.
			for (int x = 1; x < 7; x++) {
				for (int z = 1; z < 7; z++) {
					helper.assertFalse(level.getBlockState(o.offset(x, 5, z)).is(Blocks.GLOWSTONE), "glowstone left at " + x + "," + z);
				}
			}
			helper.assertTrue(level.getBlockState(o.offset(2, 5, 2)).is(Blocks.STONE_BRICKS), "glowstone not patched with the ceiling material");
			helper.assertTrue(level.getBlockState(lantern).isAir(), "lantern not removed");

			// Arnis chest converted: loot table and LootState set, its items gone.
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, TABLE);
			helper.assertTrue(key.equals(chest.getLootTable()), "chest loot table not set: " + chest.getLootTable());
			LootState state = chest.getAttached(AshfallSalvage.LOOT_STATE);
			helper.assertTrue(state != null && state.table().equals(TABLE) && state.profile().equals(Optional.of(PROFILE))
					&& state.lastOpened() == -1, "chest LootState missing or wrong: " + state);
			for (int i = 0; i < chest.getContainerSize(); i++) { // getItem rolls the table first
				helper.assertFalse(chest.getItem(i).is(Items.BEDROCK), "Arnis items were not cleared");
			}

			helper.assertTrue(foreign.getItem(0).is(Items.DIAMOND) && foreign.getLootTable() == null
					&& !foreign.hasAttached(AshfallSalvage.LOOT_STATE), "a chest outside every building was converted");

			// The unit was topped up to its minimum, every container carrying Ashfall loot.
			int containers = 0;
			for (int x = 1; x < 7; x++) {
				for (int z = 1; z < 7; z++) {
					BlockEntity be = level.getBlockEntity(o.offset(x, 1, z));
					if (be != null && be.hasAttached(AshfallSalvage.LOOT_STATE)) {
						containers++;
					}
				}
			}
			helper.assertTrue(containers >= MIN_CONTAINERS, "expected at least " + MIN_CONTAINERS + " containers, found " + containers);

			// /ashfall atlas audit must see the test's (force-loaded) chunks and the containers just made; it used to
			// walk an event-built set and report 0 loaded chunks.
			AtlasCommands.Audit audit = AtlasCommands.audit(level);
			helper.assertTrue(audit.chunks() >= chunks.size(), "audit saw " + audit.chunks() + " loaded chunks, expected at least " + chunks.size());
			helper.assertTrue(audit.containersByProfile().getOrDefault(PROFILE.toString(), 0) >= containers,
					"audit counted " + audit.containersByProfile() + ", expected at least " + containers + " for " + PROFILE);

			AtlasData data = AtlasData.get(level.getServer());
			helper.assertTrue(data.safeCandidates().stream().anyMatch(s -> s.building() == BUILDING_ID), "safe candidate not recorded");
			helper.assertTrue(data.relays().stream().anyMatch(r -> r.building() == BUILDING_ID
					&& r.pos().getY() == y0 + 1), "relay candidate not recorded on the ground floor");
		} finally {
			AtlasProcessor.setBuildingsForTesting(null);
			BuildingProfiles.setForTesting(savedProfiles);
		}
		helper.succeed();
	}

	/**
	 * The one building the old Arnis sidecar test fixture described, built directly now that the sidecar reader
	 * is archived: an 8x8 shell with a 6x6 "Home" unit inside, served by a {@link StaticBuildingSource}.
	 */
	private static StaticBuildingSource sidecar(BlockPos o, int y0) {
		List<int[]> shellRows = new java.util.ArrayList<>(), unitRows = new java.util.ArrayList<>();
		for (int z = 0; z < 8; z++) {
			shellRows.add(new int[]{o.getZ() + z, o.getX(), o.getX() + 7});
		}
		for (int z = 1; z < 7; z++) {
			unitRows.add(new int[]{o.getZ() + z, o.getX() + 1, o.getX() + 6});
		}
		Unit unit = new Unit(0, y0, y0 + 4, "Home", Footprint.of(unitRows));
		Building house = new Building(BUILDING_ID, Map.of("building", "house"), new int[]{y0}, y0, y0 + 5, Footprint.of(shellRows), List.of(unit));
		return new StaticBuildingSource("test", List.of(house));
	}
}
