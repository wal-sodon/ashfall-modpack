package xyz.toesinthesand.ashfall.arms.drops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * One entity's rule as the Mob Drops screen lists it.
 *
 * @param overridden whether the rule comes from the admin override datapack (so "Reset" has something to undo)
 */
public record MobDropView(String entity, List<DropForm> drops, boolean overridden) {
	public static final Codec<MobDropView> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("entity").forGetter(MobDropView::entity),
			DropForm.CODEC.listOf().fieldOf("drops").forGetter(MobDropView::drops),
			Codec.BOOL.fieldOf("overridden").forGetter(MobDropView::overridden)
	).apply(i, MobDropView::new));
}
