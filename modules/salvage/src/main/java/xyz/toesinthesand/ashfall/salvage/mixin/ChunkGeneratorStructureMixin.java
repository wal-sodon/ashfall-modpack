package xyz.toesinthesand.ashfall.salvage.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.toesinthesand.ashfall.salvage.city.CityZoneRules;

/**
 * Keeps villages (and the other structures Lost Cities avoids) from starting in or near the four districts.
 * <p>
 * Lost Cities' {@code avoidVillages} drops the city wherever a village stands, and for a multibuilding it drops
 * the whole footprint. On seed {@code ashfall-p3} a village at the origin removed the Downtown substation, so
 * Downtown could never be relit (Phase 3 playtest). Turning avoidance off instead would pile village houses onto
 * city blocks. Vetoing the structure start keeps both: no village in a district, and Lost Cities still avoids
 * villages in the suburbs and wasteland as before. Fabric has no per-position structure veto, hence the mixin.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorStructureMixin {
	@Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
	private void ashfall$keepOutOfDistricts(StructureSet.StructureSelectionEntry entry, StructureManager structureManager,
			RegistryAccess registryAccess, RandomState randomState, StructureTemplateManager templateManager, long seed,
			ChunkAccess chunk, ChunkPos pos, ResourceKey<Level> dimension, Climate.Sampler sampler,
			CallbackInfoReturnable<Boolean> cir) {
		if (dimension == Level.OVERWORLD && entry.structure().is(CityZoneRules.KEPT_OUT_OF_DISTRICTS)
				&& CityZoneRules.nearDistrict(pos.x(), pos.z())) {
			cir.setReturnValue(false);
		}
	}
}
