package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.director.senses.GameEventNoise;

/**
 * Noise hook. Every {@code LevelAccessor#gameEvent} overload ends in this method on the server, the same
 * funnel sculk sensors listen to, so one HEAD injection turns doors, explosions and footsteps into SOUND
 * stimuli without touching each block or entity.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelGameEventMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
			at = @At("HEAD"))
	private void ashfall$noise(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		GameEventNoise.onGameEvent((ServerLevel) (Object) this, event, pos, context);
	}
}
