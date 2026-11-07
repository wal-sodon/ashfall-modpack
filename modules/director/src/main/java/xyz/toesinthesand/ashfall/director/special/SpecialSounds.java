package xyz.toesinthesand.ashfall.director.special;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sound events of the special infected. No new audio ships: {@code assets/ashfall/sounds.json} (director's copy)
 * points every event at vanilla sound events, re-pitched (a deep brute, a wheezing bloated, a shrill screamer), so
 * each special is recognisable by ear and a resource pack can still swap any of them.
 */
public final class SpecialSounds {
	private static final Map<SpecialKind, SoundEvent> AMBIENT = new EnumMap<>(SpecialKind.class);
	private static final Map<SpecialKind, SoundEvent> HURT = new EnumMap<>(SpecialKind.class);
	private static final Map<SpecialKind, SoundEvent> DEATH = new EnumMap<>(SpecialKind.class);

	static {
		for (SpecialKind kind : SpecialKind.values()) {
			AMBIENT.put(kind, register("entity." + kind.id() + ".ambient"));
			HURT.put(kind, register("entity." + kind.id() + ".hurt"));
			DEATH.put(kind, register("entity." + kind.id() + ".death"));
		}
	}

	/** The screamer's shriek: the loudest thing a zombie makes. */
	public static final SoundEvent SCREAM = register("entity.screamer.scream");
	/** The spitter's spit. */
	public static final SoundEvent SPIT = register("entity.spitter.spit");
	/** The bloated bursting. */
	public static final SoundEvent BURST = register("entity.bloated.burst");
	/** The stalker dropping its cover right before it strikes. */
	public static final SoundEvent AMBUSH = register("entity.stalker.ambush");

	private SpecialSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("ashfall", path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static SoundEvent ambient(SpecialKind kind) {
		return AMBIENT.get(kind);
	}

	public static SoundEvent hurt(SpecialKind kind) {
		return HURT.get(kind);
	}

	public static SoundEvent death(SpecialKind kind) {
		return DEATH.get(kind);
	}

	/** Forces class loading, and so registration. */
	static void init() {
	}
}
