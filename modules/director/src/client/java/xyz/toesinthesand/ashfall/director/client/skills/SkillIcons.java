package xyz.toesinthesand.ashfall.director.client.skills;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.SkillRules;
import xyz.toesinthesand.ashfall.director.skills.SurvivorClass;

import java.util.Locale;

/** The vanilla items that stand in as skill and class icons, and how a rank's effect is written. */
final class SkillIcons {
	private SkillIcons() {
	}

	static ItemStack of(Skill skill) {
		return new ItemStack(switch (skill) {
			case HARDY -> Items.IRON_CHESTPLATE;
			case FLEET_FOOT -> Items.FEATHER;
			case BRAWLER -> Items.IRON_SWORD;
			case STEADY_AIM -> Items.SPYGLASS;
			case HEADHUNTER -> Items.SKELETON_SKULL;
			case BULWARK -> Items.SHIELD;
			case FIELD_MEDIC -> Items.PAPER;
			case IRON_GUT -> Items.GLASS_BOTTLE;
			case IMMUNE_SYSTEM -> Items.GLISTERING_MELON_SLICE;
			case LIGHT_STEP -> Items.LEATHER_BOOTS;
			case PACK_RAT -> Items.CHEST;
			case TINKER -> Items.IRON_PICKAXE;
		});
	}

	static ItemStack of(SurvivorClass c) {
		return new ItemStack(switch (c) {
			case MEDIC -> Items.GOLDEN_APPLE;
			case BRAWLER -> Items.IRON_AXE;
			case SNIPER -> Items.SPYGLASS;
			case TANK -> Items.SHIELD;
			case SCOUT -> Items.COMPASS;
			case ENGINEER -> Items.IRON_PICKAXE;
		});
	}

	/** One rank's effect as shown: hearts for Hardy, armour points for Bulwark, whole percent otherwise. */
	static String perRank(SkillRules rules, Skill skill) {
		double value = rules.perRank(skill);
		if (skill == Skill.HARDY) {
			value /= 2.0;
		} else if (skill != Skill.BULWARK) {
			value *= 100.0;
		}
		String s = String.format(Locale.ROOT, "%.1f", value);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}
}
