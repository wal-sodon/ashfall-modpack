package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import xyz.toesinthesand.ashfall.arms.grid.GridState;
import xyz.toesinthesand.ashfall.director.boss.BossState;
import xyz.toesinthesand.ashfall.director.horde.HordeSchedule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The ending stats screen of endgame-design section 4, as a written book: "Asheville Relit — Field Report", one per
 * participant. A book rather than a client screen because it needs no client code, works on a vanilla client, and is
 * a keepsake that stays in the chest after the title text has faded.
 * <p>
 * The numbers are the party's: days survived, districts lit, story bosses killed, Relay Logs heard by anyone in the
 * party and players lost to the infection (died while infected); the last page adds the reader's own lines. They are frozen into
 * {@link StoryState} at the ending, so a copy printed later tells the same story.
 */
public final class FieldReport {
	public static final String TITLE = "Asheville Relit — Field Report";
	public static final String AUTHOR = "City Grid Dispatch";

	private FieldReport() {
	}

	/**
	 * The party's numbers at the ending.
	 *
	 * @param days           world day the ending fell on
	 * @param districtsLit   districts reclaimed
	 * @param districtsTotal scored districts
	 * @param bosses         story boss kills (every lair, returns included)
	 * @param logsFound      distinct Relay Logs heard by at least one participant
	 * @param logsTotal      Relay Logs in the pool
	 * @param risen          player deaths while infected, world-wide (the save key predates 10.3.0, when the infected
	 *                       still rose as zombies)
	 * @param deaths         player deaths, world-wide
	 */
	public record Stats(int days, int districtsLit, int districtsTotal, int bosses, int logsFound, int logsTotal, int risen,
			int deaths) {
		public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0, 0);
		public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("days", 0).forGetter(Stats::days),
				Codec.INT.optionalFieldOf("districts_lit", 0).forGetter(Stats::districtsLit),
				Codec.INT.optionalFieldOf("districts_total", 0).forGetter(Stats::districtsTotal),
				Codec.INT.optionalFieldOf("bosses", 0).forGetter(Stats::bosses),
				Codec.INT.optionalFieldOf("logs_found", 0).forGetter(Stats::logsFound),
				Codec.INT.optionalFieldOf("logs_total", 0).forGetter(Stats::logsTotal),
				Codec.INT.optionalFieldOf("risen", 0).forGetter(Stats::risen),
				Codec.INT.optionalFieldOf("deaths", 0).forGetter(Stats::deaths)
		).apply(i, Stats::new));
	}

	/** Collects the party's numbers now from the grid, the boss lairs, the participants' radios and the counters. */
	public static Stats collect(MinecraftServer server, Collection<ServerPlayer> participants) {
		GridState grid = GridState.get(server);
		int bosses = 0;
		for (BossState.Lair lair : BossState.get(server).lairs()) {
			bosses += lair.kills();
		}
		Set<Integer> heard = new TreeSet<>();
		for (ServerPlayer player : participants) {
			heard.addAll(RadioProgress.get(player).logs());
		}
		int found = 0;
		for (RelayLog log : RelayLogs.pool()) {
			if (heard.contains(log.number())) {
				found++;
			}
		}
		StoryStats counters = StoryStats.get(server);
		int day = HordeSchedule.day(server.overworld().getOverworldClockTime());
		return new Stats(day, grid.reclaimedCount(), grid.districts().size(), bosses, found, RelayLogs.pool().size(),
				counters.risen(), counters.deaths());
	}

	/**
	 * The report's pages: a cover with the ending, the party's numbers, and the reader's own lines. Pure (no server),
	 * so the gametests check the assembly directly.
	 */
	public static List<Component> pages(Stats stats, StoryEnding ending, String reader, int readerLogs, SurvivorStats readerStats) {
		List<Component> pages = new ArrayList<>();
		pages.add(Component.empty()
				.append(Component.literal("Asheville Relit").withStyle(ChatFormatting.BOLD)).append("\n")
				.append(Component.translatable("book.ashfall.report.subtitle").withStyle(ChatFormatting.DARK_GRAY)).append("\n\n")
				.append(Component.translatable("book.ashfall.report.ending", Component.translatable(ending.translationKey())
						.withStyle(ending == StoryEnding.BIND ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_PURPLE))).append("\n\n")
				.append(Component.translatable("book.ashfall.report.cover." + ending.id())));
		MutableComponent numbers = Component.empty()
				.append(Component.translatable("book.ashfall.report.numbers").withStyle(ChatFormatting.BOLD)).append("\n\n");
		line(numbers, "book.ashfall.report.days", stats.days());
		line(numbers, "book.ashfall.report.districts", stats.districtsLit(), stats.districtsTotal());
		line(numbers, "book.ashfall.report.bosses", stats.bosses());
		line(numbers, "book.ashfall.report.logs", stats.logsFound(), stats.logsTotal());
		line(numbers, "book.ashfall.report.risen", stats.risen());
		line(numbers, "book.ashfall.report.deaths", stats.deaths());
		pages.add(numbers);
		MutableComponent own = Component.empty()
				.append(Component.literal(reader).withStyle(ChatFormatting.BOLD)).append("\n\n");
		line(own, "book.ashfall.report.own_logs", readerLogs, stats.logsTotal());
		line(own, "book.ashfall.report.own_deaths", readerStats.deaths());
		line(own, "book.ashfall.report.own_risen", readerStats.risen());
		own.append("\n").append(Component.translatable("book.ashfall.report.sign_off").withStyle(ChatFormatting.ITALIC));
		pages.add(own);
		return pages;
	}

	private static void line(MutableComponent page, String key, Object... args) {
		page.append(Component.translatable(key, args)).append("\n");
	}

	/** The report as a written book for {@code reader}. */
	public static ItemStack book(Stats stats, StoryEnding ending, ServerPlayer reader) {
		List<Component> pages = pages(stats, ending, reader.getName().getString(), RelayLogs.collected(RadioProgress.get(reader)),
				SurvivorStats.get(reader));
		return book(TITLE, AUTHOR, pages);
	}

	/** A tattered (uncopyable) written book with these pages. Shared with the epilogue. */
	static ItemStack book(String title, String author, List<Component> pages) {
		List<Filterable<Component>> filtered = new ArrayList<>();
		for (Component page : pages) {
			filtered.add(Filterable.passThrough(page));
		}
		String t = title.length() > WrittenBookContent.TITLE_MAX_LENGTH ? title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH) : title;
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(t), author, 3, filtered, true));
		return stack;
	}
}
