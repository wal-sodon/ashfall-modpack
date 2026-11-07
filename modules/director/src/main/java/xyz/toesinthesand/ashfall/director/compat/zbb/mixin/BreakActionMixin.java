package xyz.toesinthesand.ashfall.director.compat.zbb.mixin;

import com.tik.zbb.ai.action.MobActionContext;
import com.tik.zbb.ai.action.actions.breakk.BreakAction;
import com.tik.zbb.ai.action.actions.breakk.BreakRequest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.fortify.Fortified;
import xyz.toesinthesand.ashfall.salvage.atlas.LoadedChunks;

/**
 * Fortified blocks and safe zones. {@code canExecute} is ZB&B's single gate before every block break, so refusing at
 * HEAD also skips its damage accumulation and crack visuals. A refused fortified block takes wear instead
 * ({@link Fortified#zombieBreak}, our own cracks); once worn through it downgrades and the next ask passes. ZB&B sets
 * no cooldown on a refusal and asks again later, so the check is a short safe-zone scan, two tag lookups and, for a
 * fortified block, one attachment read per zombie (its hits are spaced by {@code hit_interval_ticks}).
 */
@Mixin(BreakAction.class)
abstract class BreakActionMixin {
	@Inject(method = "canExecute(Lcom/tik/zbb/ai/action/MobActionContext;Lcom/tik/zbb/ai/action/actions/breakk/BreakRequest;)Z",
			at = @At("HEAD"), cancellable = true)
	private void ashfall$fortified(MobActionContext context, BreakRequest request, CallbackInfoReturnable<Boolean> cir) {
		// ZB&B checks isLoaded itself, but after HEAD: don't let our block read load a chunk first
		if (LoadedChunks.ready(context.level(), request.pos())
				&& !Fortified.zombieBreak(context.level(), request.pos(), context.level().getBlockState(request.pos()), context.mob())) {
			cir.setReturnValue(false);
		}
	}
}
