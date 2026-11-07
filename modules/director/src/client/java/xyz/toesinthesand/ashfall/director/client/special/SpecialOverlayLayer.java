package xyz.toesinthesand.ashfall.director.client.special;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a special's overlay texture on the parent zombie model, the way vanilla draws a wolf's collar: same
 * geometry, cutout texture, drawn after the body. Skipped while the mob is invisible (the hidden stalker).
 */
public class SpecialOverlayLayer extends RenderLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {
	private final Identifier texture;

	public SpecialOverlayLayer(RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> parent, Identifier texture) {
		super(parent);
		this.texture = texture;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, ZombieRenderState state, float yRot, float xRot) {
		coloredCutoutModelCopyLayerRender(getParentModel(), texture, pose, collector, light, state, -1, 1);
	}
}
