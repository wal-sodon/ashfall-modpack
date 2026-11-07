package xyz.toesinthesand.ashfall.arms.survivor;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A survivor's one job. {@link #SETTLER} is the no-job default (lives at the base, runs from zombies) and {@link #GUARD}
 * fights zombies with the weapon it is handed; both work anywhere. The base roles of build order step 2
 * ({@link #SCAVENGER}, {@link #MEDIC}, {@link #MECHANIC}) work from a home's Supply Lockers, so only a survivor settled
 * at a home takes them ({@link #needsHome}); what each does is in {@link SurvivorWork}. {@link #TRADER} (build order
 * step 3) is never assigned: a trader arrives at a reclaimed district's substation with its own stock and keeps that job
 * ({@link Traders}).
 */
public enum SurvivorRole {
	SETTLER(true, false),
	GUARD(true, false),
	SCAVENGER(true, true),
	MEDIC(true, true),
	MECHANIC(true, true),
	TRADER(false, false);

	private final boolean assignable;
	private final boolean needsHome;

	SurvivorRole(boolean assignable, boolean needsHome) {
		this.assignable = assignable;
		this.needsHome = needsHome;
	}

	/** Whether players can pick this role at all (all but {@link #TRADER}). */
	public boolean assignable() {
		return assignable;
	}

	/** Whether the role only works from a home's Supply Lockers (only a settled survivor takes it). */
	public boolean needsHome() {
		return needsHome;
	}

	/** Lower-case id used in saves, commands and lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("survivor.ashfall.role." + id());
	}

	/** The role with this id, or {@link #SETTLER} for anything unknown (a save from a newer build). */
	public static SurvivorRole byId(String id) {
		for (SurvivorRole role : values()) {
			if (role.id().equals(id)) {
				return role;
			}
		}
		return SETTLER;
	}

	/** The role with this id when it exists, else null (commands report it). */
	public static SurvivorRole parse(String id) {
		for (SurvivorRole role : values()) {
			if (role.id().equals(id)) {
				return role;
			}
		}
		return null;
	}
}
