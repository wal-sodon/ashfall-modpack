package xyz.toesinthesand.ashfall.arms.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.toesinthesand.ashfall.arms.entity.EnergyBolt;
import xyz.toesinthesand.ashfall.arms.entity.Nail;
import xyz.toesinthesand.ashfall.arms.item.ArmsItems;
import xyz.toesinthesand.ashfall.arms.survivor.Survivor;
import xyz.toesinthesand.ashfall.arms.survivor.SurvivorRole;
import xyz.toesinthesand.ashfall.arms.survivor.Survivors;
import xyz.toesinthesand.ashfall.director.fortify.DirectorBlocks;
import xyz.toesinthesand.ashfall.director.pets.Friendly;
import xyz.toesinthesand.ashfall.director.pets.PetGuard;
import xyz.toesinthesand.ashfall.director.traps.SpikeStripBlock;

/**
 * Friendly fire against pets ({@code PetGuard}): a player's melee hit, a player's Energy Bolt and Nail and a spike
 * strip all leave a tamed dog untouched, whoever the player is; a zombie's hit still lands. The projectile hits use
 * the same damage sources the bolt and nail build on impact. A survivor also shrugs off the world damage a player can
 * aim (lava, fire, suffocation, falling anvils and blocks), while a zombie's hit and /kill still get through.
 * <p>
 * The friendly-fire test flips the shared {@code ashfall:friendly_fire} rule and restores it within one synchronous
 * block, so the other tests running on the server never see it on.
 */
public class PetFriendlyFireGameTest {
	@GameTest
	public void playersAndTrapsNeverHurtPets(GameTestHelper helper) {
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
		Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
		Wolf dog = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(1.5, 1, 1.5));
		dog.tame(owner);
		float full = dog.getHealth();

		EnergyBolt bolt = EnergyBolt.create(level, stranger, 4f, 1f, 32f, 0x3cc8ff);
		Nail nail = new Nail(level, owner, new ItemStack(ArmsItems.NAILS));
		DamageSource[] friendly = {
				level.damageSources().playerAttack(owner),
				level.damageSources().playerAttack(stranger),
				new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(EnergyBolt.DAMAGE_TYPE),
						bolt, stranger),
				level.damageSources().thrown(nail, owner),
				level.damageSources().source(SpikeStripBlock.DAMAGE),
		};
		for (DamageSource source : friendly) {
			helper.assertFalse(dog.hurtServer(level, source, 4f), "a pet was hurt by " + source.getMsgId());
			helper.assertTrue(dog.getHealth() == full, "dog health fell to " + dog.getHealth() + " from " + source.getMsgId());
		}
		BlockPos strip = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.setBlock(new BlockPos(1, 1, 1), DirectorBlocks.SPIKE_STRIP);
		SpikeStripBlock.stepOn(level.getBlockState(strip), level, strip, dog);
		helper.assertTrue(dog.getHealth() == full, "the spike strip hurt a pet");

		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 2.5));
		helper.assertTrue(dog.hurtServer(level, level.damageSources().mobAttack(zombie), 2f), "a zombie's hit must land");
		helper.assertTrue(dog.getHealth() < full, "a zombie's hit did no damage");
		bolt.discard();
		nail.discard();
		helper.succeed();
	}

	@GameTest
	public void friendlyFireRuleUnguardsPetsAndPlayersButNotSurvivors(GameTestHelper helper) {
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
		Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
		Wolf dog = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(1.5, 1, 1.5));
		dog.tame(owner);
		Survivor survivor = Survivors.spawn(level, helper.absolutePos(new BlockPos(2, 1, 2)), SurvivorRole.SETTLER, null);
		helper.assertTrue(survivor != null, "no survivor spawned");
		helper.assertFalse(PetGuard.friendlyFire(level), "friendly fire must be off by default");
		helper.assertTrue(Friendly.isFriendly(dog, stranger) && Friendly.isFriendly(owner, stranger), "off: pets and players are friendly");

		boolean dogHurt;
		boolean survivorHurt;
		boolean dogFriendly;
		boolean playerFriendly;
		boolean selfFriendly;
		boolean survivorFriendly;
		level.getGameRules().set(PetGuard.FRIENDLY_FIRE, true, level.getServer());
		try {
			dogFriendly = Friendly.isFriendly(dog, stranger);
			playerFriendly = Friendly.isFriendly(owner, stranger);
			selfFriendly = Friendly.isFriendly(stranger, stranger);
			survivorFriendly = Friendly.isFriendly(survivor, stranger);
			dogHurt = dog.hurtServer(level, level.damageSources().playerAttack(stranger), 2f);
			survivorHurt = survivor.hurtServer(level, level.damageSources().playerAttack(stranger), 2f);
		} finally {
			level.getGameRules().set(PetGuard.FRIENDLY_FIRE, false, level.getServer());
		}
		helper.assertTrue(dogHurt, "on: a player's hit must land on a pet");
		helper.assertFalse(survivorHurt, "on: survivors stay protected");
		helper.assertFalse(dogFriendly, "on: area weapons must hit pets");
		helper.assertFalse(playerFriendly, "on: area weapons must hit other players");
		helper.assertTrue(selfFriendly, "on: the attacker still spares themselves");
		helper.assertTrue(survivorFriendly, "on: area weapons still spare survivors");
		Wolf second = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(2.5, 1, 1.5));
		second.tame(owner);
		helper.assertFalse(second.hurtServer(level, level.damageSources().playerAttack(stranger), 2f), "off again: pets are guarded");
		survivor.discard();
		helper.succeed();
	}

	@GameTest
	public void survivorsIgnoreWorldDamageAPlayerCanAim(GameTestHelper helper) {
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		Survivor survivor = Survivors.spawn(level, helper.absolutePos(new BlockPos(1, 1, 1)), SurvivorRole.SETTLER, null);
		helper.assertTrue(survivor != null, "survivor spawned");
		try {
			float full = survivor.getHealth();
			DamageSource[] world = {
					level.damageSources().lava(),
					level.damageSources().inFire(),
					level.damageSources().onFire(),
					level.damageSources().hotFloor(),
					level.damageSources().inWall(),
					level.damageSources().source(DamageTypes.FALLING_ANVIL),
					level.damageSources().source(DamageTypes.FALLING_BLOCK),
			};
			for (DamageSource source : world) {
				helper.assertFalse(survivor.hurtServer(level, source, 4f), "a survivor was hurt by " + source.getMsgId());
				helper.assertTrue(survivor.getHealth() == full, "survivor health fell to " + survivor.getHealth() + " from " + source.getMsgId());
			}
			Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 2.5));
			helper.assertTrue(survivor.hurtServer(level, level.damageSources().mobAttack(zombie), 2f), "a zombie's hit must land");
			helper.assertTrue(survivor.getHealth() < full, "a zombie's hit did no damage");
			zombie.discard();
			survivor.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			helper.assertTrue(survivor.isDeadOrDying(), "/kill damage did not kill a survivor");
		} finally {
			survivor.discard();
		}
		helper.succeed();
	}
}
