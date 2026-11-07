package xyz.toesinthesand.ashfall.arms.gun;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events for guns, bolts and chargers. {@code assets/ashfall/sounds.json} maps each one onto
 * several variations of the CC0 Kenney Sci-Fi Sounds shipped under {@code assets/ashfall/sounds/}
 * (mono, so they are positional), each with its own pitch and volume; resource packs can replace any
 * of them.
 */
public final class ArmsSounds {
	public static final SoundEvent GUN_FIRE_LIGHT = register("gun.fire.light");
	public static final SoundEvent GUN_FIRE_HEAVY = register("gun.fire.heavy");
	public static final SoundEvent GUN_FIRE_AUTO = register("gun.fire.auto");
	/** Per gun family: the guns use these (see {@link EnergyGunItem.FireSound}); the three above stay as generic shots. */
	public static final SoundEvent GUN_FIRE_PISTOL = register("gun.fire.pistol");
	public static final SoundEvent GUN_FIRE_MAGNUM = register("gun.fire.magnum");
	public static final SoundEvent GUN_FIRE_CARBINE = register("gun.fire.carbine");
	public static final SoundEvent GUN_FIRE_RIFLE = register("gun.fire.rifle");
	public static final SoundEvent GUN_FIRE_LANCER = register("gun.fire.lancer");
	public static final SoundEvent GUN_FIRE_STORM = register("gun.fire.storm");
	public static final SoundEvent GUN_OVERHEAT = register("gun.overheat");
	public static final SoundEvent GUN_EMPTY = register("gun.empty");
	public static final SoundEvent GUN_RELOAD = register("gun.reload");
	/** A bolt striking a block (or fizzling in water). */
	public static final SoundEvent BOLT_IMPACT = register("bolt.impact");
	/** A bolt striking an entity. */
	public static final SoundEvent BOLT_HIT = register("bolt.hit");
	/** For the chargers (charger package): the Field Charger's quiet hum while charging. */
	public static final SoundEvent CHARGER_HUM = register("charger.hum");
	/** For the chargers (charger package): the Power Relay's loud hum that draws zombies. */
	public static final SoundEvent RELAY_HUM = register("relay.hum");

	private ArmsSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("ashfall", path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading, and so registration. */
	public static void init() {
	}
}
