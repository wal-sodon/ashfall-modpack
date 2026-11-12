package xyz.toesinthesand.ashfall.director.skills;

import java.util.Locale;

/**
 * The role a player picks when they first join. Each class's core skills (set in {@link SkillRules}) train further
 * and cheaper than everyone else's; every other skill stops at the general max rank.
 */
public enum SurvivorClass {
	MEDIC, BRAWLER, SNIPER, TANK, SCOUT, ENGINEER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The class with this id ({@code "medic"}), or null. */
	public static SurvivorClass byId(String id) {
		for (SurvivorClass c : values()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}
}
