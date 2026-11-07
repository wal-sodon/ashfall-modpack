package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.director.senses.Stun;

/**
 * A stunned mob ({@link Stun}) skips its whole AI step: goals, targeting, pathing and look control. Movement
 * physics run elsewhere, so it still falls; the last movement input is zeroed each tick or it would keep walking.
 */
@Mixin(Mob.class)
abstract class MobStunMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void ashfall$frozenWhileStunned(CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (Stun.isStunned(mob)) {
			Stun.halt(mob);
			ci.cancel();
		}
	}
}
