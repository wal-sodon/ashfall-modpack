package xyz.toesinthesand.ashfall.salvage.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import xyz.toesinthesand.ashfall.salvage.city.DistrictGround;
import xyz.toesinthesand.ashfall.salvage.city.ZoneMap;
import xyz.toesinthesand.ashfall.salvage.mixin.NoiseBasedChunkGeneratorAccessor;

/**
 * {@link DistrictGround}: dry plains above the cellars across the whole city, no surface decoration in a district, and
 * nothing changed in the wilderness. The test overworld is not an Ashfall world, so the hook is pointed at the
 * {@code lc_city} test dimension's generator. It is given that dimension's own plains, so tests generating chunks there at the same time
 * see no change of biome, and a stand-in resolver plays the vanilla mangrove swamp.
 */
public class DistrictGroundGameTest {
	private static final ResourceKey<Level> LC_CITY = ResourceKey.create(Registries.DIMENSION,
			Identifier.fromNamespaceAndPath("ashfall_test", "lc_city"));

	@GameTest
	public void cityGetsPlainsAndDistrictsNoSurfaceDecoration(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(LC_CITY);
		helper.assertTrue(level != null, "test dimension ashfall_test:lc_city is missing");
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		Holder<Biome> swamp = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.MANGROVE_SWAMP);
		BiomeResolver vanilla = (x, y, z) -> swamp;
		int streetQuart = QuartPos.fromBlock(76);
		int caveQuart = QuartPos.fromBlock(DistrictGround.PLAINS_FROM_Y - 8);
		// Downtown's centre chunk, and a Ridge (wilderness) chunk.
		helper.assertTrue(ZoneMap.zoneAtChunk(0, 0).isDistrict(), "chunk 0, 0 is not in a district");
		int ridgeX = 2300 >> 4;
		helper.assertTrue("ridge".equals(ZoneMap.zoneAtChunk(ridgeX, 0).id()), "chunk " + ridgeX + ", 0 is not Ridge");
		// Suburbs, wasteland and Montford: city, but not districts.
		int[][] outskirts = {{1000 >> 4, -1000 >> 4}, {-2000 >> 4, 1000 >> 4}, {-150 >> 4, -800 >> 4}};

		DistrictGround.setForTesting(generator, plains);
		try {
			BiomeResolver district = DistrictGround.resolver(generator, 0, 0, vanilla);
			helper.assertTrue(district.getNoiseBiome(0, streetQuart, 0) == plains, "district street level kept its biome");
			helper.assertTrue(district.getNoiseBiome(0, caveQuart, 0) == swamp, "district caves lost their biome");
			helper.assertTrue(DistrictGround.resolver(generator, ridgeX, 0, vanilla) == vanilla,
					"wilderness biomes were changed");
			for (int[] c : outskirts) {
				String zone = ZoneMap.zoneAtChunk(c[0], c[1]).id();
				BiomeResolver outer = DistrictGround.resolver(generator, c[0], c[1], vanilla);
				helper.assertTrue(outer.getNoiseBiome(0, streetQuart, 0) == plains, zone + " is not plains at street level");
				helper.assertTrue(outer.getNoiseBiome(0, caveQuart, 0) == swamp, zone + " caves lost their biome");
				helper.assertTrue(DistrictGround.decorates(generator, c[0], c[1],
						GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()), zone + " lost its decoration");
				for (GenerationStep.Decoration step : DistrictGround.CITY_BARE_STEPS) {
					helper.assertFalse(DistrictGround.decorates(generator, c[0], c[1], step.ordinal()),
							zone + " still gets " + step);
				}
			}

			for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
				boolean bare = DistrictGround.BARE_STEPS.contains(step) || DistrictGround.CITY_BARE_STEPS.contains(step);
				helper.assertTrue(DistrictGround.decorates(generator, 0, 0, step.ordinal()) != bare,
						"district step " + step + (bare ? " still decorates" : " was skipped"));
				helper.assertTrue(DistrictGround.decorates(generator, ridgeX, 0, step.ordinal()),
						"wilderness step " + step + " was skipped");
			}
			helper.assertTrue(DistrictGround.decorates(generator, 0, 0, GenerationStep.Decoration.RAW_GENERATION.ordinal()),
					"Lost Cities' own step was skipped");

			// No water forms in the city above DRY_FROM_Y, through this generator's own fluid picker only.
			Aquifer.FluidPicker fluids = ((NoiseBasedChunkGeneratorAccessor) (Object) generator).ashfall$globalFluidPicker().get();
			helper.assertTrue(DistrictGround.keepsDry(fluids, 8, 60, 8), "Downtown can hold water");
			helper.assertTrue(DistrictGround.keepsDry(fluids, 1000, 40, -1000), "the suburbs can hold water");
			helper.assertFalse(DistrictGround.keepsDry(fluids, 8, DistrictGround.DRY_FROM_Y - 1, 8), "deep caves went dry");
			helper.assertFalse(DistrictGround.keepsDry(fluids, 2300, 60, 0), "the Ridge went dry");
			helper.assertFalse(DistrictGround.keepsDry((x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()),
					8, 60, 8), "another generator's water went dry");

			// Any other generator (the gametest overworld here) is left alone.
			ChunkGenerator other = helper.getLevel().getChunkSource().getGenerator();
			helper.assertTrue(DistrictGround.resolver(other, 0, 0, vanilla) == vanilla, "another level's biomes changed");
			helper.assertTrue(DistrictGround.decorates(other, 0, 0, GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()),
					"another level's decoration was skipped");
		} finally {
			DistrictGround.setForTesting(null, null);
		}
		helper.succeed();
	}
}
