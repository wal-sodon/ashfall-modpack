package xyz.toesinthesand.ashfall.arms.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.arms.gun.MobGuns;

/**
 * A mob that dies holding a blaster (a zombie that picked up a dead player's sidearm, any mob given one)
 * always drops it, intact. Vanilla's equipment drop would roll the slot's drop chance and could destroy a
 * gun the player spent hours on; this runs first, at the head of every death drop, whatever the mob's own
 * loot rules or the {@code doMobLoot} rule say.
 */
@Mixin(LivingEntity.class)
abstract class MobGunDropMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
	private void ashfall$dropHeldGuns(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob) {
			MobGuns.dropHeldGuns(level, mob);
		}
	}
}
