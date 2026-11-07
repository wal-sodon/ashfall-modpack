package xyz.toesinthesand.ashfall.salvage.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * A plain city bike in model units (y down, 24 = the ground, front toward -z): two wheels, each two crossed boxes so
 * the outline reads round, a frame, fork, handlebar, seat and cranks. The wheels and cranks turn as it moves.
 */
public class BicycleModel extends EntityModel<LivingEntityRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("ashfall", "bicycle"), "main");
	/** Wheel turn per block travelled: a 16-unit (one block) wheel turns about once per three blocks. */
	private static final float SPIN = 2.0f;

	private final ModelPart frontWheel;
	private final ModelPart rearWheel;
	private final ModelPart crank;

	public BicycleModel(ModelPart root) {
		super(root);
		frontWheel = root.getChild("front_wheel");
		rearWheel = root.getChild("rear_wheel");
		crank = root.getChild("crank");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeListBuilder wheel = CubeListBuilder.create()
				.texOffs(0, 0).addBox(-0.5f, -4f, -3f, 1, 8, 6)
				.texOffs(14, 0).addBox(-0.5f, -3f, -4f, 1, 6, 8);
		root.addOrReplaceChild("front_wheel", wheel, PartPose.offset(0, 20, -7));
		root.addOrReplaceChild("rear_wheel", wheel, PartPose.offset(0, 20, 7));
		root.addOrReplaceChild("frame", CubeListBuilder.create()
				.texOffs(0, 14).addBox(-0.5f, 13f, -6f, 1, 1, 11)    // top tube
				.texOffs(0, 28).addBox(-0.5f, 11f, 4f, 1, 9, 1)      // seat post
				.texOffs(8, 28).addBox(-0.5f, 13f, -2f, 1, 7, 1)     // down tube
				.texOffs(26, 14).addBox(-0.5f, 19.5f, 0f, 1, 1, 7)   // chain stay
				.texOffs(4, 28).addBox(-0.5f, 11f, -7.5f, 1, 9, 1)   // fork
				.texOffs(26, 24).addBox(-4f, 10f, -7.5f, 8, 1, 1)    // handlebar
				.texOffs(26, 27).addBox(-1.5f, 10f, 3f, 3, 1, 4),    // seat
				PartPose.ZERO);
		root.addOrReplaceChild("crank", CubeListBuilder.create().texOffs(44, 14).addBox(-2.5f, -0.5f, -0.5f, 5, 1, 1),
				PartPose.offset(0, 20, -1));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float turn = state.walkAnimationPos * SPIN;
		frontWheel.xRot = turn;
		rearWheel.xRot = turn;
		crank.xRot = turn;
	}
}
