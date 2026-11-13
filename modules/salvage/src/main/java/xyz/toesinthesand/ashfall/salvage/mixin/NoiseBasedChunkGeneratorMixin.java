package xyz.toesinthesand.ashfall.salvage.mixin;

import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.salvage.city.DistrictGround;

/** Plains above the cellars in a district ({@link DistrictGround}); the biome fill goes through this resolver. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
	@Inject(method = "decorateBiomeResolver", at = @At("RETURN"), cancellable = true)
	private void ashfall$plainDistricts(Blender blender, ChunkAccess chunk, BiomeResolver resolver,
			CallbackInfoReturnable<BiomeResolver> cir) {
		cir.setReturnValue(DistrictGround.resolver((ChunkGenerator) (Object) this, chunk.getPos().x(),
				chunk.getPos().z(), cir.getReturnValue()));
	}
}
