package xyz.toesinthesand.ashfall.director.compat.zbb.mixin;

import com.tik.zbb.ai.action.MobActionContext;
import com.tik.zbb.ai.action.actions.build.BuildAction;
import com.tik.zbb.ai.action.actions.build.BuildRequest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;

/**
 * No zombie building inside land claims ({@link Fortified#mayBuild}): ZB&B zombies pillar and bridge
 * toward targets, and inside a claimed base that would let them climb walls the players built.
 */
@Mixin(BuildAction.class)
abstract class BuildActionMixin {
	@Inject(method = "canExecute(Lcom/tik/zbb/ai/action/MobActionContext;Lcom/tik/zbb/ai/action/actions/build/BuildRequest;)Z",
			at = @At("HEAD"), cancellable = true)
	private void ashfall$noBuildingInClaims(MobActionContext context, BuildRequest request, CallbackInfoReturnable<Boolean> cir) {
		if (!Fortified.mayBuild(context.level(), request.pos())) {
			cir.setReturnValue(false);
		}
	}
}
