package xyz.toesinthesand.ashfall.arms.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The end tick of one running item cooldown (a package-private record); see {@link ItemCooldownsAccessor}. */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
	@Accessor("endTime")
	int ashfall$endTime();
}
