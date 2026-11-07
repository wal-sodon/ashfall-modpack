package xyz.toesinthesand.ashfall.arms.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.gun.ArmsParticles;
import xyz.toesinthesand.ashfall.arms.gun.BoltStyle;

/**
 * Client-side bolt effects: the muzzle flash and muzzle offset when a bolt leaves the gun, the spark
 * trail behind it, and the impact burst the server's emitter particle expands into. Nothing here is
 * sent over the network; the server's only cost per shot is the bolt entity, and one particle packet
 * per impact.
 * <p>
 * The bolt really starts at the shooter's eyes (so it flies where the crosshair points). It is drawn
 * from the gun's muzzle instead: a sideways/downwards offset that fades to nothing over the first
 * {@link #OFFSET_FADE_DISTANCE} blocks, so the beam visibly leaves the barrel and converges on the aim.
 */
public final class BoltEffects implements EnergyBolt.ClientEffects {
	public static final BoltEffects INSTANCE = new BoltEffects();

	/** The beam and its trail stay this far clear of where the bolt started (inside the shooter's head). */
	public static final double MUZZLE_GAP = 0.8;
	/** Distance over which the muzzle offset fades out. */
	public static final double OFFSET_FADE_DISTANCE = 8.0;
	/** Trail sparks closer than this to the camera are skipped (no sparks in your face). */
	private static final double CAMERA_CLEARANCE = 1.0;
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private static final int[] EMBER_COLORS = {0xFFB040, 0xFF8A20, 0xFFD070, 0xFF6A10};

	private BoltEffects() {
	}

	/** How much of the muzzle offset is left after {@code travelled} blocks of flight (1 at the muzzle, 0 later). */
	public static double offsetFade(double travelled) {
		return Mth.clamp(1.0 - travelled / OFFSET_FADE_DISTANCE, 0.0, 1.0);
	}

