package xyz.toesinthesand.ashfall.director.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One story boss and its lair, from {@code data/<ns>/ashfall/boss_lair/<id>.json} (endgame design section 3:
 * the Abomination in the Kiln &amp; Co. foundry, Patient Zero in the hospital's sealed deep ward, the Juggernaut at
 * the Wreck hull). Everything a designer might tune is here rather than in code: where the lair is, what the
 * boss is, when it may come back, what it drops.
 *
 * @param name          translation key for the boss's name and its boss bar
 * @param anchor        where the lair is (a Lost Cities landmark, a fixed site, or a fixed position)
 * @param boss          what spawns
 * @param attributes    attribute overrides applied after the boss's variant (so a lair can make a stock mob
 *                      tougher, as it does for the mutant zombie)
 * @param glowing       outline the boss (vanilla glowing): a boss fight should never be lost to the dark
 * @param particle      ambient particle id around the boss (a simple particle type), empty for none
 * @param bossbar       boss bar look
 * @param triggerRadius a player this close to the spawn point wakes the lair
 * @param barRadius     players this close to the boss see its bar
 * @param sealed        the lair stays shut until {@link BossLairs#unseal} opens it (the deep ward until the plant
 *                      is restored); while sealed the boss never spawns and the seal's doors cannot be broken
 * @param sealRadius    how far from the spawn point the seal's doors are (on the boss's storey)
 * @param respawn       when a defeated boss may return; one-time per world when absent
 * @param drops         guaranteed items and a tiered loot table rolled on any death
 * @param summon        minions the boss calls while it has a target
 * @param requiresFlag  a story flag ({@code StoryFlags}) the lair needs: without it the boss never wakes, and with it
 *                      the lair counts as defeated at the ending, so its first visit waits out {@code respawn} like
 *                      every return after it. The Bind ending's Ascendant uses {@code ashfall:bind} to "return every
 *                      10 nights" from the ending on, and never exists in a Sever world.
 */
