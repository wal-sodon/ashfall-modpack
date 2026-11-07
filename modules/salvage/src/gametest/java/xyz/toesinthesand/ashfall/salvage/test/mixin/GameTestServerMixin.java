package xyz.toesinthesand.ashfall.salvage.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Test-only: lets the gametest server load dimensions defined in data packs. Vanilla's
 * {@code GameTestServer} bakes its flat preset against an empty level-stem registry, so a data-pack
 * dimension never exists there. We need one ({@code ashfall_test:lc_city}) to run Lost Cities on real
 * terrain without touching the flat overworld the other tests build in. Preset dimensions still win, so
 * the overworld, nether and end stay flat.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
	@WrapOperation(method = "lambda$create$1", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
	private static WorldDimensions.Complete ashfallTest$datapackDimensions(WorldDimensions dimensions, Registry<LevelStem> empty,
			Operation<WorldDimensions.Complete> original, LevelSettings settings, WorldLoader.DataLoadContext context) {
		return original.call(dimensions, context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
	}
}
