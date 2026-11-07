package xyz.toesinthesand.ashfall.arms.finale;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * A run together with its numbers, how it is saved and what its milestones do to the world. The world's one Black
 * Start is an instance over {@link FinaleState}; gametests make their own instances over their own structure with
 * tiny numbers and recording effects, so they never touch the real run, the real Grid % or each other (gametests run
 * in parallel).
 *
 * @param run      the run, changed in place
 * @param settings its numbers (the datapack file for the real run)
 * @param save     marks the run's storage dirty after a change
 * @param effects  what phase ends and the finish do beyond the run itself
 */
public record FinaleInstance(FinaleRun run, Supplier<FinaleSettings> settings, Runnable save, Effects effects) {
	/** The world side of the milestones; the real run lights the city, tests only record. */
	public interface Effects {
		/**
		 * A phase ended: light band {@code band} (0-2) and set the Grid % bonus.
		 */
		void phaseEnded(ServerLevel level, FinaleInstance instance, int band, int bonus);

		/** The Heart phase ended: the ending choice opens for {@code participants}. */
		void completed(ServerLevel level, FinaleInstance instance, Collection<ServerPlayer> participants);

		/** An admin jump or abort changed the bonus without a phase end. */
		void bonusChanged(ServerLevel level, int bonus);

		/**
		 * An admin jump skipped the phase that lights band {@code band} (0-2): light it without the phase end's title,
		 * flash and bonus (the jump sets the bonus once). A no-op by default, for test recorders that don't care.
		 */
		default void bandRelit(ServerLevel level, FinaleInstance instance, int band) {
		}
	}

	public FinaleSettings s() {
		return settings.get();
	}
}
