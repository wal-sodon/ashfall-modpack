package xyz.toesinthesand.ashfall.salvage.city;

import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.salvage.AshfallSalvage;
import xyz.toesinthesand.ashfall.salvage.mixin.NoiseBasedChunkGeneratorAccessor;

/**
 * Keeps vanilla's land out of the city. Lost Cities builds the city in the first generation step (raw generation)
 * on top of whatever vanilla terrain is there, and vanilla then decorates the result for the biome underneath:
 * mangrove roots and mud through Downtown, snow and ice on the streets, pointed dripstone from an exposed cave
 * biome, trees through roofs. In an Ashfall overworld:
 * <ul>
 * <li>in every city zone ({@link CityZoneRules#plainsBiome}: the districts, Montford, the suburbs and the
 * wasteland) the biome from {@link #PLAINS_FROM_Y} up is plains ({@code NoiseBasedChunkGeneratorMixin}). Surface
 * blocks, grass and water colours are plain ones, and since plains is warm, the city only ever gets rain, never
 * snow. Caves below keep their real biomes;</li>
 * <li>in every city zone no water forms from {@link #DRY_FROM_Y} up ({@code NoiseBasedAquiferMixin}): where vanilla
 * would fill a river, an ocean or a pond, the ground stays solid to the old water line and gets a grass top, and
 * caves that would flood stay rock. No lava lakes or springs ({@link #CITY_BARE_STEPS}) either, and no ocean
 * structures ({@link CityZoneRules#KEPT_OUT_OF_CITY}) start there;</li>
 * <li>in a district ({@link CityZoneRules#forcesCity}) the surface decoration steps ({@link #BARE_STEPS}) don't
 * run at all ({@code ChunkGeneratorDecorationMixin}). Lost Cities' own feature (raw generation), structures, ores
 * and springs are untouched.</li>
 * </ul>
 * This does not flatten terrain: vanilla shapes the land from noise, not biomes.
 * <p>
 * Only the overworld generator of a level that uses the Ashfall profile is affected, set when that level loads
 * (before its first chunk is made).
 */
public final class DistrictGround {
	/**
	 * Lowest block Y with the plains biome in the city. Streets stand at 75 ({@code cityLevel0Height}) and
	 * cellars reach two floors (12 blocks) under them, so this covers every cellar with a little to spare.
	 */
	public static final int PLAINS_FROM_Y = 56;
	/**
	 * Lowest block Y where the city's terrain gets no water. Oceans bottom out around 30 and river beds above that,
	 * so everything they would hold is solid ground; deep flooded caves below keep their water.
	 */
	public static final int DRY_FROM_Y = 0;
	/** Decoration steps skipped everywhere in the city: lava lakes and water and lava springs. */
	public static final Set<GenerationStep.Decoration> CITY_BARE_STEPS = EnumSet.of(
			GenerationStep.Decoration.LAKES,
			GenerationStep.Decoration.FLUID_SPRINGS);
	/** Decoration steps skipped in a district chunk (on top of {@link #CITY_BARE_STEPS}). */
	public static final Set<GenerationStep.Decoration> BARE_STEPS = EnumSet.of(
			GenerationStep.Decoration.LAKES,
			GenerationStep.Decoration.LOCAL_MODIFICATIONS,
			GenerationStep.Decoration.SURFACE_STRUCTURES,
			GenerationStep.Decoration.UNDERGROUND_DECORATION,
			GenerationStep.Decoration.VEGETAL_DECORATION);

	private static final GenerationStep.Decoration[] STEPS = GenerationStep.Decoration.values();

	private record Active(ChunkGenerator generator, Holder<Biome> plains, Aquifer.@Nullable FluidPicker fluids) {
		Active(ChunkGenerator generator, Holder<Biome> plains) {
			this(generator, plains, generator instanceof NoiseBasedChunkGenerator noise
					? ((NoiseBasedChunkGeneratorAccessor) (Object) noise).ashfall$globalFluidPicker().get() : null);
		}
	}

	private static volatile @Nullable Active active;

	private DistrictGround() {
	}

	public static void register() {
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				enable(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = null);
	}

	private static void enable(ServerLevel level) {
		active = null;
		if (!LostCityContext.isAshfall(level)) {
			return;
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		// Features are sorted over the biome source's own biomes; a biome it can't produce would crash decoration.
		if (!generator.getBiomeSource().possibleBiomes().contains(plains)) {
			AshfallSalvage.LOGGER.warn("The overworld can't make plains; districts keep vanilla biomes and decoration");
			return;
		}
		active = new Active(generator, plains);
		AshfallSalvage.LOGGER.info("The city generates as dry plains, and districts without vanilla surface decoration");
	}

	/** Test hook: act for {@code generator} as if it were an Ashfall overworld's (null turns it off again). */
	public static void setForTesting(@Nullable ChunkGenerator generator, @Nullable Holder<Biome> plains) {
		active = generator == null || plains == null ? null : new Active(generator, plains);
	}

	/** True if {@code generator} is the Ashfall overworld's. */
	public static boolean isCityGenerator(ChunkGenerator generator) {
		Active current = active;
		return current != null && current.generator == generator;
	}

	/**
	 * True if an aquifer working for the global fluid picker {@code fluids} must not put water at (x, y, z): the
	 * Ashfall overworld's own picker (aquifers carry no other link to their generator), inside a city zone, from
	 * {@link #DRY_FROM_Y} up.
	 */
	public static boolean keepsDry(Aquifer.FluidPicker fluids, int x, int y, int z) {
		Active current = active;
		return current != null && current.fluids == fluids && y >= DRY_FROM_Y
				&& CityZoneRules.plainsBiome(x >> 4, z >> 4);
	}

	/** True if {@code generator} is the Ashfall overworld's and the chunk is in a district. */
	public static boolean appliesTo(ChunkGenerator generator, int chunkX, int chunkZ) {
		Active current = active;
		return current != null && current.generator == generator && CityZoneRules.forcesCity(chunkX, chunkZ);
	}

	/** {@code resolver} with plains from {@link #PLAINS_FROM_Y} up, if the chunk is in a city zone. */
	public static BiomeResolver resolver(ChunkGenerator generator, int chunkX, int chunkZ, BiomeResolver resolver) {
		Active current = active;
		if (current == null || current.generator != generator || !CityZoneRules.plainsBiome(chunkX, chunkZ)) {
			return resolver;
		}
		Holder<Biome> plains = current.plains;
		int fromQuart = QuartPos.fromBlock(PLAINS_FROM_Y);
		return (x, y, z) -> y >= fromQuart ? plains : resolver.getNoiseBiome(x, y, z);
	}

	/** False if features of decoration step {@code step} (an ordinal) must not run in this chunk. */
	public static boolean decorates(ChunkGenerator generator, int chunkX, int chunkZ, int step) {
		if (!isCityGenerator(generator) || step < 0 || step >= STEPS.length) {
			return true;
		}
		GenerationStep.Decoration decoration = STEPS[step];
		if (CITY_BARE_STEPS.contains(decoration) && CityZoneRules.plainsBiome(chunkX, chunkZ)) {
			return false;
		}
		return !BARE_STEPS.contains(decoration) || !CityZoneRules.forcesCity(chunkX, chunkZ);
	}
}
