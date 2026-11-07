package xyz.toesinthesand.ashfall.arms.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.arms.client.gun.ScopeClient;

/** Hides the first-person hands and blaster while looking through the scope, so the overlay is unobstructed. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ScopeHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void ashfall$hideWhileScoped(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
										 PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (ScopeClient.scoped()) {
			ci.cancel();
		}
	}
}
