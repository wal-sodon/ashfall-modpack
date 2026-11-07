package xyz.toesinthesand.ashfall.arms.story;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import xyz.toesinthesand.ashfall.arms.AshfallArms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The Relay Log pool and the books made from it.
 * <p>
 * A player holding the Field Radio who uses a Power Relay "tunes in" to the fragment that relay carries
 * ({@link RelayLogPicker}) and gets it as a written book, once per log: duplicates would only clutter
 * inventories, and the collection count drives the "Relight" advancements. What a player has heard lives in
 * the {@link RadioProgress} attachment, so it survives death and logging out.
 */
public final class RelayLogs {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ashfall", "relay_logs");
	public static final String AUTHOR_FALLBACK = "Unknown";

	/** Numbered logs, sorted by number: the order the picker indexes into. */
	private static volatile List<RelayLog> pool = List.of();
	/** Every log by id ({@code ashfall:log_07}, {@code ashfall:site/warehouse}). */
	private static volatile Map<Identifier, RelayLog> byId = Map.of();

	private RelayLogs() {
	}

	/** What happened when a player tuned into a relay. */
	public enum Result {
		/** A new log: the book went to the player. */
		NEW,
		/** The player already has this relay's log. */
		ALREADY_HEARD,
		/** No logs are loaded (a datapack removed them). */
		EMPTY
	}

	/** The outcome plus the log involved (absent only for {@link Result#EMPTY}). */
	public record Tuned(Result result, Optional<RelayLog> log) {
	}

	public static List<RelayLog> pool() {
		return pool;
	}

	public static Optional<RelayLog> get(Identifier id) {
		return Optional.ofNullable(byId.get(id));
	}

	/** The log a relay at {@code pos} carries, if any logs are loaded. */
	public static Optional<RelayLog> forRelay(BlockPos pos) {
		List<RelayLog> logs = pool;
		int index = RelayLogPicker.index(pos.asLong(), logs.size());
		return index < 0 ? Optional.empty() : Optional.of(logs.get(index));
	}

	/**
	 * Tunes {@code player} into the relay at {@code relayPos}: records the relay as discovered (for the Grid
	 * Map), and if its log is new, records it and gives the book (dropped at the player's feet if the
	 * inventory is full).
	 */
	public static Tuned tune(ServerPlayer player, BlockPos relayPos) {
		Optional<RelayLog> log = forRelay(relayPos);
		RadioProgress progress = RadioProgress.get(player).withRelay(relayPos.asLong());
		if (log.isEmpty()) {
			RadioProgress.set(player, progress);
			return new Tuned(Result.EMPTY, log);
		}
		int number = log.get().number();
		if (progress.hasLog(number)) {
			RadioProgress.set(player, progress);
			return new Tuned(Result.ALREADY_HEARD, log);
		}
		RadioProgress.set(player, progress.withLog(number));
		ItemStack book = book(log.get());
		if (!player.getInventory().add(book)) {
			player.spawnAtLocation(player.level(), book);
		}
		return new Tuned(Result.NEW, log);
	}

	/** How many numbered logs the player has collected. Only logs still in the pool count. */
	public static int collected(RadioProgress progress) {
		int n = 0;
		for (RelayLog log : pool) {
			if (progress.hasLog(log.number())) {
				n++;
			}
		}
		return n;
	}

	/** A written book holding {@code log}: "Relay Log #N" for pool logs, the document's own title otherwise. */
	public static ItemStack book(RelayLog log) {
		String title = log.isSiteDocument() ? log.title() : "Relay Log #" + log.number();
		if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
			title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		}
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int i = 0; i < log.pages().size(); i++) {
			MutableComponent page = Component.empty();
			if (i == 0) {
				page.append(Component.literal(log.title()).withStyle(ChatFormatting.BOLD)).append("\n");
				if (!log.author().isEmpty()) {
					page.append(Component.literal(log.author()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)).append("\n");
				}
				page.append("\n");
			}
			page.append(Component.literal(log.pages().get(i)));
			pages.add(Filterable.passThrough(page));
		}
		String author = log.author().isEmpty() ? AUTHOR_FALLBACK : log.author();
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		// Generation 3 is "tattered": vanilla refuses to copy it, which suits a one-off recovered transmission.
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 3, pages, true));
		return stack;
	}

	/** Test hook: replaces the loaded logs. */
	public static void setForTesting(Map<Identifier, RelayLog> logs) {
		install(logs);
	}

	private static void install(Map<Identifier, RelayLog> logs) {
		Map<Identifier, RelayLog> all = new TreeMap<>(logs);
		List<RelayLog> numbered = new ArrayList<>(all.values().stream().filter(l -> !l.isSiteDocument()).toList());
		numbered.sort(Comparator.comparingInt(RelayLog::number));
		for (int i = 1; i < numbered.size(); i++) {
			if (numbered.get(i).number() == numbered.get(i - 1).number()) {
				AshfallArms.LOGGER.warn("Two Relay Logs share number {}; players can only collect one of them", numbered.get(i).number());
			}
		}
		byId = Map.copyOf(all);
		pool = List.copyOf(numbered);
	}

	/** Loads {@code data/<ns>/ashfall/relay_logs/**.json}. */
	public static final class Loader extends SimpleJsonResourceReloadListener<RelayLog> {
		public Loader() {
			super(RelayLog.CODEC, FileToIdConverter.json("ashfall/relay_logs"));
		}

		@Override
		protected void apply(Map<Identifier, RelayLog> loaded, ResourceManager manager, ProfilerFiller profiler) {
			install(loaded);
			AshfallArms.LOGGER.info("Loaded {} Relay Logs and {} site documents", pool.size(), byId.size() - pool.size());
		}
	}
}