	/** Mixes an 0xRRGGBB colour towards white by {@code t} (0 = unchanged, 1 = white). */
	public static int towardWhite(int rgb, float t) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r += Math.round((255 - r) * t);
		g += Math.round((255 - g) * t);
		b += Math.round((255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static ParticleStatus particleStatus() {
		return Minecraft.getInstance().options.particles().get();
	}

	private static ColorParticleOption color(net.minecraft.core.particles.ParticleType<ColorParticleOption> type, int rgb, float alpha) {
		return ColorParticleOption.create(type, (Math.round(alpha * 255f) << 24) | (rgb & 0xFFFFFF));
	}

	private static @Nullable Particle spawn(ParticleOptions options, Vec3 at, Vec3 velocity) {
		return Minecraft.getInstance().particleEngine.createParticle(options, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
	}

	private static void spark(int rgb, Vec3 at, Vec3 velocity, int lifetime, float gravity, float friction, float size, boolean physics) {
		BoltParticles.Spark spark = BoltParticles.asSpark(spawn(color(ArmsParticles.BOLT_SPARK, rgb, 1f), at, velocity));
		if (spark != null) {
			spark.tune(lifetime, gravity, friction, size, physics);
		}
	}

	/** A random unit vector. */
	private static Vec3 randomUnit(RandomSource random) {
		double z = random.nextDouble() * 2 - 1;
		double a = random.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(1 - z * z);
		return new Vec3(r * Math.cos(a), r * Math.sin(a), z);
	}

	/** A random direction in the hemisphere around {@code normal}, biased towards it by {@code bias} (0..1). */
	private static Vec3 hemisphere(Vec3 normal, double bias, RandomSource random) {
		Vec3 d = randomUnit(random);
		if (d.dot(normal) < 0) {
			d = d.scale(-1);
		}
		return d.scale(1 - bias).add(normal.scale(bias)).normalize();
	}

	/** The shooter's right, from its yaw (works at any pitch, unlike a cross product with the aim). */
	private static Vec3 rightOf(Entity owner) {
		float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/** +1 if the gun is in the right hand, -1 if in the left. */
	private static int handSide(Entity owner) {
		if (!(owner instanceof LivingEntity living)) {
			return 1;
		}
		boolean rightMain = living.getMainArm() == HumanoidArm.RIGHT;
		boolean offhand = living.isUsingItem() && living.getUsedItemHand() == InteractionHand.OFF_HAND;
		return rightMain != offhand ? 1 : -1;
	}

	private static boolean isFirstPersonShooter(Entity owner) {
		Minecraft mc = Minecraft.getInstance();
		return owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
	}

	@Override
	public void onFirstTick(EnergyBolt bolt) {
		Vec3 origin = bolt.visualOrigin();
		Entity owner = bolt.getOwner();
		Vec3 velocity = bolt.getDeltaMovement();
		if (!bolt.isFreshShot() || origin == null || owner == null || velocity.lengthSqr() < 1e-8) {
			return;
		}
		Vec3 dir = velocity.normalize();
		boolean firstPerson = isFirstPersonShooter(owner);
		int side = handSide(owner);
		// First person: the gun sits low and to the side of the view; third person: at the hand.
		Vec3 offset = rightOf(owner).scale(side * (firstPerson ? 0.26 : 0.36)).add(UP.scale(firstPerson ? -0.14 : -0.42));
		bolt.setMuzzleOffset(offset);

		ParticleStatus status = particleStatus();
		BoltStyle style = bolt.getStyle();
		int rgb = bolt.getColor();
		Vec3 muzzle = origin.add(offset).add(dir.scale(firstPerson ? 0.75 : 0.9)).add(0, EnergyBolt.VISUAL_CENTRE_LIFT, 0);
		float flash = style.flashSize() * (firstPerson ? 0.55f : 1f);
		// A white-hot core flash and a wider, fainter coloured halo.
		spawn(color(ArmsParticles.BOLT_FLASH, towardWhite(rgb, 0.6f), 0.95f), muzzle, new Vec3(flash, 0, 0));
		spawn(color(ArmsParticles.BOLT_FLASH, rgb, 0.45f), muzzle, new Vec3(flash * 1.9f, 0, 0));
		if (status == ParticleStatus.MINIMAL) {
			return;
		}
		RandomSource random = bolt.level().getRandom();
		int sparks = Math.round((firstPerson ? 3 : 5) * style.impactScale());
		if (status == ParticleStatus.DECREASED) {
			sparks = Math.max(1, sparks / 2);
		}
		int sparkColor = towardWhite(rgb, 0.35f);
		for (int i = 0; i < sparks; i++) {
			Vec3 v = dir.scale(0.12 + random.nextDouble() * 0.2).add(randomUnit(random).scale(0.06));
			spark(sparkColor, muzzle, v, 3 + random.nextInt(3), 0.1f, 0.8f, 0.03f + random.nextFloat() * 0.015f, false);
		}
	}

	@Override
	public void onTick(EnergyBolt bolt, Vec3 from, Vec3 to) {
		ParticleStatus status = particleStatus();
		if (status == ParticleStatus.MINIMAL) {
			return;
		}
		Vec3 path = to.subtract(from);
		double length = path.length();
		if (length < 1e-4) {
			return;
		}
		Vec3 dir = path.scale(1 / length);
		BoltStyle style = bolt.getStyle();
		int count = status == ParticleStatus.DECREASED ? Math.max(1, style.trailSparks() / 2) : style.trailSparks();
		Vec3 origin = bolt.visualOrigin();
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		RandomSource random = bolt.level().getRandom();
		int rgb = towardWhite(bolt.getColor(), 0.25f);
		for (int i = 0; i < count; i++) {
			Vec3 p = from.add(path.scale(random.nextDouble()));
			double travelled = origin == null ? Double.MAX_VALUE : p.subtract(origin).dot(dir);
			if (travelled < MUZZLE_GAP + 0.4) {
				continue;
			}
			p = p.add(bolt.muzzleOffset().scale(offsetFade(travelled))).add(0, EnergyBolt.VISUAL_CENTRE_LIFT, 0);
			if (p.distanceToSqr(camera) < CAMERA_CLEARANCE * CAMERA_CLEARANCE) {
				continue;
			}
			Vec3 v = randomUnit(random).scale(0.015).add(dir.scale(0.02));
			spark(rgb, p, v, 4 + random.nextInt(4), 0.05f, 0.9f, 0.022f + random.nextFloat() * 0.018f, false);
		}
	}

	/**
	 * Expands an impact emitter: {@code (xd, yd, zd)} is the surface normal scaled by the impact size.
	 * Blocks get a flash, coloured sparks plus orange embers that bounce, a wisp of smoke and a scorch
	 * mark; entities get the flash and a smaller spark burst.
	 */
	static void impact(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
					   ColorParticleOption options, boolean entity) {
		Vec3 raw = new Vec3(xd, yd, zd);
		double scaleD = raw.length();
		Vec3 normal = scaleD < 1e-6 ? UP : raw.scale(1 / scaleD);
		float scale = (float) Mth.clamp(scaleD, 0.3, 3.0);
		int rgb = (Math.round(options.getRed() * 255) << 16) | (Math.round(options.getGreen() * 255) << 8) | Math.round(options.getBlue() * 255);
		Vec3 at = new Vec3(x, y, z);
		RandomSource random = level.getRandom();
		ParticleStatus status = particleStatus();

		spawn(color(ArmsParticles.BOLT_FLASH, towardWhite(rgb, 0.5f), 0.9f), at.add(normal.scale(0.06)),
				new Vec3((entity ? 0.35f : 0.45f) * scale, 0, 0));
		if (!entity) {
			level.addParticle(ArmsParticles.SCORCH, at.x + normal.x * 0.012, at.y + normal.y * 0.012, at.z + normal.z * 0.012,
					normal.x, normal.y, normal.z);
		}
		if (status == ParticleStatus.MINIMAL) {
			return;
		}
		int sparks = Math.round((entity ? 7 : 9) * scale);
		int embers = Math.round((entity ? 3 : 7) * scale);
		if (status == ParticleStatus.DECREASED) {
			sparks = Math.max(2, sparks / 2);
			embers = Math.max(1, embers / 2);
		}
		Vec3 start = at.add(normal.scale(0.05));
		int sparkColor = towardWhite(rgb, 0.3f);
		for (int i = 0; i < sparks; i++) {
			Vec3 v = hemisphere(normal, 0.35, random).scale(0.12 + random.nextDouble() * 0.28);
			spark(sparkColor, start, v, 6 + random.nextInt(7), 0.55f, 0.86f, 0.04f + random.nextFloat() * 0.03f, true);
		}
		for (int i = 0; i < embers; i++) {
			Vec3 v = hemisphere(normal, 0.25, random).scale(0.1 + random.nextDouble() * 0.3);
			int ember = EMBER_COLORS[random.nextInt(EMBER_COLORS.length)];
			spark(ember, start, v, 10 + random.nextInt(12), 0.8f, 0.9f, 0.03f + random.nextFloat() * 0.025f, true);
		}
		if (!entity) {
			int smoke = status == ParticleStatus.DECREASED ? 1 : 2 + random.nextInt(2);
			for (int i = 0; i < smoke; i++) {
				Vec3 drift = normal.scale(0.02 + random.nextDouble() * 0.02).add(0, 0.015, 0);
				level.addParticle(ParticleTypes.SMOKE, start.x, start.y, start.z, drift.x, drift.y, drift.z);
			}
		}
	}
}
