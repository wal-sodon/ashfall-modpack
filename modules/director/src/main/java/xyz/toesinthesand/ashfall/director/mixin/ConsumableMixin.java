package xyz.toesinthesand.ashfall.director.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.infection.Infection;

/**
 * Drinking and treatment hook. Every consumable (food, potions, our bottles, Bandages, Antibiotics)
 * finishes through {@code Consumable.onConsume}, so one injection covers Ashfall's water items, the
 * vanilla water bottle and the medical items alike. HEAD, because the stack is shrunk at the end of the
 * method.
 */
@Mixin(Consumable.class)
abstract class ConsumableMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void ashfall$quenchThirst(Level level, LivingEntity entity, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (entity instanceof ServerPlayer player) {
			Hydration.onConsume(player, stack);
			Infection.onConsume(player, stack);
		}
	}
}
