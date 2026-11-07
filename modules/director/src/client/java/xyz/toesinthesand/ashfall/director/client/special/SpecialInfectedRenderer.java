package xyz.toesinthesand.ashfall.director.client.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;

/**
 * The vanilla zombie renderer reshaped and recoloured per {@link SpecialLook}: zero new models, so the specials
 * animate, wear damage flashes and hold items exactly like zombies, and survive resource packs that retexture the
 * zombie (they pick up the pack's zombie under their own tint).
 */
public class SpecialInfectedRenderer extends ZombieRenderer {
	private final SpecialLook look;

	public SpecialInfectedRenderer(EntityRendererProvider.Context context, SpecialLook look) {
		super(context);
		this.look = look;
		addLayer(new SpecialOverlayLayer(this, look.overlay()));
	}

	@Override
	protected int getModelTint(ZombieRenderState state) {
		return look.tint();
	}

	/**
	 * Runs after the model flip and before the model is lifted onto its feet, so the origin here is the feet: the
	 * lean pivots there (a positive angle tips the head forward, towards model -z) and the squash keeps the feet on
	 * the ground.
	 */
	@Override
	protected void scale(ZombieRenderState state, PoseStack pose) {
		super.scale(state, pose);
		if (look.leanDeg() != 0f) {
			pose.rotateDegrees(Axis.XP, look.leanDeg());
		}
		if (look.widthX() != 1f || look.heightY() != 1f) {
			pose.scale(look.widthX(), look.heightY(), look.widthX());
		}
	}
}