public record BossLair(String name, Anchor anchor, Boss boss, Map<Identifier, Double> attributes, boolean glowing,
		Optional<Identifier> particle, Bar bossbar, int triggerRadius, int barRadius, boolean sealed, int sealRadius,
		Optional<Respawn> respawn, Drops drops, Optional<Summon> summon, Optional<String> requiresFlag) {

	/** A lair that needs no story flag (every lair before the ending existed). */
	public BossLair(String name, Anchor anchor, Boss boss, Map<Identifier, Double> attributes, boolean glowing,
			Optional<Identifier> particle, Bar bossbar, int triggerRadius, int barRadius, boolean sealed, int sealRadius,
			Optional<Respawn> respawn, Drops drops, Optional<Summon> summon) {
		this(name, anchor, boss, attributes, glowing, particle, bossbar, triggerRadius, barRadius, sealed, sealRadius, respawn,
				drops, summon, Optional.empty());
	}

	/**
	 * Exactly one of: a landmark ({@code landmark} + its north-west {@code chunk} + {@code size} in chunks, the
	 * boss standing on the landmark's boss marker), a fixed {@code site} (its {@code boss} marker), or a fixed
	 * block {@code pos}.
	 */
	public record Anchor(Optional<String> landmark, Optional<List<Integer>> chunk, Optional<List<Integer>> size,
			Optional<String> site, Optional<BlockPos> pos) {
		public static final Codec<Anchor> CODEC = RecordCodecBuilder.<Anchor>create(i -> i.group(
				Codec.STRING.optionalFieldOf("landmark").forGetter(Anchor::landmark),
				Codec.INT.listOf(2, 2).optionalFieldOf("chunk").forGetter(Anchor::chunk),
				Codec.INT.listOf(2, 2).optionalFieldOf("size").forGetter(Anchor::size),
				Codec.STRING.optionalFieldOf("site").forGetter(Anchor::site),
				BlockPos.CODEC.optionalFieldOf("pos").forGetter(Anchor::pos)
		).apply(i, Anchor::new)).validate(Anchor::validate);

		public static Anchor at(BlockPos pos) {
			return new Anchor(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(pos));
		}

		static DataResult<Anchor> validate(Anchor a) {
			int kinds = (a.landmark.isPresent() ? 1 : 0) + (a.site.isPresent() ? 1 : 0) + (a.pos.isPresent() ? 1 : 0);
			if (kinds != 1) {
				return DataResult.error(() -> "a lair anchor needs exactly one of landmark, site or pos");
			}
			if (a.landmark.isPresent() && a.chunk.isEmpty()) {
				return DataResult.error(() -> "a landmark anchor needs its north-west chunk");
			}
			return DataResult.success(a);
		}

		public boolean isLandmark() {
			return landmark.isPresent();
		}

		public int chunkX() {
			return chunk.map(List::getFirst).orElse(0);
		}

		public int chunkZ() {
			return chunk.map(List::getLast).orElse(0);
		}

		public int widthChunks() {
			return size.map(List::getFirst).orElse(1);
		}

		public int depthChunks() {
			return size.map(List::getLast).orElse(1);
		}
	}

	/**
	 * What spawns: a zombie variant from our {@code zombie_variant} data (weight 0 keeps it out of normal
	 * spawning), or another mod's entity, with a variant to fall back on when that mod is missing so a pack
	 * without Mutant Monsters still has its Abomination fight.
	 */
	public record Boss(Optional<Identifier> variant, Optional<Identifier> entity, Optional<Identifier> fallbackVariant) {
		public static final Codec<Boss> CODEC = RecordCodecBuilder.<Boss>create(i -> i.group(
				Identifier.CODEC.optionalFieldOf("variant").forGetter(Boss::variant),
				Identifier.CODEC.optionalFieldOf("entity").forGetter(Boss::entity),
				Identifier.CODEC.optionalFieldOf("fallback_variant").forGetter(Boss::fallbackVariant)
		).apply(i, Boss::new)).validate(b -> b.variant.isPresent() != b.entity.isPresent()
				? DataResult.success(b) : DataResult.error(() -> "a boss needs exactly one of variant or entity"));
	}

	public record Bar(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		public static final Bar DEFAULT = new Bar(BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		public static final Codec<Bar> CODEC = RecordCodecBuilder.create(i -> i.group(
				BossEvent.BossBarColor.CODEC.optionalFieldOf("color", BossEvent.BossBarColor.RED).forGetter(Bar::color),
				BossEvent.BossBarOverlay.CODEC.optionalFieldOf("overlay", BossEvent.BossBarOverlay.NOTCHED_10).forGetter(Bar::overlay)
		).apply(i, Bar::new));
	}

	/**
	 * A defeated boss may return {@code afterEndingDays} in-game days after the story ending (or after its defeat,
	 * whichever is later): the "Aftershock" bosses that keep crystals obtainable in the post-ending sandbox.
	 */
	public record Respawn(int afterEndingDays) {
		public static final Codec<Respawn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 100_000).fieldOf("after_ending_days").forGetter(Respawn::afterEndingDays)
		).apply(i, Respawn::new));
	}

	public record ItemCount(Identifier item, int count) {
		public static final Codec<ItemCount> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(ItemCount::item),
				Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(ItemCount::count)
		).apply(i, ItemCount::new));
	}

	/**
	 * Items are named by id, not by Java reference: the crystals and cells belong to arms, above this module, and
	 * a missing item (arms not installed) is logged and skipped rather than breaking the lair.
	 */
	public record Drops(List<ItemCount> guaranteed, Optional<Identifier> lootTable) {
		public static final Drops NONE = new Drops(List.of(), Optional.empty());
		public static final Codec<Drops> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemCount.CODEC.listOf().optionalFieldOf("guaranteed", List.of()).forGetter(Drops::guaranteed),
				Identifier.CODEC.optionalFieldOf("loot_table").forGetter(Drops::lootTable)
		).apply(i, Drops::new));
	}

	/** Every {@code everyTicks} while the boss has a target, {@code count} minions, up to {@code maxAlive} near it. */
	public record Summon(Identifier variant, int everyTicks, int count, int maxAlive, int radius) {
		public static final Codec<Summon> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("variant").forGetter(Summon::variant),
				Codec.intRange(20, 1_000_000).optionalFieldOf("every_ticks", 300).forGetter(Summon::everyTicks),
				Codec.intRange(1, 16).optionalFieldOf("count", 2).forGetter(Summon::count),
				Codec.intRange(1, 64).optionalFieldOf("max_alive", 6).forGetter(Summon::maxAlive),
				Codec.intRange(1, 32).optionalFieldOf("radius", 5).forGetter(Summon::radius)
		).apply(i, Summon::new));
	}

	public static final Codec<BossLair> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(BossLair::name),
			Anchor.CODEC.fieldOf("anchor").forGetter(BossLair::anchor),
			Boss.CODEC.fieldOf("boss").forGetter(BossLair::boss),
			Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("attributes", Map.of()).forGetter(BossLair::attributes),
			Codec.BOOL.optionalFieldOf("glowing", false).forGetter(BossLair::glowing),
			Identifier.CODEC.optionalFieldOf("particle").forGetter(BossLair::particle),
			Bar.CODEC.optionalFieldOf("bossbar", Bar.DEFAULT).forGetter(BossLair::bossbar),
			Codec.intRange(1, 256).optionalFieldOf("trigger_radius", 32).forGetter(BossLair::triggerRadius),
			Codec.intRange(1, 256).optionalFieldOf("bar_radius", 48).forGetter(BossLair::barRadius),
			Codec.BOOL.optionalFieldOf("sealed", false).forGetter(BossLair::sealed),
			Codec.intRange(1, 64).optionalFieldOf("seal_radius", 16).forGetter(BossLair::sealRadius),
			Respawn.CODEC.optionalFieldOf("respawn").forGetter(BossLair::respawn),
			Drops.CODEC.optionalFieldOf("drops", Drops.NONE).forGetter(BossLair::drops),
			Summon.CODEC.optionalFieldOf("summon").forGetter(BossLair::summon),
			Codec.STRING.optionalFieldOf("requires_flag").forGetter(BossLair::requiresFlag)
	).apply(i, BossLair::new));
}
