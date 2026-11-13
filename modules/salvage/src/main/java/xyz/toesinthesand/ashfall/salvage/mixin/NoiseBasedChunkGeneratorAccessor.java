package xyz.toesinthesand.ashfall.salvage.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The generator's memoized global fluid picker: the one every aquifer of its chunks is handed. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
	@Accessor("globalFluidPicker")
	Supplier<Aquifer.FluidPicker> ashfall$globalFluidPicker();
}
