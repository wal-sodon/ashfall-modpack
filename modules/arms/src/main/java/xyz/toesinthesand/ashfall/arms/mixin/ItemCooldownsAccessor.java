package xyz.toesinthesand.ashfall.arms.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Read access to a player's running cooldowns, so a gun's draw time can tell how long the current cooldown still
 * has to go and never shorten it ({@link xyz.toesinthesand.ashfall.arms.gun.DrawTime}). The values are the private
 * {@code CooldownInstance} records; read their end tick through {@link CooldownInstanceAccessor}.
 */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
	@Accessor("cooldowns")
	Map<Identifier, ?> ashfall$cooldowns();

	@Accessor("tickCount")
	int ashfall$tickCount();
}
