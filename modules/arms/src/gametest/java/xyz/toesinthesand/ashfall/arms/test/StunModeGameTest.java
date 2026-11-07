package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.entity.ArmsEntities;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.gun.ArmsComponents;
import xyz.toesinthesand.ashfall.arms.gun.FireMode;
import xyz.toesinthesand.ashfall.arms.gun.GunLogic;
import xyz.toesinthesand.ashfall.arms.gun.GunState;
import xyz.toesinthesand.ashfall.arms.gun.GunStatsManager;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.item.EnergyItems;
import xyz.toesinthesand.ashfall.director.boss.BossLairs;
import xyz.toesinthesand.ashfall.director.senses.SenseGoal;
import xyz.toesinthesand.ashfall.director.senses.SenseGoals;
import xyz.toesinthesand.ashfall.director.senses.Stimuli;
import xyz.toesinthesand.ashfall.director.senses.StimulusType;
import xyz.toesinthesand.ashfall.director.senses.Stun;
import xyz.toesinthesand.ashfall.director.special.SpecialEntities;

import java.util.ArrayList;
import java.util.List;

/**
 * Stun mode: silent bolts that hold a mob instead of hurting it, their energy and heat cost, the stun's durations
 * and lockout, a stunned zombie's dead senses, and the fire mode switch.
 */
public class StunModeGameTest {
	private static ItemStack loadedGun(Item gun, Item pack, long energy) {
		ItemStack stack = new ItemStack(gun);
		stack.set(ArmsComponents.LOADED_PACK, ItemStackTemplate.fromNonEmptyStack(EnergyItems.withEnergy(new ItemStack(pack), energy)));
		return stack;
	}

	private static ItemStack stunGun(Item gun, Item pack, long energy) {
		ItemStack stack = loadedGun(gun, pack, energy);
		stack.set(ArmsComponents.FIRE_MODE, FireMode.STUN);
		return stack;
	}

	private static long loadedEnergy(ItemStack gun) {
		return EnergyItems.stored(gun.get(ArmsComponents.LOADED_PACK));
	}

	/** A shooter at (1.5, 2, 1.5) facing south (+z). */
	private static Player shooter(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		player.setPos(pos.x, pos.y, pos.z);
		player.setYRot(0f);
		player.setXRot(0f);
		return player;
	}

	/** SOUND stimuli whose source lies inside this test's own bounds (neighbouring tests make their own noise). */
	private static List<String> soundsInside(GameTestHelper helper) {
		AABB bounds = helper.getBounds();
		List<String> found = new ArrayList<>();
		Stimuli.forEachActive(helper.getLevel(), s -> {
			if (s.type() == StimulusType.SOUND && bounds.contains(s.x(), s.y(), s.z())) {
				found.add(s.toString());
			}
		});
		return found;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		return helper.spawn(EntityTypes.HUSK, new Vec3(x, 2, z));
	}

