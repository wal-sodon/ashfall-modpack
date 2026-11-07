package xyz.toesinthesand.ashfall.salvage.client;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import xyz.toesinthesand.ashfall.salvage.bike.Bicycle;
import xyz.toesinthesand.ashfall.salvage.bike.Bikes;

public class BicycleRenderer extends MobRenderer<Bicycle, LivingEntityRenderState, BicycleModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("ashfall", "textures/entity/special/bicycle.png");

	public BicycleRenderer(EntityRendererProvider.Context context) {
		super(context, new BicycleModel(context.bakeLayer(BicycleModel.LAYER)), 0.4f);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(BicycleModel.LAYER, BicycleModel::createLayer);
		EntityRenderers.register(Bikes.BICYCLE, BicycleRenderer::new);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
