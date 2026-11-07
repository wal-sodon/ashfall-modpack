package xyz.toesinthesand.ashfall.director.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;

/**
 * Thirst's two hooks on the player: it receives a share of every bit of hunger exhaustion, and an
 * empty bar stops sprinting the same way an empty stomach does.
 */
@Mixin(Player.class)
abstract class PlayerThirstMixin {
	/**
	 * Injected right where hunger gets its exhaustion, so the vanilla gates (invulnerable players,
	 * client side) apply to thirst too without being copied.
	 */
	@Inject(method = "causeFoodExhaustion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"))
	private void ashfall$thirstExhaustion(float amount, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			Hydration.onFoodExhaustion(player, amount);
		}
	}

	/**
	 * The sprint gate both sides use (the client decides to sprint). Reads the synced attachment, so the
	 * local player sees the same answer as the server. Flying players are left alone like vanilla.
	 */
	@ModifyReturnValue(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"))
	private boolean ashfall$noSprintWhenParched(boolean original) {
		Player self = (Player) (Object) this;
		return original && (self.getAbilities().mayfly || !Hydration.get(self).blocksSprinting());
	}
}
