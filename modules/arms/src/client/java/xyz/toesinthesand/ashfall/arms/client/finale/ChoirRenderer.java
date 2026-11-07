package xyz.toesinthesand.ashfall.arms.client.finale;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.arms.finale.FinaleBlocks;

/**
 * The Choir drawn with no new model, the way the special infected are: the vanilla zombie renderer under a pale
 * crystal tint, with a shard overlay ({@code textures/entity/special/choir.png}, from
 * {@code tools/textures/entities/choir.py}) drawn on the same geometry. Its size is the {@code minecraft:scale}
 * attribute, which the vanilla renderer already applies, so the file can change it without a client update.
 */
public class ChoirRenderer extends ZombieRenderer {
	/** Pale violet-blue: the Wreck's crystal colour multiplied into whatever zombie texture the player has. */
	static final int TINT = 0xFFB4B8F0;
	static final Identifier OVERLAY = Identifier.fromNamespaceAndPath("ashfall", "textures/entity/special/choir.png");

	public ChoirRenderer(EntityRendererProvider.Context context) {
		super(context);
		addLayer(new ShardLayer(this));
	}

	public static void register() {
		EntityRenderers.register(FinaleBlocks.CHOIR, ChoirRenderer::new);
	}

	@Override
	protected int getModelTint(ZombieRenderState state) {
		return TINT;
	}

	/** The crystal shards: a cutout copy of the parent model with the overlay texture, like a wolf's collar. */
	static final class ShardLayer extends RenderLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {
		ShardLayer(RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, ZombieRenderState state, float yRot, float xRot) {
			coloredCutoutModelCopyLayerRender(getParentModel(), OVERLAY, pose, collector, light, state, -1, 1);
		}
	}
}
