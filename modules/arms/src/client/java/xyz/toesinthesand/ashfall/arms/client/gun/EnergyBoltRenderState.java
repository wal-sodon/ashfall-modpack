package xyz.toesinthesand.ashfall.arms.client.gun;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * What the bolt renderer needs, all relative to the entity's render position: the beam's head (with the
 * centre lift and the fading muzzle offset applied), its unit flight direction, how long it is this
 * frame, its thickness and colour, and whether to draw it at all.
 */
public class EnergyBoltRenderState extends EntityRenderState {
	public boolean visible;
	public int color = 0xFFFFFF;
	public float headX;
	public float headY;
	public float headZ;
	public float dirX;
	public float dirY;
	public float dirZ = 1f;
	public float length;
	public float coreRadius;
	public float glowRadius;
}
