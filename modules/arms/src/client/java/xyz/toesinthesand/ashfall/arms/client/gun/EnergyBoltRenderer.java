package xyz.toesinthesand.ashfall.arms.client.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.gun.BoltStyle;

/**
 * Draws an energy bolt as a beam of light: a near-white core inside a wider, softer glow in the gun's
 * colour, with a pointed head, a fading tail and a small bloom at the head. Length and thickness come
 * from the bolt's {@link BoltStyle}.
 * <p>
 * Rendering uses the lightning render type: untextured, additive ({@code SRC_ALPHA, ONE}) and unlit, so
 * the beam is always full-bright and overlapping layers add up to white-hot. Each layer is a ribbon
 * turned to face the camera about the flight axis, with alpha falling to zero at its edges, so the beam
 * looks round and soft from any side.
 * <p>
 * The beam is never drawn behind where the bolt started (that is inside the shooter's head), nor when
 * any part of it passes within {@link #CAMERA_CLEARANCE} of the camera: a quad that close covers half
 * the screen, which used to show up as a coloured rectangle flashing at the edge of the view on firing.
 */
public class EnergyBoltRenderer extends EntityRenderer<EnergyBolt, EnergyBoltRenderState> {
	private static final RenderType RENDER_TYPE = RenderTypes.lightning();
	/** Beams passing closer than this to the camera are not drawn. */
	private static final float CAMERA_CLEARANCE = 0.5f;
	/** Shortest beam worth drawing. */
	private static final float MIN_LENGTH = 0.05f;
	/** Fraction of the beam, from the head, drawn at full strength before the tail fades. */
	private static final float BODY_FRACTION = 0.3f;
	private static final float GLOW_ALPHA = 0.55f;
	private static final float CORE_ALPHA = 1.0f;
	private static final float BLOOM_ALPHA = 0.45f;
	private static final float BLOOM_SCALE = 2.4f;

	public EnergyBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EnergyBoltRenderState createRenderState() {
		return new EnergyBoltRenderState();
	}

	@Override
	public void extractRenderState(EnergyBolt bolt, EnergyBoltRenderState state, float partialTick) {
		super.extractRenderState(bolt, state, partialTick);
		state.visible = false;
		Vec3 velocity = bolt.getDeltaMovement();
		if (velocity.lengthSqr() < 1e-8) {
			return;
		}
		Vec3 dir = velocity.normalize();
		BoltStyle style = bolt.getStyle();
		Vec3 origin = bolt.visualOrigin();
		double travelled = origin == null ? Double.MAX_VALUE
				: new Vec3(state.x - origin.x, state.y - origin.y, state.z - origin.z).dot(dir);
		float length = (float) Math.min(style.length(), travelled - BoltEffects.MUZZLE_GAP);
		if (length < MIN_LENGTH) {
			return; // still in (or just out of) the muzzle
		}
		Vec3 offset = bolt.muzzleOffset().scale(BoltEffects.offsetFade(travelled));
		state.visible = true;
		state.color = bolt.getColor();
		state.headX = (float) offset.x;
		state.headY = (float) (offset.y + EnergyBolt.VISUAL_CENTRE_LIFT);
		state.headZ = (float) offset.z;
		state.dirX = (float) dir.x;
		state.dirY = (float) dir.y;
		state.dirZ = (float) dir.z;
		state.length = length;
		state.coreRadius = style.coreRadius();
		state.glowRadius = style.glowRadius();
	}

	/** Bolts are tiny boxes with a long beam behind them; culling by the box would clip visible tails. */
	@Override
	protected boolean affectedByCulling(EnergyBolt bolt) {
		return false;
	}

