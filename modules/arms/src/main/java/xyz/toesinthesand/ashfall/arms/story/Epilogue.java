package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The epilogue book of endgame-design section 4: "Asheville Relit — Epilogue", assembled from the Relay Logs the
 * reader heard, in log order, and closed by the ending's own pages from its profile ({@link EndingProfile.Closing}).
 * <p>
 * Built per player from their own collection: two survivors who listened to different relays read different
 * epilogues, which is the reward for chasing the logs. Logs missed before the ending stay collectible (section 5,
 * "Lore"), and {@code /ashfall ending epilogue} prints a fresh copy with whatever has been heard since.
 */
public final class Epilogue {
	public static final String TITLE = "Asheville Relit — Epilogue";
	public static final String AUTHOR = "The Survivors";
	/** Written books hold at most 100 pages; the cover, the gap note and the closing must always fit. */
	public static final int MAX_PAGES = 100;
	/** Characters of a log's text kept on its epilogue page, so title plus excerpt fit one book page. */
	public static final int EXCERPT_CHARS = 200;

	private Epilogue() {
	}

	/**
	 * The epilogue's pages: a cover, one page per heard log (title and an excerpt of its text), a note on how many logs
	 * are still out there, then the ending's closing pages. Pure, for the gametests.
	 *
	 * @param pool   the numbered Relay Logs, in order
	 * @param heard  log numbers the reader has heard
	 * @param ending the world's ending (its name goes on the cover)
	 */
	public static List<Component> pages(List<RelayLog> pool, Collection<Integer> heard, StoryEnding ending, EndingProfile.Closing closing) {
		List<RelayLog> found = new ArrayList<>();
		for (RelayLog log : pool) {
			if (heard.contains(log.number())) {
				found.add(log);
			}
		}
		int reserved = 2 + closing.pages().size();
		List<Component> pages = new ArrayList<>();
		pages.add(Component.empty()
				.append(Component.literal("Asheville Relit").withStyle(ChatFormatting.BOLD)).append("\n")
				.append(Component.translatable("book.ashfall.epilogue.subtitle", Component.translatable(ending.translationKey()))
						.withStyle(ChatFormatting.DARK_GRAY)).append("\n\n")
				.append(Component.translatable("book.ashfall.epilogue.cover", found.size(), pool.size())));
		for (RelayLog log : found) {
			if (pages.size() + reserved - 1 >= MAX_PAGES) {
				break;
			}
			pages.add(Component.empty()
					.append(Component.literal("#" + log.number() + " " + log.title()).withStyle(ChatFormatting.BOLD)).append("\n")
					.append(Component.literal(log.author()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)).append("\n\n")
					.append(Component.literal(excerpt(log))));
		}
		int missing = pool.size() - found.size();
		pages.add(missing > 0
				? Component.translatable("book.ashfall.epilogue.missing", missing).withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("book.ashfall.epilogue.complete").withStyle(ChatFormatting.DARK_GREEN));
		for (int i = 0; i < closing.pages().size(); i++) {
			MutableComponent page = Component.empty();
			if (i == 0 && !closing.title().isEmpty()) {
				page.append(Component.literal(closing.title()).withStyle(ChatFormatting.BOLD)).append("\n\n");
			}
			pages.add(page.append(Component.literal(closing.pages().get(i))));
		}
		return pages;
	}

	/** A log's text joined into one excerpt, cut at a word boundary with an ellipsis when it is long. */
	static String excerpt(RelayLog log) {
		String text = String.join(" ", log.pages()).strip();
		if (text.length() <= EXCERPT_CHARS) {
			return text;
		}
		int cut = text.lastIndexOf(' ', EXCERPT_CHARS - 1);
		return text.substring(0, cut > EXCERPT_CHARS / 2 ? cut : EXCERPT_CHARS - 1).stripTrailing() + "…";
	}

	/** The epilogue as a written book for {@code reader}, from their own Relay Logs. */
	public static ItemStack book(ServerPlayer reader, StoryEnding ending) {
		return FieldReport.book(TITLE, AUTHOR, pages(RelayLogs.pool(), RadioProgress.get(reader).logs(), ending,
				EndingProfiles.get(ending).closing()));
	}
}
