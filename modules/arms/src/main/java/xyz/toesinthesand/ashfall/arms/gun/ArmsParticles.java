package xyz.toesinthesand.ashfall.arms.gun;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Particle types for energy weapons. Registered on both sides so the server can send the impact
 * emitters; the client registers the providers (client {@code BoltParticles}) and the sprite lists in
 * {@code assets/ashfall/particles/}, which reuse vanilla particle textures (spark, flash, big smoke).
 * <p>
 * Trail sparks and muzzle flashes are spawned client-side only, from the bolt itself, so a shot costs
 * the server nothing beyond the bolt entity; an impact costs one particle packet (an emitter that
 * expands into sparks, smoke and a scorch mark on the client).
 */
public final class ArmsParticles {
	/** A small hot spark, tinted, full-bright, falling and shrinking. Speed is taken as given. */
	public static final ParticleType<ColorParticleOption> BOLT_SPARK = color("bolt_spark");
	/** A soft round flash of light, tinted, two or three ticks long; the speed's x is its size in blocks. */
	public static final ParticleType<ColorParticleOption> BOLT_FLASH = color("bolt_flash");
	/**
	 * Emitter for a bolt hitting a block. Send it with count 0 and the hit face's normal as the
	 * direction, scaled by the impact size (see {@link BoltStyle#impactScale()}); the client turns it into
	 * a flash, coloured and orange sparks, a wisp of smoke and a scorch mark.
	 */
	public static final ParticleType<ColorParticleOption> BOLT_IMPACT = color("bolt_impact");
	/** Emitter for a bolt hitting an entity: like {@link #BOLT_IMPACT} without smoke or scorch. */
	public static final ParticleType<ColorParticleOption> BOLT_HIT = color("bolt_hit");
	/** A dark scorch mark lying flat on a surface; the speed vector is the surface normal. */
	public static final SimpleParticleType SCORCH = register("scorch", FabricParticleTypes.simple(false));

	private ArmsParticles() {
	}

	private static ParticleType<ColorParticleOption> color(String path) {
		return register(path, FabricParticleTypes.complex(false, ColorParticleOption::codec, ColorParticleOption::streamCodec));
	}

	private static <T extends ParticleType<?>> T register(String path, T type) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("ashfall", path), type);
	}

	/** Forces class loading, and so registration. */
	public static void init() {
	}
}
