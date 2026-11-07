package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.spawn.BuildingPopulations;
import xyz.toesinthesand.ashfall.director.spawn.DirectorSpawns;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;

/**
 * Building population zombies never despawn while inside their building, so a building stays
 * populated until players actually clear it. Once one wanders out, vanilla despawn rules apply again
 * (hence not {@code setPersistenceRequired()}, which would be permanent).
 */
@Mixin(Mob.class)
abstract class MobBuildingPersistenceMixin {
	@Inject(method = "requiresCustomPersistence", at = @At("RETURN"), cancellable = true)
	private void ashfall$keepInsideBuilding(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		Mob self = (Mob) (Object) this;
		DirectorZombie tag = DirectorSpawns.tagOf(self);
		if (tag != null && tag.fromBuilding() && BuildingPopulations.insideOwnBuilding(self, tag)) {
			cir.setReturnValue(true);
		}
	}
}
