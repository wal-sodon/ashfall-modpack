package xyz.toesinthesand.ashfall.director.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.director.hydration.Hydration;
import xyz.toesinthesand.ashfall.director.hydration.ThirstData;
import xyz.toesinthesand.ashfall.director.infection.Infection;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.Stimulus;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.skills.Skill;
import xyz.toesinthesand.ashfall.director.skills.SkillData;
import xyz.toesinthesand.ashfall.director.skills.SkillRules;
import xyz.toesinthesand.ashfall.director.skills.SurvivorClass;
import xyz.toesinthesand.ashfall.director.skills.Skills;
import xyz.toesinthesand.ashfall.director.skills.XpMath;
import xyz.toesinthesand.ashfall.director.spawn.DirectorZombie;
import xyz.toesinthesand.ashfall.director.special.SpecialInfected;
import xyz.toesinthesand.ashfall.director.special.SpecialInfectedEntity;
import xyz.toesinthesand.ashfall.director.special.SpecialKind;
import xyz.toesinthesand.ashfall.salvage.loot.LootRefill;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Survivor Skills: buying ranks with XP points, the attribute skills, keeping skills through death, and each hook
 * the other skills read (thirst, infection, noise, special infected XP, the Pack Rat loot chance).
 */
public class SkillsGameTest {
	private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.getAbilities().invulnerable = false;
		player.getAbilities().instabuild = false;
		return player;
	}

	private static void remove(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	private static void points(ServerPlayer player, int points) {
		int level = XpMath.levelFor(points);
		player.setExperienceLevels(level);
		player.setExperiencePoints(points - XpMath.pointsAtLevel(level));
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1e-4, what + ": expected " + expected + ", was " + actual);
	}

	@GameTest
	public void xpCurveMatchesVanilla(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "skills-curve");
		try {
			player.setExperienceLevels(0);
			player.setExperiencePoints(0);
			for (int i = 0; i < 40; i++) {
				helper.assertValueEqual(XpMath.pointsForNextLevel(player.experienceLevel), player.getXpNeededForNextLevel(),
						"points for the next level at " + player.experienceLevel);
				player.giveExperienceLevels(1);
			}
			helper.assertValueEqual(XpMath.pointsAtLevel(30), 1395, "points at level 30");
			helper.assertValueEqual(XpMath.levelFor(1394), 29, "level for 1394 points");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void classSetsHowFarAndHowCheapSkillsTrain(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "skills-buyer");
		try {
			points(player, 1000);
			helper.assertValueEqual(Skills.buy(player, Skill.HARDY), Skills.BuyResult.NO_CLASS, "no class, no training");
			helper.assertValueEqual(Skills.choose(player, SurvivorClass.MEDIC), Skills.ChooseResult.CHOSEN, "first pick");

			points(player, 100);
			helper.assertValueEqual(Skills.buy(player, Skill.FIELD_MEDIC), Skills.BuyResult.BOUGHT, "a core skill");
			helper.assertValueEqual(Skills.points(player), 60, "core rank 1 costs 40");
			helper.assertValueEqual(Skills.buy(player, Skill.HARDY), Skills.BuyResult.BOUGHT, "a general skill");
			helper.assertValueEqual(Skills.points(player), 0, "general rank 1 costs 60");
			helper.assertValueEqual(Skills.buy(player, Skill.HARDY), Skills.BuyResult.NOT_ENOUGH_XP, "general rank 2 costs 180");

			points(player, XpMath.pointsAtLevel(30));
			helper.assertValueEqual(Skills.buy(player, Skill.HARDY), Skills.BuyResult.BOUGHT, "general rank 2 at level 30");
			helper.assertValueEqual(Skills.points(player), 1395 - 180, "the same 180 points at level 30");
			helper.assertValueEqual(Skills.buy(player, Skill.HARDY), Skills.BuyResult.MAX_RANK, "a general skill stops at rank 2");
			for (int rank = 2; rank <= 5; rank++) {
				points(player, 1000);
				helper.assertValueEqual(Skills.buy(player, Skill.FIELD_MEDIC), Skills.BuyResult.BOUGHT, "core rank " + rank);
			}
			helper.assertValueEqual(Skills.buy(player, Skill.FIELD_MEDIC), Skills.BuyResult.MAX_RANK, "a core skill stops at rank 5");
			helper.assertValueEqual(Skills.get(player).spent(), 40 + 60 + 180 + 100 + 180 + 280 + 400, "points spent");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void changingClassNeedsHomeAndTimeAndRefundsHalf(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "skills-changer");
		try {
			Skills.choose(player, SurvivorClass.TANK);
			points(player, 400);
			Skills.buy(player, Skill.HARDY);
			Skills.buy(player, Skill.HARDY);
			int spent = Skills.get(player).spent();
			helper.assertValueEqual(spent, 140, "two core ranks");
			int held = Skills.points(player);

			helper.assertValueEqual(Skills.choose(player, SurvivorClass.TANK), Skills.ChooseResult.SAME_CLASS, "same class");
			Skills.setHomeCheckForTesting(p -> false);
			helper.assertValueEqual(Skills.choose(player, SurvivorClass.SCOUT), Skills.ChooseResult.NOT_AT_HOME, "away from home");
			Skills.setHomeCheckForTesting(p -> true);
			helper.assertValueEqual(Skills.choose(player, SurvivorClass.SCOUT), Skills.ChooseResult.CHANGED, "at home");
			helper.assertValueEqual(Skills.survivorClass(player), SurvivorClass.SCOUT, "new class");
			helper.assertValueEqual(Skills.get(player).totalRanks(), 0, "ranks cleared");
			helper.assertValueEqual(Skills.points(player), held + spent / 2, "half the spent XP back");
			near(helper, player.getMaxHealth(), 20.0, "Hardy gone with the old class");
			helper.assertValueEqual(Skills.choose(player, SurvivorClass.MEDIC), Skills.ChooseResult.TOO_SOON, "a week between changes");
		} finally {
			Skills.setHomeCheckForTesting(null);
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void attributeSkillsApplyOnceAndSurviveDeath(GameTestHelper helper) {
		ServerPlayer dead = survivalPlayer(helper, "skills-dead");
		ServerPlayer respawned = survivalPlayer(helper, "skills-respawned");
		try {
			double speed = dead.getAttributeValue(Attributes.MOVEMENT_SPEED);
			double armor = dead.getAttributeValue(Attributes.ARMOR);
			Skills.set(dead, SkillData.EMPTY.asClass(SurvivorClass.TANK, -1L).with(Skill.HARDY, 2).with(Skill.FLEET_FOOT, 1)
					.with(Skill.BULWARK, 3));
			Skills.applyAttributes(dead, false);
			Skills.applyAttributes(dead, false);
			near(helper, dead.getMaxHealth(), 24.0, "max health with Hardy 2 (applied twice)");
			near(helper, dead.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.03, "speed with Fleet Foot 1");
			near(helper, dead.getAttributeValue(Attributes.ARMOR), armor + 3.0, "armour with Bulwark 3");

			ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(dead, respawned, false);
			helper.assertValueEqual(Skills.survivorClass(respawned), SurvivorClass.TANK, "class after death");
			helper.assertValueEqual(Skills.rank(respawned, Skill.HARDY), 2, "Hardy after death");
			near(helper, respawned.getMaxHealth(), 24.0, "max health after death");
			near(helper, respawned.getHealth(), 24.0, "respawned at the new full health");

			Skills.set(respawned, SkillData.EMPTY);
			near(helper, respawned.getMaxHealth(), 20.0, "max health after a reset");
			helper.assertTrue(respawned.getHealth() <= 20.0f, "health clamped to the lower max");
		} finally {
			remove(dead);
			remove(respawned);
		}
		helper.succeed();
	}

	@GameTest
	public void ranksOutsideTheClassAreCapped(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "skills-capped");
		try {
			Skills.set(player, SkillData.EMPTY.asClass(SurvivorClass.SCOUT, -1L).with(Skill.STEADY_AIM, 5).with(Skill.PACK_RAT, 5));
			helper.assertValueEqual(Skills.rank(player, Skill.STEADY_AIM), 2, "a general skill counts up to rank 2");
			helper.assertValueEqual(Skills.rank(player, Skill.PACK_RAT), 5, "a core skill counts up to rank 5");
		} finally {
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void reductionSkillsFeedTheirSystems(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "skills-hooks");
		ServerLevel level = helper.getLevel();
		Difficulty old = level.getServer().getWorldData().getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		try {
			float before = Infection.resistanceFactor(player);
			// Scout: Light Step and Pack Rat are core; Immune System and Iron Gut general (max 2)
			Skills.set(player, SkillData.EMPTY.asClass(SurvivorClass.SCOUT, -1L).with(Skill.IMMUNE_SYSTEM, 2).with(Skill.IRON_GUT, 1)
					.with(Skill.LIGHT_STEP, 2).with(Skill.PACK_RAT, 3));
			near(helper, Infection.resistanceFactor(player), before * 0.80, "infection rolls with Immune System 2");

			Hydration.set(player, new ThirstData(20, 5.0f, 0.0f));
			Hydration.onFoodExhaustion(player, 1.0f);
			near(helper, Hydration.get(player).exhaustion(), Hydration.ACTIVITY_MULTIPLIER * 0.88, "thirst exhaustion with Iron Gut 1");

			Stimulus quiet = Stimuli.emit(level, player.position(), StimulusType.SOUND, 20.0, player);
			near(helper, quiet.radius(), 16.0, "footstep radius with Light Step 2");
			Stimulus light = Stimuli.emit(level, player.position(), StimulusType.LIGHT, 20.0, player);
			near(helper, light.radius(), 20.0, "Light Step leaves light alone");

			near(helper, LootRefill.extraRollChance(player), 0.30, "Pack Rat 3 extra-roll chance");
		} finally {
			level.getServer().setDifficulty(old, true);
			remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void specialInfectedDropMoreXp(GameTestHelper helper) {
		helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
		SpecialInfectedEntity brute = SpecialInfected.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)),
				SpecialKind.BRUTE, DirectorZombie.Source.SIEGE);
		helper.assertTrue(brute != null, "brute did not spawn");
		ServerPlayer player = survivalPlayer(helper, "skills-xp");
		try {
			Method base = SpecialInfectedEntity.class.getDeclaredMethod("getBaseExperienceReward", ServerLevel.class);
			base.setAccessible(true);
			helper.assertValueEqual(base.invoke(brute, helper.getLevel()), 25, "brute XP");
			helper.assertValueEqual(SkillRules.get().specialXp(SpecialKind.BRUTE), 25, "shipped brute XP");
			brute.discard();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		} finally {
			remove(player);
		}
		helper.succeed();
	}
}
