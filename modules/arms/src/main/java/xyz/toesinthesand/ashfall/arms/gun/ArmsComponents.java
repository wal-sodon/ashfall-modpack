package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStackTemplate;
import xyz.toesinthesand.ashfall.arms.blade.BladeCrystal;

/**
 * Item data components for energy guns, the flamethrower and Plasma Blades. All are persistent (saved with the item) and synchronised
 * (the HUD and tooltips read them client-side).
 */
public final class ArmsComponents {
	/**
	 * Heat, last shot tick, lockout and energy credit. Ignores the swap animation so firing (which
	 * rewrites this every shot) never makes the held gun bob.
	 */
	public static final DataComponentType<GunState> GUN_STATE = register("gun_state",
			DataComponentType.<GunState>builder().persistent(GunState.CODEC).networkSynchronized(GunState.STREAM_CODEC)
					.ignoreSwapAnimation().build());

	/** The Cell Pack currently in the gun, as an item template that carries the pack's stored energy. */
	public static final DataComponentType<ItemStackTemplate> LOADED_PACK = register("loaded_pack",
			DataComponentType.<ItemStackTemplate>builder().persistent(ItemStackTemplate.CODEC)
					.networkSynchronized(ItemStackTemplate.STREAM_CODEC).ignoreSwapAnimation().build());

	/** A Plasma Blade's socketed Resonance Crystal: its wear is the blade's durability. */
	public static final DataComponentType<BladeCrystal> BLADE_CRYSTAL = register("blade_crystal",
			DataComponentType.<BladeCrystal>builder().persistent(BladeCrystal.CODEC).networkSynchronized(BladeCrystal.STREAM_CODEC)
					.ignoreSwapAnimation().build());

	/**
	 * Present while a Plasma Blade can cut (crystal left, charge for a swing). A plain marker so data can
	 * react without code: the item model's beam, LambDynamicLights' light, the Director's light sense.
	 */
	public static final DataComponentType<Unit> BLADE_LIT = register("blade_lit",
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());

	/** Fitted attachments, e.g. the Dampener. */
	public static final DataComponentType<GunAttachments> ATTACHMENTS = register("attachments",
			DataComponentType.<GunAttachments>builder().persistent(GunAttachments.CODEC)
					.networkSynchronized(GunAttachments.STREAM_CODEC).build());

	/**
	 * Damage or stun, on blasters that have a stun mode. Absent means {@link FireMode#DAMAGE}. Ignores the swap
	 * animation so switching modes doesn't bob the held gun.
	 */
	public static final DataComponentType<FireMode> FIRE_MODE = register("fire_mode",
			DataComponentType.<FireMode>builder().persistent(FireMode.CODEC).networkSynchronized(FireMode.STREAM_CODEC)
					.ignoreSwapAnimation().build());

	/**
	 * A flamethrower's tank: ticks of fire left ({@link FlamethrowerLogic#TANK_TICKS} when full). Ignores the swap
	 * animation, since firing rewrites it every tick.
	 */
	public static final DataComponentType<Integer> FUEL = register("fuel",
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT)
					.ignoreSwapAnimation().build());

	private ArmsComponents() {
	}

	private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("ashfall", path), type);
	}

	/** Forces class loading, and so registration. */
	public static void init() {
	}
}
