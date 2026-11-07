package xyz.toesinthesand.ashfall.director.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates {@code ashfall_director.zbb.mixins.json} on Zombies Break and Build being installed. ZB&B is a
 * compile-only dependency the pack supplies; without it the target classes don't exist, and this plugin
 * keeps Mixin from even trying (a missing target would otherwise be an error or a log warning).
 * <p>
 * Lives outside the mixin package because Mixin refuses to load ordinary classes from it.
 */
public final class ZbbMixinPlugin implements IMixinConfigPlugin {
	public static final String ZBB_MOD_ID = "zbb";

	private boolean zbbLoaded;

	@Override
	public void onLoad(String mixinPackage) {
		zbbLoaded = FabricLoader.getInstance().isModLoaded(ZBB_MOD_ID);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return zbbLoaded;
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
