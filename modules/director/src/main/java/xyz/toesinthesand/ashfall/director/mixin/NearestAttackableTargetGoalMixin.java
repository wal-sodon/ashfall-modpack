package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.director.senses.HuntSurvivorsGoal;
import xyz.toesinthesand.ashfall.director.senses.SenseGoal;

/**
 * A hunting zombie's vanilla player target goal weighs survivors too (see {@link HuntSurvivorsGoal} for why this hook
 * rather than a replacement goal): after vanilla finds the nearest player, {@link HuntSurvivorsGoal#choose} picks
 * between that player, the nearest survivor and the zombie's current target, so a nearer survivor is picked and a
 * survivor already being chased is adopted instead of dropped for a player only slightly nearer. Other mobs and other
 * target types are untouched. Extends {@link TargetGoal} only to reach its protected {@code mob}.
 */
@Mixin(NearestAttackableTargetGoal.class)
abstract class NearestAttackableTargetGoalMixin extends TargetGoal {
	@Shadow
	@Final
	protected Class<?> targetType;

	@Shadow
	protected LivingEntity target;

	private NearestAttackableTargetGoalMixin(Mob mob, boolean mustSee) {
		super(mob, mustSee);
	}

	@Inject(method = "findTarget", at = @At("TAIL"))
	private void ashfall$weighSurvivors(CallbackInfo ci) {
		if (!Player.class.isAssignableFrom(targetType) || !(mob instanceof PathfinderMob hunter)
				|| !HuntSurvivorsGoal.huntsSurvivors(hunter)) {
			return;
		}
		LivingEntity current = mob.getTarget();
		LivingEntity player = target != null && HuntSurvivorsGoal.mayChase(mob, target) ? target : null;
		target = HuntSurvivorsGoal.choose(mob, current != null && HuntSurvivorsGoal.isPrey(current) ? current : null,
				player, HuntSurvivorsGoal.nearestSurvivor(hunter, SenseGoal.SPOT_RANGE));
	}
}