	@GameTest(maxTicks = 60)
	public void stunBoltNoDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player owner = shooter(helper);
		Husk target = husk(helper, 1.5, 5.5);
		target.setNoAi(true);
		float max = target.getMaxHealth();
		// Even a bolt that carries damage does none in stun mode.
		Vec3 from = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		EnergyBolt bolt = EnergyBolt.create(level, owner, new Vec3(from.x, target.getY() + 0.5, from.z), 6f, 1.75f, 96f, 0xffffff);
		bolt.setStun(true);
		bolt.shoot(0, 0, 1, 1.0f, 0f);
		level.addFreshEntity(bolt);
		helper.succeedWhen(() -> {
			helper.assertTrue(bolt.isRemoved(), "the stun bolt should have hit");
			helper.assertTrue(Stun.isStunned(target), "the husk should be stunned");
			helper.assertValueEqual(target.getHealth(), max, "husk health after a stun bolt");
			helper.assertTrue(target.getLastHurtByMob() == null, "a stun bolt must not anger its target");
			helper.assertTrue(target.getTarget() == null, "a stunned husk has no target");
			helper.assertTrue(target.getDeltaMovement().horizontalDistanceSqr() < 1e-6, "no knockback");
		});
	}

	@GameTest(maxTicks = 60)
	public void stunSilentOnHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		Husk target = husk(helper, 1.5, 4.5);
		target.setNoAi(true);
		ItemStack gun = stunGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
		helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.FIRED, "stun shot");
		helper.assertTrue(soundsInside(helper).isEmpty(), "a stun shot made noise: " + soundsInside(helper));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getEntities(ArmsEntities.ENERGY_BOLT).isEmpty(), "the bolt is still flying");
			helper.assertTrue(Stun.isStunned(target), "the husk should be stunned");
			helper.assertTrue(soundsInside(helper).isEmpty(), "a stun hit made noise: " + soundsInside(helper));
		});
	}

	@GameTest(maxTicks = 60)
	public void stunSilentOnMiss(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(1, y, 5), Blocks.STONE);
			helper.setBlock(new BlockPos(4, y, 5), Blocks.STONE);
		}
		Player player = shooter(helper);
		ItemStack gun = stunGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
		helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.FIRED, "stun shot");
		// Control: an ordinary bolt hitting the other wall leaves the vanilla landing noise, so the check can see one.
		Vec3 from = helper.absoluteVec(new Vec3(4.5, 3, 1.5));
		EnergyBolt control = EnergyBolt.create(level, null, from, 4f, 1.75f, 96f, 0xffffff);
		control.shoot(0, 0, 1, 1.0f, 0f);
		level.addFreshEntity(control);
		AABB stunSide = new AABB(helper.absoluteVec(new Vec3(0, 0, 0)), helper.absoluteVec(new Vec3(3, 8, 8)));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getEntities(ArmsEntities.ENERGY_BOLT).isEmpty(), "bolts still flying");
			List<String> landing = new ArrayList<>();
			List<String> stunNoise = new ArrayList<>();
			Stimuli.forEachActive(level, s -> {
				if (s.type() != StimulusType.SOUND || !helper.getBounds().contains(s.x(), s.y(), s.z())) {
					return;
				}
				(stunSide.contains(s.x(), s.y(), s.z()) ? stunNoise : landing).add(s.toString());
			});
			helper.assertTrue(!landing.isEmpty(), "the control bolt should leave a landing noise");
			helper.assertTrue(stunNoise.isEmpty(), "a stun bolt hitting a wall made noise: " + stunNoise);
		});
	}

	@GameTest
	public void stunTripleEnergyHeat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = shooter(helper);
		player.setXRot(90f); // straight down into the floor, so no bolt flies off into a neighbouring test
		record Case(Item gun, Item pack, long energy, float heat) {
		}
		// Spark: 1 energy x3, 12 heat x1.5. Ion Rifle: 3 x3, 14 x1.5.
		for (Case c : List.of(new Case(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 3, 18f),
				new Case(ArmsItems.ION_RIFLE, ArmsItems.CELL_PACK_STANDARD, 9, 21f))) {
			ItemStack gun = stunGun(c.gun(), c.pack(), 30);
			helper.assertValueEqual(GunLogic.tryFire(level, player, gun), GunLogic.FireResult.FIRED, c.gun() + " stun shot");
			helper.assertValueEqual(loadedEnergy(gun), 30 - c.energy(), c.gun() + " energy after a stun shot");
			helper.assertTrue(Math.abs(gun.get(ArmsComponents.GUN_STATE).heat() - c.heat()) < 1e-4,
					c.gun() + " heat after a stun shot: " + gun.get(ArmsComponents.GUN_STATE).heat());
		}
		// Volt: 1.5 x3 = 4.5, paid in whole units with the half carried as credit.
		ItemStack volt = stunGun(ArmsItems.VOLT_SIDEARM, ArmsItems.CELL_PACK_SMALL, 30);
		GunLogic.tryFire(level, player, volt);
		GunState after = volt.get(ArmsComponents.GUN_STATE);
		helper.assertTrue(Math.abs((30 - loadedEnergy(volt)) - after.energyCredit() - 4.5f) < 1e-4,
				"volt stun shot should cost 4.5, drew " + (30 - loadedEnergy(volt)) + " with credit " + after.energyCredit());
		// The bolt is a pale, damageless stun bolt.
		helper.assertTrue(helper.getEntities(ArmsEntities.ENERGY_BOLT).stream()
				.allMatch(b -> b.isStun() && b.getDamage() == 0f && b.getColor() == GunLogic.STUN_BOLT_COLOR), "stun bolts");
		// Damage mode on the same gun is the ordinary price.
		ItemStack plain = loadedGun(ArmsItems.SPARK_PISTOL, ArmsItems.CELL_PACK_SMALL, 30);
		GunLogic.tryFire(level, player, plain);
		helper.assertValueEqual(loadedEnergy(plain), 29L, "spark damage shot energy");
		// A gun without stun mode ignores a STUN component (say, after a datapack removed it).
		ItemStack lancer = stunGun(ArmsItems.LANCER_LONGRIFLE, ArmsItems.CELL_PACK_STANDARD, 60);
		helper.assertTrue(!GunLogic.isStunMode(lancer, GunStatsManager.get(false, ArmsItems.LANCER_LONGRIFLE)), "lancer has no stun mode");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void stunFreezes60Ticks(GameTestHelper helper) {
		Husk husk = husk(helper, 2.5, 2.5);
		Player prey = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 preyPos = helper.absoluteVec(new Vec3(2.5, 2, 6.5));
		prey.setPos(preyPos.x, preyPos.y, preyPos.z);
		husk.getNavigation().moveTo(preyPos.x, preyPos.y, preyPos.z, 1.0);

		helper.assertValueEqual(Stun.tryStun(husk, null), Stun.STUN_TICKS, "stun ticks");
		helper.assertValueEqual(Stun.STUN_TICKS, 60, "a stun holds for 3 s");
		helper.assertTrue(husk.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1e-6, "stunned husk speed");
		helper.assertTrue(husk.getNavigation().isDone(), "the stun drops the husk's path");
		husk.setTarget(prey);
		helper.assertTrue(husk.getTarget() == null, "a stunned husk can't take a target");
		Vec3 start = husk.position();
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Stun.isStunned(husk), "still stunned at 40 ticks");
			double moved = husk.position().subtract(start).horizontalDistance();
			helper.assertTrue(moved < 0.05, "the stunned husk walked " + moved + " blocks");
			helper.assertTrue(husk.getTarget() == null, "the stunned husk found a target");
		});
		helper.runAfterDelay(62, () -> {
			helper.assertTrue(!Stun.isStunned(husk), "the stun should wear off after 60 ticks");
			helper.assertTrue(husk.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.0, "speed comes back");
			husk.setTarget(prey);
			helper.assertTrue(husk.getTarget() == prey, "a recovered husk can target again");
			helper.succeed();
		});
	}

	@GameTest
	public void stunRestunCooldown(GameTestHelper helper) {
		Husk husk = husk(helper, 2.5, 2.5);
		husk.setNoAi(true);
		helper.assertValueEqual(Stun.tryStun(husk, null), 60, "first stun");
		helper.assertValueEqual(Stun.tryStun(husk, null), 0, "no stacking on a stunned husk");
		husk.removeEffect(Stun.STUNNED);
		helper.assertValueEqual(Stun.tryStun(husk, null), 0, "re-stun inside the lockout");
		long now = helper.getLevel().getGameTime();
		helper.assertValueEqual(husk.getAttached(Stun.LOCKED_UNTIL), now + Stun.STUN_TICKS + Stun.LOCKOUT_TICKS, "lockout end");
		husk.setAttached(Stun.LOCKED_UNTIL, now);
		helper.assertValueEqual(Stun.tryStun(husk, null), 60, "stun again once the lockout is over");
		helper.succeed();
	}

	@GameTest
	public void stunSpecialAndBossDurations(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Husk husk = husk(helper, 1.5, 1.5);
		husk.setNoAi(true);
		helper.assertValueEqual(Stun.durationFor(husk), 60, "plain zombie");

		var crawler = helper.spawn(SpecialEntities.CRAWLER, new Vec3(3.5, 2, 1.5));
		crawler.setNoAi(true);
		helper.assertValueEqual(Stun.tryStun(crawler, player), 30, "special infected stun");

		Husk boss = husk(helper, 5.5, 1.5);
		boss.setNoAi(true);
		boss.addTag(BossLairs.BOSS_TAG);
		helper.assertValueEqual(Stun.tryStun(boss, player), 10, "lair boss stun");

		// Every non-friendly mob can be stunned; pets and players never.
		Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(1.5, 2, 4.5));
		pig.setNoAi(true);
		helper.assertValueEqual(Stun.tryStun(pig, player), 60, "a pig is not friendly");
		Wolf wolf = helper.spawn(EntityTypes.WOLF, new Vec3(3.5, 2, 4.5));
		wolf.tame(player);
		helper.assertValueEqual(Stun.tryStun(wolf, player), 0, "a tamed wolf is never stunned");
		helper.assertValueEqual(Stun.tryStun(player, null), 0, "a player is never stunned");
		helper.succeed();
	}

	@GameTest
	public void stunnedZombieIgnoresStimuli(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Husk husk = husk(helper, 1.5, 1.5);
		husk.setNoAi(true);
		SenseGoal goal = SenseGoals.attach(husk, 1.0);
		Stimuli.emit(level, helper.absoluteVec(new Vec3(5.5, 2, 5.5)), StimulusType.SOUND, 16, null);
		helper.assertTrue(goal.pickStimulus() != null, "an alert husk hears the noise");
		Stun.tryStun(husk, null);
		helper.assertTrue(goal.pickStimulus() == null, "a stunned husk must hear nothing");
		helper.succeed();
	}

	@GameTest
	public void toggleOnlyStunCapable(GameTestHelper helper) {
		Player player = shooter(helper);
		for (Item item : List.of(ArmsItems.SPARK_PISTOL, ArmsItems.VOLT_SIDEARM, ArmsItems.ION_RIFLE)) {
			ItemStack gun = new ItemStack(item);
			helper.assertValueEqual(GunLogic.toggleMode(player, gun), FireMode.STUN, item + " first toggle");
			helper.assertValueEqual(GunLogic.fireMode(gun), FireMode.STUN, item + " saved mode");
			helper.assertValueEqual(GunLogic.toggleMode(player, gun), FireMode.DAMAGE, item + " second toggle");
			helper.assertTrue(!gun.has(ArmsComponents.FIRE_MODE), item + " back to damage leaves no component");
		}
		for (Item item : List.of(ArmsItems.ARC_MAGNUM, ArmsItems.PULSE_CARBINE, ArmsItems.LANCER_LONGRIFLE, ArmsItems.STORM_REPEATER)) {
			ItemStack gun = new ItemStack(item);
			helper.assertTrue(GunLogic.toggleMode(player, gun) == null, item + " has no stun mode");
			helper.assertTrue(!gun.has(ArmsComponents.FIRE_MODE), item + " got a fire mode");
		}
		helper.assertTrue(GunLogic.toggleMode(player, new ItemStack(ArmsItems.CROWBAR)) == null, "a crowbar has no fire mode");
		helper.succeed();
	}
}
