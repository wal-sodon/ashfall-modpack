package xyz.toesinthesand.ashfall.arms.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * One written fragment from {@code data/<ns>/ashfall/relay_logs/**.json}.
 * <p>
 * Numbered logs (1..n) are the pool Power Relays hand out; a log with {@code number} 0 is a site document,
 * found only in a landmark cache through the {@code ashfall:relay_document} loot function and never picked
 * by a relay. Text is plain strings rather than components so writers can edit the files without knowing
 * the component format; the book builder adds the styling.
 *
 * @param number the log's number in the pool, or 0 for a site document
 * @param title  shown on the first page and, for site documents, as the book title
 * @param author who recorded it, shown under the title
 * @param pages  one string per book page (keep each under about 230 characters so it fits)
 */
public record RelayLog(int number, String title, String author, List<String> pages) {
	public static final Codec<RelayLog> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 999).optionalFieldOf("number", 0).forGetter(RelayLog::number),
			Codec.STRING.fieldOf("title").forGetter(RelayLog::title),
			Codec.STRING.optionalFieldOf("author", "").forGetter(RelayLog::author),
			Codec.STRING.listOf(1, 20).fieldOf("pages").forGetter(RelayLog::pages)
	).apply(i, RelayLog::new));

	public boolean isSiteDocument() {
		return number == 0;
	}
}
