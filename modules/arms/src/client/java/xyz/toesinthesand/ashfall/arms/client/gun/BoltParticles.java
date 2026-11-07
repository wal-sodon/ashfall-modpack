package xyz.toesinthesand.ashfall.arms.client.gun;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import xyz.toesinthesand.ashfall.arms.gun.ArmsParticles;

/**
 * Client particles for energy bolts. The sprites are vanilla particle textures listed in
 * {@code assets/ashfall/particles/*.json} (firework spark frames, the firework flash, big smoke), tinted
 * per bolt; sparks and flashes are full-bright so they read as light at night. The two impact emitters
 * have no sprite: they expand straight into sparks, smoke and a scorch mark (see {@link BoltEffects}).
 */
public final class BoltParticles {
	private BoltParticles() {
	}

	public static void register() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(ArmsParticles.BOLT_SPARK, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
				new Spark(level, x, y, z, xd, yd, zd, sprites, options, random));
		registry.register(ArmsParticles.BOLT_FLASH, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
				new Flash(level, x, y, z, (float) xd, sprites.get(random), options));
		registry.register(ArmsParticles.SCORCH, sprites -> (SimpleParticleType options, ClientLevel level, double x, double y,
				double z, double xd, double yd, double zd, RandomSource random) ->
				new Scorch(level, x, y, z, (float) xd, (float) yd, (float) zd, sprites.get(random), random));
		registry.register(ArmsParticles.BOLT_IMPACT, (options, level, x, y, z, xd, yd, zd, random) -> {
			BoltEffects.impact(level, x, y, z, xd, yd, zd, options, false);
			return null;
		});
		registry.register(ArmsParticles.BOLT_HIT, (options, level, x, y, z, xd, yd, zd, random) -> {
			BoltEffects.impact(level, x, y, z, xd, yd, zd, options, true);
			return null;
		});
	}

	/**
	 * A hot spark: flies at the given speed with drag and a little gravity, bounces off blocks, shrinks
	 * through the firework spark frames and fades out. Defaults suit an impact; callers tune lifetime,
	 * gravity and size through the setters for trails and muzzle sparks.
	 */
	public static final class Spark extends SingleQuadParticle {
		private final SpriteSet sprites;
		private float startSize;

		Spark(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
			  ColorParticleOption color, RandomSource random) {
			super(level, x, y, z, sprites.first());
			this.sprites = sprites;
			this.xd = xd;
			this.yd = yd;
			this.zd = zd;
			this.friction = 0.86f;
			this.gravity = 0.6f;
			this.hasPhysics = true;
			this.lifetime = 7 + random.nextInt(8);
			this.startSize = 0.045f + random.nextFloat() * 0.03f;
			this.quadSize = startSize;
			setColor(color.getRed(), color.getGreen(), color.getBlue());
			setAlpha(color.getAlpha());
			setSpriteFromAge(sprites);
		}

		public Spark tune(int lifetime, float gravity, float friction, float size, boolean physics) {
			this.lifetime = lifetime;
			this.gravity = gravity;
			this.friction = friction;
			this.startSize = size;
			this.quadSize = size;
			this.hasPhysics = physics;
			setSpriteFromAge(sprites);
			return this;
		}

		@Override
		public void tick() {
			super.tick();
			if (!removed) {
				setSpriteFromAge(sprites);
				float life = (float) age / lifetime;
				alpha = 1f - life * life;
			}
		}

		@Override
		public float getQuadSize(float partialTick) {
			float life = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
			return startSize * (1f - 0.6f * life);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
	}

	/** A soft round flash (the firework flash sprite, small): bright for a tick, gone in two or three. */
	public static final class Flash extends SingleQuadParticle {
		private final float size;
		private final float startAlpha;

		Flash(ClientLevel level, double x, double y, double z, float size, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
			  ColorParticleOption color) {
			super(level, x, y, z, sprite);
			this.size = size > 0 ? size : 0.4f;
			this.lifetime = 3;
			this.hasPhysics = false;
			this.gravity = 0f;
			setColor(color.getRed(), color.getGreen(), color.getBlue());
			this.startAlpha = color.getAlpha();
			setAlpha(startAlpha);
		}

		@Override
		public float getQuadSize(float partialTick) {
			float t = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
			// Quad size is a half-extent (the quad spans -size..+size); the flash size is a diameter in blocks.
			return 0.5f * size * (0.7f + 0.5f * t);
		}

		@Override
		public void tick() {
			super.tick();
			alpha = startAlpha * Math.max(0f, 1f - (float) age / lifetime);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
	}

	/**
	 * A dark scorch mark lying flat on the surface it was spawned against (the speed vector is the
	 * surface normal), randomly turned, fading over its last second. Lit by the world like a decal.
	 */
	public static final class Scorch extends SingleQuadParticle {
		private static final int FADE_TICKS = 20;
		private final Quaternionf facing;

		Scorch(ClientLevel level, double x, double y, double z, float nx, float ny, float nz,
			   net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, RandomSource random) {
			super(level, x, y, z, sprite);
			float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-4f) {
				nx = 0f;
				ny = 1f;
				nz = 0f;
				len = 1f;
			}
			// The quad faces local +Z; turn that onto the normal, then spin it about the normal.
			this.facing = new Quaternionf().rotationTo(0f, 0f, 1f, nx / len, ny / len, nz / len)
					.rotateZ(random.nextFloat() * Mth.TWO_PI);
			this.lifetime = 100 + random.nextInt(40);
			this.quadSize = 0.14f + random.nextFloat() * 0.06f;
			this.hasPhysics = false;
			this.gravity = 0f;
			setColor(0.07f, 0.06f, 0.055f);
			setAlpha(0.85f);
		}

		@Override
		public FacingCameraMode getFacingCameraMode() {
			return (target, camera, partialTick) -> target.set(facing);
		}

		@Override
		public void tick() {
			super.tick();
			int left = lifetime - age;
			if (left < FADE_TICKS) {
				alpha = 0.85f * Math.max(0, left) / FADE_TICKS;
			}
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}
	}

	/** Whether {@code p} is one of ours that can be tuned (a provider may return null when particles are off). */
	static Spark asSpark(Particle p) {
		return p instanceof Spark spark ? spark : null;
	}
}
