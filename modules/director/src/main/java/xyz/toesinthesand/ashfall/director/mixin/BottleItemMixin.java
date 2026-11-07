package xyz.toesinthesand.ashfall.director.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.salvage.item.SalvageItems;

/**
 * A glass bottle dipped in a water source comes up with dirty water instead of the vanilla water
 * bottle: open water is never safe after the fall. The only {@code PotionContents.createItemStack}
 * call in {@code BottleItem.use} is that water branch (dragon's breath builds its stack directly).
 * <p>
 * The one exception is {@link Hydration#cleanWaterAt}: where the grid's city pump runs again, the same
 * bottle comes up as clean bottled water. The player's position stands for the water's, which is at
 * most a reach away and always in the same district.
 */
@Mixin(BottleItem.class)
abstract class BottleItemMixin {
	@ModifyExpressionValue(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/alchemy/PotionContents;createItemStack(Lnet/minecraft/world/item/Item;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack ashfall$fillWithDirtyWater(ItemStack waterBottle, Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel server && Hydration.cleanWaterAt(server, player.blockPosition())) {
			return new ItemStack(SalvageItems.BOTTLED_WATER);
		}
		return new ItemStack(SalvageItems.DIRTY_WATER);
	}
}
