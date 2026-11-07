package xyz.toesinthesand.ashfall.arms.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.arms.client.gun.ScopeClient;

/**
 * The scope's mouse handling: turning slows with the zoom (like the spyglass, scaled to the
 * magnification), and while aiming a scope the scroll wheel switches zoom steps instead of the hotbar.
 */
@Mixin(MouseHandler.class)
public abstract class ScopeMouseMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double ashfall$scopeYaw(double yaw) {
		return yaw * ScopeClient.sensitivity();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double ashfall$scopePitch(double pitch) {
		return pitch * ScopeClient.sensitivity();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void ashfall$scopeScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (window == minecraft.getWindow().handle() && minecraft.gui.screen() == null && minecraft.gui.overlay() == null
				&& yOffset != 0 && ScopeClient.onScroll(yOffset)) {
			ci.cancel();
		}
	}
}
