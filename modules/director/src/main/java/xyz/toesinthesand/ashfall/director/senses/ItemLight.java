package xyz.toesinthesand.ashfall.director.senses;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import xyz.toesinthesand.ashfall.director.AshfallDirector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;

/**
 * How brightly a held item shines, 0..15.
 * <p>
 * LambDynamicLights decides what players actually see glowing, so when it is installed (single player:
 * it is client-only, but the integrated server shares the JVM) its item table is the truth, data driven
 * items like the Plasma Blade included. Without it (every dedicated server) the fallback is the block
 * the item places: a torch item is as bright as a torch block.
 * <p>
 * LambDynamicLights is reached by reflection through
 * {@code LambDynLights.INSTANCE.itemLightSourceManager().getLuminance(ItemStack)} (verified against
 * 4.13.0+26.3) so it never becomes a compile dependency; any failure falls back for good, warned once.
 * <p>
 * Higher modules add their own glowing items through {@link #addSource}: a lit Plasma Blade (arms) must
 * draw zombies on a dedicated server too, where LambDynamicLights is absent, and director can't see arms.
 */
public final class ItemLight {
	private static final String LDL_ID = "lambdynlights";
	private static MethodHandle ldlLuminance;
	private static boolean ldlResolved;
	private static final List<ToIntFunction<ItemStack>> SOURCES = new CopyOnWriteArrayList<>();

	private ItemLight() {
	}

	/**
	 * Registers an extra light rule for held items, e.g. "a lit Plasma Blade shines at 12". The brightest
	 * answer of LambDynamicLights (or the block fallback) and every source wins.
	 */
	public static void addSource(ToIntFunction<ItemStack> source) {
		SOURCES.add(source);
	}

	/** Light level of {@code stack} when held, 0 for an empty or dark item. */
	public static int luminance(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		int light = baseLuminance(stack);
		for (ToIntFunction<ItemStack> source : SOURCES) {
			light = Math.max(light, Math.clamp(source.applyAsInt(stack), 0, 15));
		}
		return light;
	}

	private static int baseLuminance(ItemStack stack) {
		MethodHandle ldl = lambDynamicLights();
		if (ldl != null) {
			try {
				int value = (int) ldl.invokeExact(stack);
				return Math.clamp(value, 0, 15);
			} catch (Throwable t) {
				disableLdl(t);
			}
		}
		return blockLuminance(stack);
	}

	/** The vanilla fallback: the light emission of the block this item places, 0 for other items. */
	public static int blockLuminance(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState().getLightEmission();
		}
		return 0;
	}

	private static MethodHandle lambDynamicLights() {
		if (!ldlResolved) {
			ldlResolved = true;
			if (FabricLoader.getInstance().isModLoaded(LDL_ID)) {
				try {
					Class<?> main = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
					Class<?> manager = Class.forName("dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager");
					Object instance = main.getField("INSTANCE").get(null);
					MethodHandles.Lookup lookup = MethodHandles.publicLookup();
					MethodHandle getManager = lookup.findVirtual(main, "itemLightSourceManager", MethodType.methodType(manager));
					MethodHandle getLuminance = lookup.findVirtual(manager, "getLuminance", MethodType.methodType(int.class, ItemStack.class));
					Object managerInstance = getManager.invoke(instance);
					ldlLuminance = getLuminance.bindTo(managerInstance).asType(MethodType.methodType(int.class, ItemStack.class));
				} catch (Throwable t) {
					disableLdl(t);
				}
			}
		}
		return ldlLuminance;
	}

	private static void disableLdl(Throwable t) {
		ldlLuminance = null;
		AshfallDirector.LOGGER.warn("Could not read LambDynamicLights item light; using block light emission instead", t);
	}
}
