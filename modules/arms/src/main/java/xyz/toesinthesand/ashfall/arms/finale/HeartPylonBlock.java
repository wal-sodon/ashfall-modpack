package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.world.level.block.Block;

/**
 * A Heart pylon: a column of the Wreck's crystal that feeds the Heart. A plain block on purpose (endgame-design
 * section 4: "four crystal pylons, as blocks"), so breaking one is ordinary mining and needs no new combat rules.
 * <p>
 * Very hard (80) and in no {@code mineable} tag, so a pickaxe is no better than a fist (about 100 s a pylon); a lit
 * Plasma Blade mines {@code #ashfall:crystal_pylons} at speed 12 (blade stats), about 10 s. That is the blade role
 * the design asks for. No loot: the crystal shatters. The finale counts pylons by looking at their positions, so a
 * pylon removed any way at all counts as broken.
 */
public class HeartPylonBlock extends Block {
	public HeartPylonBlock(Properties properties) {
		super(properties);
	}
}
