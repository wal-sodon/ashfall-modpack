package xyz.toesinthesand.ashfall.director.infection;

import net.minecraft.server.level.ServerPlayer;
import net.petemc.contagion.casts.InfectedEntity;

/**
 * The only class that touches Contagion's types. Contagion is All Rights Reserved and compile-only, so
 * this class is loaded only after {@link Infection#CONTAGION_LOADED} says the mod is present, and it only
 * calls Contagion's public player interface ({@code InfectedEntity}); none of its code is copied.
 * <p>
 * Contagion models an infection as one countdown to death. Ashfall's timeline has pauses and knock-backs,
 * which a single countdown can't express, so Ashfall's attachment is the source of truth and this bridge
 * writes the matching countdown into Contagion every tick. That keeps Contagion's own view (its HUD, its
 * "infectious" state late in the infection) in step, and keeps its death branch from ever firing on its
 * own: Ashfall decides when the infection kills.
 */
final class ContagionBridge {
	/**
	 * Contagion kills when its countdown reaches 2. Its effect ticks during the entity tick, before our
	 * end-of-tick write, so the countdown is never allowed to fall near that point; Ashfall's own death
	 * check is what ends the infection.
	 */
	static final long MIN_COUNTDOWN = 40L;

	private ContagionBridge() {
	}

	/** Writes Ashfall's remaining time into Contagion's countdown. */
	static void sync(ServerPlayer player, long remainingTicks, long totalTicks) {
		if (player instanceof InfectedEntity infected) {
			infected.contagion_setInitialInfectionDuration(Math.max(totalTicks, MIN_COUNTDOWN));
			infected.contagion_setInfectionTicks(Math.max(remainingTicks, MIN_COUNTDOWN));
		}
	}
}
