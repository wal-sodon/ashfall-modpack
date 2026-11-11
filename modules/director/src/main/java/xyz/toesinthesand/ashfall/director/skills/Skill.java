package xyz.toesinthesand.ashfall.director.skills;

import java.util.Locale;

/**
 * The Survivor Skills a player buys with XP. How far a player can train one depends on their {@link SurvivorClass}:
 * a class's core skills go further and cost less (see {@link SkillRules}). "Bonus" skills add {@code rank * perRank}
 * (a fraction, or half-hearts / armour points); "reduction" skills take {@code rank * perRank} off something bad,
 * never below nothing.
 */
public enum Skill {
	/** Extra max health (half-hearts per rank). */
	HARDY,
	/** Faster movement. */
	FLEET_FOOT,
	/** More melee damage, Plasma Blades included (they scale from the attack attribute). */
	BRAWLER,
	/** More blaster bolt damage. */
	STEADY_AIM,
	/** Bigger blaster headshot multiplier. */
	HEADHUNTER,
	/** Extra armour points. */
	BULWARK,
	/** Bandages heal more. */
	FIELD_MEDIC,
	/** Thirst drains slower and dirty water is less risky. */
	IRON_GUT,
	/** Every infection roll is less likely to take. */
	IMMUNE_SYSTEM,
	/** The sounds a player makes reach fewer zombies. */
	LIGHT_STEP,
	/** A chance of an extra loot roll in salvage containers. */
	PACK_RAT,
	/** Fortify repairs restore more. */
	TINKER;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The skill with this id ({@code "iron_gut"}), or null. */
	public static Skill byId(String id) {
		for (Skill skill : values()) {
			if (skill.id().equals(id)) {
				return skill;
			}
		}
		return null;
	}
}