	@Override
	protected int getBlockLightLevel(EnergyBolt bolt, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(EnergyBoltRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.visible) {
			return;
		}
		Vector3f head = new Vector3f(state.headX, state.headY, state.headZ);
		Vector3f dir = new Vector3f(state.dirX, state.dirY, state.dirZ);
		Vector3f tail = new Vector3f(dir).mul(-state.length).add(head);
		Vector3f cam = new Vector3f((float) (camera.pos.x - state.x), (float) (camera.pos.y - state.y), (float) (camera.pos.z - state.z));
		if (distanceToSegment(cam, head, tail) < CAMERA_CLEARANCE) {
			return;
		}
		// Ribbon width axis: perpendicular to the flight and to the line of sight.
		Vector3f mid = new Vector3f(head).add(tail).mul(0.5f);
		Vector3f side = new Vector3f(dir).cross(new Vector3f(cam).sub(mid));
		if (side.lengthSquared() < 1e-8f) {
			// Looking straight down the beam: any perpendicular will do (the bloom covers it).
			side.set(dir).cross(Math.abs(dir.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0));
		}
		side.normalize();
		Vector3f bloomX = new Vector3f(1, 0, 0).rotate(camera.orientation);
		Vector3f bloomY = new Vector3f(0, 1, 0).rotate(camera.orientation);

		int rgb = state.color & 0xFFFFFF;
		int core = BoltEffects.towardWhite(rgb, 0.8f);
		float glowR = state.glowRadius;
		float coreR = state.coreRadius;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
			beam(pose, buffer, head, tail, dir, side, glowR, rgb, GLOW_ALPHA);
			beam(pose, buffer, head, tail, dir, side, coreR, core, CORE_ALPHA);
			bloom(pose, buffer, head, bloomX, bloomY, glowR * BLOOM_SCALE, rgb, BLOOM_ALPHA);
		});
	}

	/** One beam layer: a pointed tip ahead of the head, a full-strength body, then a tail fading to nothing. */
	private static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vector3f head, Vector3f tail, Vector3f dir,
							 Vector3f side, float radius, int rgb, float alpha) {
		Vector3f tip = new Vector3f(dir).mul(radius * 1.6f).add(head);
		Vector3f body = new Vector3f(tail).sub(head).mul(BODY_FRACTION).add(head);
		segment(pose, buffer, tip, 0f, radius * 0.35f, head, alpha, radius, side, rgb);
		segment(pose, buffer, head, alpha, radius, body, alpha, radius, side, rgb);
		segment(pose, buffer, body, alpha, radius, tail, 0f, radius * 0.6f, side, rgb);
	}

	/**
	 * A piece of ribbon from {@code a} to {@code b}: two halves, each from the centre line (at the given
	 * alpha) out to an edge (alpha 0), drawn from both sides.
	 */
	private static void segment(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, float alphaA, float widthA,
								Vector3f b, float alphaB, float widthB, Vector3f side, int rgb) {
		for (int s = -1; s <= 1; s += 2) {
			Vector3f aEdge = new Vector3f(side).mul(s * widthA).add(a);
			Vector3f bEdge = new Vector3f(side).mul(s * widthB).add(b);
			int ca = argb(alphaA, rgb);
			int cb = argb(alphaB, rgb);
			int clear = rgb; // alpha 0
			quad(pose, buffer, aEdge, clear, a, ca, b, cb, bEdge, clear);
		}
	}

	/** A camera-facing glow at the head, falling off from the centre to the edges. */
	private static void bloom(PoseStack.Pose pose, VertexConsumer buffer, Vector3f centre, Vector3f x, Vector3f y,
							  float radius, int rgb, float alpha) {
		int c = argb(alpha, rgb);
		Vector3f px = new Vector3f(x).mul(radius);
		Vector3f py = new Vector3f(y).mul(radius);
		for (int i = 0; i < 4; i++) {
			// Quarter i: centre, edge midpoint, corner, next edge midpoint.
			Vector3f e1 = rotateQuarter(px, py, i);
			Vector3f e2 = rotateQuarter(px, py, i + 1);
			Vector3f corner = new Vector3f(e1).add(e2).mul(0.7f).add(centre);
			quad(pose, buffer, centre, c, new Vector3f(e1).add(centre), rgb, corner, rgb, new Vector3f(e2).add(centre), rgb);
		}
	}

	private static Vector3f rotateQuarter(Vector3f px, Vector3f py, int i) {
		return switch (i & 3) {
			case 0 -> new Vector3f(px);
			case 1 -> new Vector3f(py);
			case 2 -> new Vector3f(px).negate();
			default -> new Vector3f(py).negate();
		};
	}

	/** A quad drawn with both windings, so it shows whichever way it faces. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p0, int c0, Vector3f p1, int c1,
							 Vector3f p2, int c2, Vector3f p3, int c3) {
		vertex(pose, buffer, p0, c0);
		vertex(pose, buffer, p1, c1);
		vertex(pose, buffer, p2, c2);
		vertex(pose, buffer, p3, c3);
		vertex(pose, buffer, p3, c3);
		vertex(pose, buffer, p2, c2);
		vertex(pose, buffer, p1, c1);
		vertex(pose, buffer, p0, c0);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p, int argb) {
		buffer.addVertex(pose, p.x, p.y, p.z).setColor(argb);
	}

	private static int argb(float alpha, int rgb) {
		return (Math.round(Math.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
	}

	/** Distance from {@code p} to the segment {@code a}-{@code b}. */
	static float distanceToSegment(Vector3f p, Vector3f a, Vector3f b) {
		Vector3f ab = new Vector3f(b).sub(a);
		float len2 = ab.lengthSquared();
		float t = len2 < 1e-8f ? 0f : Math.clamp(new Vector3f(p).sub(a).dot(ab) / len2, 0f, 1f);
		return new Vector3f(ab).mul(t).add(a).distance(p);
	}
}
