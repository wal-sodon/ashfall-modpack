package xyz.toesinthesand.ashfall.salvage.city;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates {@code ashfall_salvage.lostcities.mixins.json} on The Lost Cities being installed. Lost Cities is
 * compile-only for us; without it the targets do not exist and Mixin would fail or warn. Lives outside
 * the mixin package because Mixin refuses to load ordinary classes from it.
 */
public final class LostCitiesMixinPlugin implements IMixinConfigPlugin {
	private boolean loaded;

	@Override
	public void onLoad(String mixinPackage) {
		loaded = FabricLoader.getInstance().isModLoaded(LostCityContext.MOD_ID);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return loaded;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
