package xyz.toesinthesand.ashfall.arms.client.grid;

import xyz.toesinthesand.ashfall.director.graffiti.GraffitiPayload;

import java.util.List;

/** The client's copy of the last {@code ashfall:graffiti}: the Graffiti marks to draw on the Grid Map. Cleared on disconnect. */
public final class GraffitiClientState {
	private static volatile List<GraffitiPayload.Mark> marks = List.of();

	private GraffitiClientState() {
	}

	public static void accept(GraffitiPayload payload) {
		marks = List.copyOf(payload.marks());
	}

	public static void clear() {
		marks = List.of();
	}

	public static List<GraffitiPayload.Mark> marks() {
		return marks;
	}
}
