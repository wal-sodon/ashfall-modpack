package xyz.toesinthesand.ashfall.arms.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.arms.client.gun.ScopeClient;

/**
 * The Optic's scope zoom: narrows the camera's final field of view by {@link ScopeClient}'s eased zoom
 * (4x with the Optic, up to the gun's {@code scope_zoom}) while sneaking with a scoped blaster in first
 * person. Applied after vanilla's FOV modifier (spyglass, bow, speed) on purpose, so the "FOV Effects"
 * accessibility slider does not cancel the scope, and eased per frame by {@link ScopeClient}.
 */
@Mixin(Camera.class)
public abstract class OpticZoomMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void ashfall$scopeZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (((Camera) (Object) this).isPanoramicMode()) {
			return;
		}
		float multiplier = ScopeClient.frameFovMultiplier(Minecraft.getInstance());
		if (multiplier < 1f) {
			cir.setReturnValue(cir.getReturnValueF() * multiplier);
		}
	}
}
