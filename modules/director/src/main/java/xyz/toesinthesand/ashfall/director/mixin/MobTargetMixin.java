package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.director.haven.RespawnChoice;
import xyz.toesinthesand.ashfall.director.senses.Stun;

/**
 * Respawn protection's "zombies ignore you": {@code setTarget} is where every goal (vanilla target goals,
 * our {@code SenseGoal}) hands a mob its target, so refusing a protected player
 * here covers all of them with one check, and clearing ({@code null}) always passes. A stunned mob
 * ({@link Stun}) takes no new target at all.
 */
@Mixin(Mob.class)
abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void ashfall$respawnProtection(LivingEntity target, CallbackInfo ci) {
		if (target instanceof ServerPlayer player && RespawnChoice.isProtected(player)
				&& RespawnChoice.affectedBy((Mob) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void ashfall$noTargetWhileStunned(LivingEntity target, CallbackInfo ci) {
		if (target != null && Stun.isStunned((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
