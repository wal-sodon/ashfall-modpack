package xyz.toesinthesand.ashfall.arms.story;

/**
 * The ending vote's arithmetic (design decision 3: majority of online party members, 30-second window), free of game
 * state so JUnit pins every case down.
 * <ul>
 * <li><b>Early end.</b> As soon as one option holds a strict majority of the eligible voters (the participants still
 * online), it wins: nobody waits out the timer for a vote that cannot change.</li>
 * <li><b>Everyone voted, or the window closed.</b> The option with more votes wins. A tie, or nobody voting at all,
 * re-prompts once; in the second round the same tie or silence goes to Sever, the rebuild ending, which is the safe
 * default because it makes the world easier rather than harder.</li>
 * </ul>
 */
public final class VoteTally {
	/** What the vote says now. */
	public enum Outcome {
		/** Keep waiting. */
		PENDING,
		SEVER,
		BIND,
		/** A tie or no votes in the first round: ask again. */
		REPROMPT;

		public StoryEnding ending() {
			return switch (this) {
				case SEVER -> StoryEnding.SEVER;
				case BIND -> StoryEnding.BIND;
				default -> StoryEnding.NONE;
			};
		}
	}

	private VoteTally() {
	}

	/**
	 * @param sever      Sever votes from eligible voters
	 * @param bind       Bind votes from eligible voters
	 * @param eligible   participants still online (at least 1 is assumed)
	 * @param windowOver the round's timer has run out
	 * @param finalRound this is the re-prompt round, after which a tie or silence defaults to Sever
	 */
	public static Outcome decide(int sever, int bind, int eligible, boolean windowOver, boolean finalRound) {
		int voters = Math.max(1, eligible);
		sever = Math.max(0, sever);
		bind = Math.max(0, bind);
		if (sever * 2 > voters) {
			return Outcome.SEVER;
		}
		if (bind * 2 > voters) {
			return Outcome.BIND;
		}
		if (!windowOver && sever + bind < voters) {
			return Outcome.PENDING;
		}
		if (sever > bind) {
			return Outcome.SEVER;
		}
		if (bind > sever) {
			return Outcome.BIND;
		}
		return finalRound ? Outcome.SEVER : Outcome.REPROMPT;
	}
}
