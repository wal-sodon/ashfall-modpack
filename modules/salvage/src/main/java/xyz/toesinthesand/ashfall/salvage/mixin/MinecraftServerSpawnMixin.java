package xyz.toesinthesand.ashfall.salvage.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.toesinthesand.ashfall.salvage.city.CitySpawn;

/**
 * Hooks the end of vanilla's new-world spawn selection: it runs exactly once per world, at creation,
 * which is the moment to move the spawn into Montford (see {@link CitySpawn}). Fabric has no event for
 * it. If Lost Cities handled the spawn itself it cancels at HEAD and we stay out of the way.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSpawnMixin {
	@Inject(method = "setInitialSpawn", at = @At("TAIL"))
	private static void ashfall$citySpawn(ServerLevel level, ServerLevelData levelData, boolean spawnBonusChest,
			boolean isDebug, LevelLoadListener listener, CallbackInfo ci) {
		if (!isDebug) {
			CitySpawn.onInitialSpawn(level, levelData);
		}
	}
}
