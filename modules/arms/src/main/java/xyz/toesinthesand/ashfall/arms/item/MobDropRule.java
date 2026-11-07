package xyz.toesinthesand.ashfall.arms.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One {@code data/<ns>/ashfall/mob_drops/<entity ns>/<entity path>.json} file: the extra drops Ashfall adds to
 * one entity's loot table.
 * <p>
 * A file per entity (named after it) means a datapack, or the Mob Drops screen writing into
 * {@code world/datapacks/ashfall_overrides}, replaces the shipped rules for that entity just by using the same
 * path; an empty {@code drops} list switches Ashfall's drops off for it.
 *
 * @param entity the entity type id; its default loot table {@code <ns>:entities/<path>} receives the drops
 */
public record MobDropRule(Identifier entity, List<Drop> drops) {
	/**
	 * One extra loot pool: {@code chance} to drop {@code min}..{@code max} of {@code item}.
	 *
	 * @param killedByPlayer only when a player lands the kill (no mob farms, no environment kills)
	 */
	public record Drop(Identifier item, float chance, boolean killedByPlayer, int min, int max) {
		public static final int MAX_COUNT = 64;
		public static final Codec<Drop> CODEC = RecordCodecBuilder.<Drop>create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(Drop::item),
				Codec.floatRange(0, 1).fieldOf("chance").forGetter(Drop::chance),
				Codec.BOOL.optionalFieldOf("killed_by_player", true).forGetter(Drop::killedByPlayer),
				Codec.intRange(1, MAX_COUNT).optionalFieldOf("min", 1).forGetter(Drop::min),
				Codec.intRange(1, MAX_COUNT).optionalFieldOf("max", 1).forGetter(Drop::max)
		).apply(i, Drop::new)).validate(d -> d.min() <= d.max() ? DataResult.success(d)
				: DataResult.error(() -> "min " + d.min() + " is above max " + d.max()));
	}

	public static final Codec<MobDropRule> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("entity").forGetter(MobDropRule::entity),
			Drop.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(MobDropRule::drops)
	).apply(i, MobDropRule::new));
}
