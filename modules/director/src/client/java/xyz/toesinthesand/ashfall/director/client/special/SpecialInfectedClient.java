package xyz.toesinthesand.ashfall.director.client.special;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.LlamaSpitRenderer;
import xyz.toesinthesand.ashfall.director.special.SpecialEntities;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;

/** Client half of the special infected: one reshaped zombie renderer per kind, and the llama-spit renderer for acid. */
public final class SpecialInfectedClient {
	private SpecialInfectedClient() {
	}

	public static void init() {
		for (SpecialKind kind : SpecialKind.values()) {
			SpecialLook look = SpecialLook.of(kind);
			EntityRenderers.register(SpecialEntities.type(kind), context -> new SpecialInfectedRenderer(context, look));
		}
		EntityRenderers.register(SpecialEntities.ACID_SPIT, LlamaSpitRenderer::new);
	}
}
