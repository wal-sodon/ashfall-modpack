package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.pets.Dogs;

/**
 * Turns vanilla wolves into Ashfall's dogs (see {@link Dogs}): the zombie guard and growl goals after the wolf's own,
 * the smaller bite once its attributes exist, and taming with Rotten Flesh as well as a bone. Extends {@link TamableAnimal} only to reach {@code Mob}'s protected
 * goal selectors.
 */
@Mixin(Wolf.class)
abstract class WolfMixin extends TamableAnimal {
	private WolfMixin(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Shadow
	private void tryToTame(Player player) {
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void ashfall$dogGoals(CallbackInfo ci) {
		Dogs.addGoals((Wolf) (Object) this, goalSelector, targetSelector);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void ashfall$dogBite(EntityType<? extends Wolf> type, Level level, CallbackInfo ci) {
		Dogs.applyBite((Wolf) (Object) this);
	}

	/**
	 * Rotten Flesh tames a wild dog exactly like vanilla's bone: one is used up, then the same 1 in 3 roll, hearts or
	 * smoke, and a tamed dog sits. Checked first because a wild wolf would otherwise treat the flesh as food.
	 */
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void ashfall$fleshTaming(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack stack = player.getItemInHand(hand);
		Wolf dog = (Wolf) (Object) this;
		if (dog.isTame() || !Dogs.isFleshTreat(stack) || dog.isAngry()) {
			return;
		}
		if (!level().isClientSide()) {
			stack.consume(1, player);
			tryToTame(player);
		}
		cir.setReturnValue(level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
	}
}
