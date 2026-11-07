package xyz.toesinthesand.ashfall.arms.mixin;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.arms.item.MobDrops;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Reads the mob drop rules at the start of every loot reload, before any loot table is built, so
 * {@link MobDrops}' {@code LootTableEvents.MODIFY} hook sees this reload's rules (reload listeners run only after
 * the loot tables are done).
 */
@Mixin(ReloadableServerRegistries.class)
abstract class LootReloadMixin {
	@Inject(method = "reload", at = @At("HEAD"))
	private static void ashfall$loadMobDrops(LayeredRegistryAccess<RegistryLayer> registries, List<Registry.PendingTags<?>> tags,
											 ResourceManager manager, Executor executor, CallbackInfoReturnable<?> cir) {
		MobDrops.load(manager);
	}
}
