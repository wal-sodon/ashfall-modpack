package xyz.toesinthesand.ashfall.salvage.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Autocomplete ranking for the admin screens' id fields (loot tables, items, entities).
 * <p>
 * Lives on the common side so it is plain, unit-testable Java. Admins usually remember a fragment of a path
 * ("police", "zombie"), not the namespace, so a match anywhere counts, but ids whose path starts with the typed
 * text come first, then ids that start with it including the namespace, then the rest in their original order.
 */
public final class IdFilter {
	private IdFilter() {
	}

	/**
	 * @param ids   candidates, typically sorted
	 * @param query what was typed; blank gives no suggestions
	 * @param max   at most this many results
	 */
	public static List<String> filter(List<String> ids, String query, int max) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty() || max <= 0) {
			return List.of();
		}
		List<String> pathStart = new ArrayList<>(), fullStart = new ArrayList<>(), contains = new ArrayList<>();
		for (String id : ids) {
			if (id.equals(q)) {
				continue;
			}
			int colon = id.indexOf(':');
			String path = colon >= 0 ? id.substring(colon + 1) : id;
			if (path.startsWith(q)) {
				pathStart.add(id);
			} else if (id.startsWith(q)) {
				fullStart.add(id);
			} else if (id.contains(q)) {
				contains.add(id);
			}
			if (pathStart.size() >= max) {
				break;
			}
		}
		List<String> out = new ArrayList<>(max);
		for (List<String> group : List.of(pathStart, fullStart, contains)) {
			for (String id : group) {
				if (out.size() >= max) {
					return out;
				}
				out.add(id);
			}
		}
		return out;
	}
}
