package xyz.toesinthesand.ashfall.salvage.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.salvage.city.DistrictGround;

/** Skips vanilla's surface decoration in a district chunk ({@link DistrictGround#BARE_STEPS}). */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorDecorationMixin {
	@Shadow
	@Final
	private Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;

	@WrapOperation(method = "applyBiomeDecoration", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean ashfall$bareDistricts(FeaturePlacer placer, PlacedFeature feature, RandomSource random,
			BlockPos origin, Operation<Boolean> original, @Local FeatureSorter.StepFeatureData stepData) {
		ChunkGenerator self = (ChunkGenerator) (Object) this;
		int chunkX = SectionPos.blockToSectionCoord(origin.getX());
		int chunkZ = SectionPos.blockToSectionCoord(origin.getZ());
		if (!DistrictGround.decorates(self, chunkX, chunkZ, ashfall$step(stepData))) {
			return false;
		}
		return original.call(placer, feature, random, origin);
	}

	private int ashfall$step(FeatureSorter.StepFeatureData stepData) {
		List<FeatureSorter.StepFeatureData> steps = featuresPerStep.get();
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i) == stepData) {
				return i;
			}
		}
		return -1;
	}
}
