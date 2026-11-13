package xyz.toesinthesand.ashfall.salvage.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.salvage.city.DistrictGround;

/**
 * No rivers, oceans or ponds in the city ({@link DistrictGround#keepsDry}). The aquifer decides, block by block,
 * whether open terrain holds water; a null answer means "solid", so the noise fill puts the generator's default stone
 * there and the surface rules later top it with grass. Carvers ask the same aquifer, so caves and canyons dug below
 * the old water line don't fill either: null makes them skip the block.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class NoiseBasedAquiferMixin {
	@Shadow
	@Final
	private Aquifer.FluidPicker globalFluidPicker;

	@Inject(method = "computeSubstance", at = @At("RETURN"), cancellable = true)
	private void ashfall$dryCity(int x, int y, int z, double density, CallbackInfoReturnable<BlockState> cir) {
		BlockState state = cir.getReturnValue();
		if (state != null && state.is(Blocks.WATER) && DistrictGround.keepsDry(globalFluidPicker, x, y, z)) {
			cir.setReturnValue(null);
		}
	}
}
