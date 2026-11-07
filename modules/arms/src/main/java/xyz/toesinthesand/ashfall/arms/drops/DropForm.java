package xyz.toesinthesand.ashfall.arms.drops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.toesinthesand.ashfall.arms.item.MobDropRule;

/**
 * One drop row of the Mob Drops screen as typed: the item stays a string so the server, not the client, decides
 * whether it names a real item and can say so.
 */
public record DropForm(String item, float chance, boolean killedByPlayer, int min, int max) {
	public static final Codec<DropForm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("item").forGetter(DropForm::item),
			Codec.FLOAT.fieldOf("chance").forGetter(DropForm::chance),
			Codec.BOOL.fieldOf("killed_by_player").forGetter(DropForm::killedByPlayer),
			Codec.INT.fieldOf("min").forGetter(DropForm::min),
			Codec.INT.fieldOf("max").forGetter(DropForm::max)
	).apply(i, DropForm::new));

	public static DropForm of(MobDropRule.Drop drop) {
		return new DropForm(drop.item().toString(), drop.chance(), drop.killedByPlayer(), drop.min(), drop.max());
	}
}
