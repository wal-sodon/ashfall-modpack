package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * What an ending changes, from {@code data/<ns>/ashfall/ending/<sever|bind>.json} (endgame-design section 4). Every
 * number a designer might tune lives in the file, and the code only knows how to apply each field, so a datapack can
 * rebalance an ending (or make Sever harsher) without touching Java.
 *
 * @param antibioticsCure      sets the {@code ashfall:sever} story flag, with which Antibiotics cure infection at any
 *                             stage (user decision 2026-10-25: there is still no cure item)
 * @param flags                further story flags the ending sets ({@code ashfall:bind} wakes the Ascendant's lair)
 * @param spawnMultiplier      Director ambient and building spawning multiplier (Sever: 0.4, "about 40%")
 * @param hordeInterval        horde nights come this many times less often (Sever: rare "Aftershocks")
 * @param hordeGrowth          Bind's weekly escalation of horde nights, up to a hard cap
 * @param freeRelays           every Power Relay charges without cells, inside a district or not ("a free Grid Point")
 * @param unlockAttachments    the Weaponsmith Bench fits district-gated attachments (Overcharger) anywhere
 * @param bunkerRadiusBonus    extra chunks on each side of every Bunker Core's haven (cores powered by the Heart)
 * @param rewards              items each participant receives (Bind: the Resonant Plasma Blade)
 * @param aftershocks          how often new crash sites fall after the ending
 * @param closing              the epilogue's ending-specific closing pages
 */
public record EndingProfile(boolean antibioticsCure, List<String> flags, float spawnMultiplier, int hordeInterval,
		Optional<HordeGrowth> hordeGrowth, boolean freeRelays, boolean unlockAttachments, int bunkerRadiusBonus,
		List<Identifier> rewards, Aftershocks aftershocks, Closing closing) {

	/**
	 * Bind's horde escalation: every {@code everyDays} days after the ending the tier floor rises by {@code tierPerStep}
	 * from {@code tierStart} and waves grow by {@code sizePerStep}, until {@code tierCap} / {@code sizeCap}. The
	 * Director clamps both again (tier VI, x2), so a datapack can make it gentler but never unbounded.
	 */
	public record HordeGrowth(int everyDays, int tierStart, int tierPerStep, int tierCap, float sizePerStep, float sizeCap) {
		public static final Codec<HordeGrowth> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 365).optionalFieldOf("every_days", 7).forGetter(HordeGrowth::everyDays),
				Codec.intRange(0, 6).optionalFieldOf("tier_start", 4).forGetter(HordeGrowth::tierStart),
				Codec.intRange(0, 6).optionalFieldOf("tier_per_step", 1).forGetter(HordeGrowth::tierPerStep),
				Codec.intRange(0, 6).optionalFieldOf("tier_cap", 6).forGetter(HordeGrowth::tierCap),
				Codec.floatRange(0f, 1f).optionalFieldOf("size_per_step", 0.1f).forGetter(HordeGrowth::sizePerStep),
				Codec.floatRange(1f, 2f).optionalFieldOf("size_cap", 1.5f).forGetter(HordeGrowth::sizeCap)
		).apply(i, HordeGrowth::new));

		/** Escalation steps taken {@code daysSinceEnding} days after the ending (0 on the ending day itself). */
		public int steps(long daysSinceEnding) {
			return (int) Math.min(Integer.MAX_VALUE / 8, Math.max(0, daysSinceEnding) / everyDays);
		}

		/** The horde tier floor {@code daysSinceEnding} days after the ending, never above {@code tierCap}. */
		public int tier(long daysSinceEnding) {
			return Math.min(tierCap, tierStart + steps(daysSinceEnding) * tierPerStep);
		}

		/** The wave size multiplier {@code daysSinceEnding} days after the ending, never above {@code sizeCap}. */
		public double size(long daysSinceEnding) {
			return Math.min(sizeCap, 1.0 + steps(daysSinceEnding) * (double) sizePerStep);
		}
	}

	/**
	 * Aftershock crash sites: a new pass of crash sites every {@code everyDays} days after the ending, each pass at
	 * {@code chanceScale} of the rules' chance (0 days turns them off).
	 */
	public record Aftershocks(int everyDays, float chanceScale) {
		public static final Aftershocks NONE = new Aftershocks(0, 0f);
		public static final Codec<Aftershocks> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(0, 365).optionalFieldOf("every_days", 7).forGetter(Aftershocks::everyDays),
				Codec.floatRange(0f, 1f).optionalFieldOf("chance_scale", 0.5f).forGetter(Aftershocks::chanceScale)
		).apply(i, Aftershocks::new));

		/** Passes fallen {@code daysSinceEnding} days after the ending: the first one a full interval after it. */
		public int generations(long daysSinceEnding) {
			return everyDays <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, daysSinceEnding) / everyDays);
		}
	}

	/** The ending's closing pages for the epilogue: a heading and plain-text pages (original text, data-driven). */
	public record Closing(String title, List<String> pages) {
		public static final Closing EMPTY = new Closing("", List.of());
		public static final Codec<Closing> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("title", "").forGetter(Closing::title),
				Codec.STRING.listOf().optionalFieldOf("pages", List.of()).forGetter(Closing::pages)
		).apply(i, Closing::new));
	}

	/** No ending: nothing changes. Also what an ending falls back to if its file is missing. */
	public static final EndingProfile NEUTRAL = new EndingProfile(false, List.of(), 1f, 1, Optional.empty(), false, false, 0,
			List.of(), Aftershocks.NONE, Closing.EMPTY);

	public static final Codec<EndingProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("antibiotics_cure", false).forGetter(EndingProfile::antibioticsCure),
			Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(EndingProfile::flags),
			Codec.floatRange(0.05f, 4f).optionalFieldOf("spawn_multiplier", 1f).forGetter(EndingProfile::spawnMultiplier),
			Codec.intRange(1, 10).optionalFieldOf("horde_interval_multiplier", 1).forGetter(EndingProfile::hordeInterval),
			HordeGrowth.CODEC.optionalFieldOf("horde_growth").forGetter(EndingProfile::hordeGrowth),
			Codec.BOOL.optionalFieldOf("free_relays", false).forGetter(EndingProfile::freeRelays),
			Codec.BOOL.optionalFieldOf("unlock_attachments", false).forGetter(EndingProfile::unlockAttachments),
			Codec.intRange(0, 4).optionalFieldOf("bunker_radius_bonus", 0).forGetter(EndingProfile::bunkerRadiusBonus),
			Identifier.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(EndingProfile::rewards),
			Aftershocks.CODEC.optionalFieldOf("aftershocks", Aftershocks.NONE).forGetter(EndingProfile::aftershocks),
			Closing.CODEC.optionalFieldOf("closing", Closing.EMPTY).forGetter(EndingProfile::closing)
	).apply(i, EndingProfile::new));
}
